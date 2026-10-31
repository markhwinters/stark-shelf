package com.stark.shelf

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AppItem(
    val label: String,
    val pkg: String,
    val icon: ImageBitmap,
    val version: String,
    /** INSTALLED, UPDATED or BUILT-IN */
    val dateLabel: String,
    val date: String,
)

val Context.ds by preferencesDataStore("shelf")

object Keys {
    val enabled = stringSetPreferencesKey("enabled")
    val sound = booleanPreferencesKey("sound")
    val usage = stringPreferencesKey("usage")
}

fun parseUsage(s: String?): Map<String, Int> =
    if (s.isNullOrEmpty()) emptyMap()
    else s.split(";").mapNotNull {
        val i = it.lastIndexOf('=')
        if (i < 0) null else it.substring(0, i) to (it.substring(i + 1).toIntOrNull() ?: 0)
    }.toMap()

fun loadApps(ctx: Context): List<AppItem> {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val minValid = 946684800000L // 2000-01-01; preinstalled apps often report 1970
    return pm.queryIntentActivities(intent, 0)
        .filter { it.activityInfo.packageName != ctx.packageName }
        .distinctBy { it.activityInfo.packageName }
        .mapNotNull { ri ->
            try {
                val pkg = ri.activityInfo.packageName
                val info = pm.getPackageInfo(pkg, 0)
                val first = info.firstInstallTime
                val upd = info.lastUpdateTime
                val (dl, d) = when {
                    first > minValid -> "INSTALLED" to fmt.format(Date(first))
                    upd > minValid -> "UPDATED" to fmt.format(Date(upd))
                    else -> "BUILT-IN" to "FACTORY"
                }
                AppItem(
                    label = ri.loadLabel(pm).toString(),
                    pkg = pkg,
                    icon = ri.loadIcon(pm).toBitmap(128, 128).asImageBitmap(),
                    version = info.versionName ?: "-",
                    dateLabel = dl,
                    date = d,
                )
            } catch (e: Exception) {
                null
            }
        }
        .sortedBy { it.label.lowercase() }
}

class ShelfVM(app: Application) : AndroidViewModel(app) {
    private val store = app.ds
    private val _all = MutableStateFlow<List<AppItem>>(emptyList())
    val all = _all.asStateFlow()

    /** null = nothing chosen yet, so show every app */
    val enabled = store.data.map { it[Keys.enabled] }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val sound = store.data.map { it[Keys.sound] ?: true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val usage = store.data.map { parseUsage(it[Keys.usage]) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val shown = combine(_all, enabled) { a, e -> if (e == null) a else a.filter { it.pkg in e } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val rx = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) = reload()
    }

    fun reload() {
        viewModelScope.launch(Dispatchers.IO) { _all.value = loadApps(getApplication()) }
    }

    fun recordLaunch(pkg: String) {
        viewModelScope.launch {
            store.edit { p ->
                val m = parseUsage(p[Keys.usage]).toMutableMap()
                m[pkg] = (m[pkg] ?: 0) + 1
                p[Keys.usage] = m.entries.joinToString(";") { "${it.key}=${it.value}" }
            }
        }
    }

    fun toggle(pkg: String, on: Boolean) {
        viewModelScope.launch {
            store.edit { p ->
                val cur = p[Keys.enabled] ?: _all.value.map { it.pkg }.toSet()
                p[Keys.enabled] = if (on) cur + pkg else cur - pkg
            }
        }
    }

    fun setAll(on: Boolean) {
        viewModelScope.launch {
            store.edit { p -> p[Keys.enabled] = if (on) _all.value.map { it.pkg }.toSet() else emptySet() }
        }
    }

    fun setSound(on: Boolean) {
        viewModelScope.launch { store.edit { it[Keys.sound] = on } }
    }

    init {
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(app, rx, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        reload()
    }

    override fun onCleared() {
        getApplication<Application>().unregisterReceiver(rx)
    }
}
