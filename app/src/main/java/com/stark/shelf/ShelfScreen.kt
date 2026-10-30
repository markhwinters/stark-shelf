package com.stark.shelf

import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.absoluteValue

@Composable
fun Clock() {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = LocalTime.now(); delay(1000) } }
    Text(now.format(DateTimeFormatter.ofPattern("HH:mm:ss")), color = C.dim, fontFamily = Mono, fontSize = 12.sp)
}

@Composable
fun AppCard(app: AppItem, modifier: Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .height(340.dp)
            .background(Brush.verticalGradient(listOf(C.panel, Color(0xFF060D13))))
            .border(1.dp, C.line)
            .brackets(C.cy),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(160.dp), contentAlignment = Alignment.Center) {
            Hud(C.cy, Modifier.fillMaxSize())
            Image(app.icon, null, Modifier.size(72.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            app.label, color = C.tx, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(horizontal = 12.dp)
        )
        Spacer(Modifier.height(6.dp))
        Text("${app.installed.take(4)} \u00B7 READY", color = C.am, fontFamily = Mono, fontSize = 11.sp)
    }
}

@Composable
fun Meta(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(C.bg).padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(label, color = C.dim, fontFamily = Mono, fontSize = 10.sp)
        Text(value, color = C.tx, fontFamily = Mono, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
fun ShelfScreen(vm: ShelfVM, onSettings: () -> Unit) {
    val apps by vm.shown.collectAsState()
    val soundOn by vm.sound.collectAsState()
    val sound by rememberUpdatedState(soundOn)
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    val list = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState { list.size }
    val focus = remember { FocusRequester() }
    val tone = remember { try { ToneGenerator(AudioManager.STREAM_SYSTEM, 40) } catch (e: Exception) { null } }
    DisposableEffect(Unit) { onDispose { tone?.release() } }

    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.drop(1).collect {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            if (sound) tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 25)
        }
    }
    LaunchedEffect(searching) { if (searching) focus.requestFocus() }

    fun launch(pkg: String) {
        ctx.packageManager.getLaunchIntentForPackage(pkg)?.let { ctx.startActivity(it) }
    }

    Column(Modifier.fillMaxSize().background(C.bg).systemBarsPadding().padding(vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("STARK SHELF", color = C.cy, fontFamily = Mono, fontSize = 13.sp, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
            Clock()
            Spacer(Modifier.width(10.dp))
            Tag("SEARCH") { searching = !searching; if (!searching) query = "" }
            Spacer(Modifier.width(8.dp))
            Tag("SET") { onSettings() }
        }
        if (searching) {
            BasicTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                textStyle = TextStyle(color = C.tx, fontFamily = Mono, fontSize = 14.sp),
                cursorBrush = SolidColor(C.cy),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
                    .border(1.dp, C.cy).background(C.panel).padding(12.dp).focusRequester(focus)
            )
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            val n = if (list.isEmpty()) 0 else pager.currentPage + 1
            Text("%02d / %02d".format(n, list.size), color = C.cy, fontFamily = Mono, fontSize = 15.sp)
        }

        if (list.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    if (apps.isEmpty()) "No apps on the shelf.\nOpen SET to choose some." else "No apps match.",
                    color = C.dim, fontFamily = Mono, fontSize = 13.sp, textAlign = TextAlign.Center
                )
            }
        } else {
            HorizontalPager(
                pager, Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 70.dp),
                pageSpacing = 12.dp,
                verticalAlignment = Alignment.CenterVertically,
                key = { list[it].pkg }
            ) { page ->
                AppCard(
                    list[page],
                    Modifier
                        .graphicsLayer {
                            val signed = (pager.currentPage - page) + pager.currentPageOffsetFraction
                            val off = signed.absoluteValue.coerceIn(0f, 1f)
                            val s = lerp(0.8f, 1f, 1f - off)
                            scaleX = s; scaleY = s
                            alpha = lerp(0.4f, 1f, 1f - off)
                            rotationY = signed * -25f
                            cameraDistance = 12f * density
                        }
                        .clickable {
                            if (page == pager.currentPage) launch(list[page].pkg)
                            else scope.launch { pager.animateScrollToPage(page) }
                        }
                )
            }
            val cur = list[pager.currentPage.coerceIn(0, list.lastIndex)]
            Column(Modifier.padding(horizontal = 16.dp)) {
                Column(Modifier.fillMaxWidth().border(1.dp, C.line).background(C.line)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                        Meta("PACKAGE", cur.pkg, Modifier.weight(1f))
                        Meta("VERSION", cur.version, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(1.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                        Meta("INSTALLED", cur.installed, Modifier.weight(1f))
                        Meta("STATUS", "READY", Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tag("INFO", modifier = Modifier.weight(1f)) {
                        ctx.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${cur.pkg}"))
                        )
                    }
                    Tag("LAUNCH", filled = true, modifier = Modifier.weight(2f)) { launch(cur.pkg) }
                }
            }
        }
    }
}
