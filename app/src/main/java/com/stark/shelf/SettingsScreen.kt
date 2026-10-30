package com.stark.shelf

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun openHomeSettings(ctx: Context) {
    try {
        ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
    } catch (e: Exception) {
        ctx.startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@Composable
fun SettingsScreen(vm: ShelfVM, onBack: () -> Unit) {
    val all by vm.all.collectAsState()
    val enabled by vm.enabled.collectAsState()
    val sound by vm.sound.collectAsState()
    val en = enabled
    val ctx = LocalContext.current
    val onCount = if (en == null) all.size else all.count { it.pkg in en }
    val colors = SwitchDefaults.colors(
        checkedThumbColor = C.bg, checkedTrackColor = C.cy,
        uncheckedThumbColor = C.dim, uncheckedTrackColor = C.bg, uncheckedBorderColor = C.dim
    )

    Column(Modifier.fillMaxSize().background(C.bg).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Tag("BACK", onClick = onBack)
            Spacer(Modifier.width(12.dp))
            Text("SHELF SETTINGS", color = C.cy, fontFamily = Mono, fontSize = 13.sp, letterSpacing = 2.sp)
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            item {
                Tag("SET AS HOME APP", filled = true, modifier = Modifier.fillMaxWidth()) { openHomeSettings(ctx) }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("SOUND", color = C.tx, fontFamily = Mono, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Switch(checked = sound, onCheckedChange = { vm.setSound(it) }, colors = colors)
                }
                Spacer(Modifier.height(16.dp))
                Text("APPS ON SHELF  $onCount / ${all.size}", color = C.am, fontFamily = Mono, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tag("SELECT ALL") { vm.setAll(true) }
                    Tag("CLEAR") { vm.setAll(false) }
                }
                Spacer(Modifier.height(8.dp))
            }
            items(all, key = { it.pkg }) { app ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp).border(1.dp, C.line).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(app.icon, null, Modifier.size(36.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.label, color = C.tx, fontSize = 15.sp, maxLines = 1)
                        Text(app.pkg, color = C.dim, fontFamily = Mono, fontSize = 10.sp, maxLines = 1)
                    }
                    Switch(
                        checked = en == null || app.pkg in en,
                        onCheckedChange = { vm.toggle(app.pkg, it) },
                        colors = colors
                    )
                }
            }
        }
    }
}
