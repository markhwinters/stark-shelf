package com.stark.shelf

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.provider.Settings
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Outer ring: star (most used), A-Z, and # for everything else. */
val LETTERS: List<String> = listOf("\u2605") + ('A'..'Z').map { it.toString() } + listOf("#")
const val STAR = "\u2605"
const val PAGE = 11
const val DEFAULT_STATUS = "ALL SYSTEMS NOMINAL"

object Boot { var shown = false }

class HudState {
    var letter by mutableStateOf(STAR)
    var query by mutableStateOf("")
    var page by mutableIntStateOf(0)
    var focus by mutableIntStateOf(-1)
    var status by mutableStateOf(DEFAULT_STATUS)
    /** 0 none, 1 letter ring, 2 app ring, 3 centre (cancel) */
    var zone = 0
}

/** more: 0 = no paging, >0 = apps remaining, -1 = last page (wraps to start) */
class Nodes(val items: List<AppItem>, val more: Int, val total: Int) {
    val count: Int get() = items.size + if (more != 0) 1 else 0
}

fun buildNodes(apps: List<AppItem>, usage: Map<String, Int>, letter: String, query: String, page: Int): Nodes {
    val q = query.trim()
    fun first(a: AppItem): Char? = a.label.firstOrNull()?.uppercaseChar()
    val list: List<AppItem> = when {
        q.isNotEmpty() -> apps.filter { it.label.contains(q, true) || it.pkg.contains(q, true) }
            .sortedWith(compareBy({ !it.label.startsWith(q, true) }, { it.label.lowercase() }))
        letter == STAR -> apps.sortedWith(compareBy({ -(usage[it.pkg] ?: 0) }, { it.label.lowercase() }))
        letter == "#" -> apps.filter { val c = first(it); c == null || c !in 'A'..'Z' }
        else -> apps.filter { first(it) == letter[0] }
    }
    if (list.size <= PAGE + 1) return Nodes(list, 0, list.size)
    val pages = (list.size + PAGE - 1) / PAGE
    val from = (page % pages) * PAGE
    val items = list.subList(from, minOf(from + PAGE, list.size))
    val remaining = list.size - (from + items.size)
    return Nodes(items, if (remaining > 0) remaining else -1, list.size)
}

fun handleTouch(p: Offset, size: IntSize, hud: HudState, nodes: Nodes, onLetter: () -> Unit, onFocus: () -> Unit) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val r = size.width / 2f
    val v = p - c
    val d = v.getDistance()
    val norm = (((Math.toDegrees(atan2(v.y, v.x).toDouble()) + 90.0) % 360.0 + 360.0) % 360.0).toFloat()
    when {
        d >= 0.70f * r -> {
            val step = 360f / LETTERS.size
            val l = LETTERS[(norm / step).roundToInt() % LETTERS.size]
            hud.zone = 1
            if (l != hud.letter || hud.query.isNotEmpty()) {
                hud.letter = l; hud.query = ""; hud.page = 0; hud.focus = -1
                onLetter()
            }
        }
        d >= 0.30f * r -> {
            hud.zone = 2
            val n = nodes.count
            if (n > 0) {
                val idx = (norm / (360f / n)).roundToInt() % n
                if (idx != hud.focus) { hud.focus = idx; onFocus() }
            }
        }
        else -> { hud.zone = 3; hud.focus = -1 }
    }
}

fun matchSpoken(said: String, apps: List<AppItem>): AppItem? {
    val s = said.lowercase().trim()
        .replace(Regex("^(open|launch|start|run|show me|show|go to)\\s+"), "").trim()
    if (s.isEmpty()) return null
    return apps.firstOrNull { it.label.lowercase() == s }
        ?: apps.firstOrNull { it.label.lowercase().startsWith(s) }
        ?: apps.firstOrNull { it.label.lowercase().contains(s) }
        ?: apps.firstOrNull { it.label.length >= 3 && s.contains(it.label.lowercase()) }
}

fun battery(ctx: Context): Int = try {
    (ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
        .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
} catch (e: Exception) { -1 }

@Composable
fun Meta(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(C.bg).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Text(label, color = C.dim, fontFamily = Mono, fontSize = 9.sp)
        Text(value, color = C.tx, fontFamily = Mono, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
fun AppNode(app: AppItem, focused: Boolean, size: Dp, modifier: Modifier) {
    val sc by animateFloatAsState(if (focused) 1.4f else 1f, label = "sc")
    Image(
        app.icon, app.label,
        modifier.size(size).graphicsLayer { scaleX = sc; scaleY = sc; alpha = if (focused) 1f else 0.85f }
    )
}

@Composable
fun MoreNode(more: Int, focused: Boolean, modifier: Modifier) {
    Box(
        modifier.size(38.dp).border(1.5.dp, if (focused) C.am else C.line, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(if (more > 0) "+$more" else "\u21BA", color = if (focused) C.am else C.cy, fontFamily = Mono, fontSize = 11.sp)
    }
}

@Composable
fun Dial(
    hud: HudState,
    nodes: Nodes,
    onTouchStart: () -> Unit,
    onLetter: () -> Unit,
    onFocus: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier,
) {
    val latest by rememberUpdatedState(nodes)
    val startCb by rememberUpdatedState(onTouchStart)
    val letterCb by rememberUpdatedState(onLetter)
    val focusCb by rememberUpdatedState(onFocus)
    val releaseCb by rememberUpdatedState(onRelease)

    val t = rememberInfiniteTransition(label = "dial")
    val a1 by t.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(40000, easing = LinearEasing)), label = "a1"
    )
    val a2 by t.animateFloat(
        initialValue = 360f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(22000, easing = LinearEasing)), label = "a2"
    )
    val sweep by t.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "sw"
    )

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val s = if (maxWidth < maxHeight) maxWidth else maxHeight
        val r = s.value / 2f
        Box(
            Modifier.size(s).pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    startCb()
                    handleTouch(down.position, size, hud, latest, letterCb, focusCb)
                    down.consume()
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.first()
                        if (!ch.pressed) { ch.consume(); releaseCb(); break }
                        handleTouch(ch.position, size, hud, latest, letterCb, focusCb)
                        ch.consume()
                    }
                }
            },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val c = center
                val rr = size.minDimension / 2f
                val cy = C.cy
                drawCircle(Brush.radialGradient(listOf(cy.copy(alpha = 0.10f), Color.Transparent), center = c, radius = rr), rr, c)
                drawCircle(cy.copy(alpha = 0.55f), rr * 0.99f, c, style = Stroke(2f))
                drawCircle(cy.copy(alpha = 0.25f), rr * 0.70f, c, style = Stroke(1.5f))
                // active letter wedge
                val li = LETTERS.indexOf(hud.letter)
                if (hud.query.isEmpty() && li >= 0) {
                    val step = 360f / LETTERS.size
                    drawArc(
                        cy.copy(alpha = 0.28f), -90f + li * step - step / 2f, step, false,
                        topLeft = Offset(c.x - rr * 0.855f, c.y - rr * 0.855f),
                        size = Size(rr * 1.71f, rr * 1.71f),
                        style = Stroke(rr * 0.29f)
                    )
                }
                // rotating tick ring
                rotate(a1, c) {
                    for (i in 0 until 120) {
                        val th = Math.toRadians(i * 3.0)
                        val big = i % 5 == 0
                        val r0 = rr * 0.93f
                        val r1 = r0 + rr * (if (big) 0.05f else 0.025f)
                        drawLine(
                            cy.copy(alpha = if (big) 0.7f else 0.3f),
                            Offset(c.x + (r0 * cos(th)).toFloat(), c.y + (r0 * sin(th)).toFloat()),
                            Offset(c.x + (r1 * cos(th)).toFloat(), c.y + (r1 * sin(th)).toFloat()),
                            2f
                        )
                    }
                }
                // radar sweep
                rotate(sweep, c) {
                    drawCircle(
                        Brush.sweepGradient(0f to Color.Transparent, 0.75f to Color.Transparent, 1f to cy.copy(alpha = 0.22f), center = c),
                        rr * 0.70f, c
                    )
                }
                // app ring track and rotating arcs
                drawCircle(
                    cy.copy(alpha = 0.22f), rr * 0.50f, c,
                    style = Stroke(1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 10f)))
                )
                rotate(a2, c) {
                    drawArc(cy.copy(alpha = 0.5f), 0f, 70f, false, topLeft = Offset(c.x - rr * 0.62f, c.y - rr * 0.62f), size = Size(rr * 1.24f, rr * 1.24f), style = Stroke(3f))
                    drawArc(cy.copy(alpha = 0.3f), 180f, 40f, false, topLeft = Offset(c.x - rr * 0.62f, c.y - rr * 0.62f), size = Size(rr * 1.24f, rr * 1.24f), style = Stroke(3f))
                }
                rotate(a1 * 2f, c) {
                    drawArc(C.am.copy(alpha = 0.7f), 200f, 30f, false, topLeft = Offset(c.x - rr * 0.35f, c.y - rr * 0.35f), size = Size(rr * 0.70f, rr * 0.70f), style = Stroke(3f))
                }
                // centre reticle
                drawCircle(C.panel.copy(alpha = 0.85f), rr * 0.27f, c)
                drawCircle(cy.copy(alpha = 0.7f), rr * 0.27f, c, style = Stroke(2.5f))
                // lock-on
                val n = nodes.count
                val fi = hud.focus
                if (fi in 0 until n) {
                    val th = Math.toRadians(-90.0 + fi * 360.0 / n)
                    val dx = cos(th).toFloat()
                    val dy = sin(th).toFloat()
                    val p = Offset(c.x + rr * 0.50f * dx, c.y + rr * 0.50f * dy)
                    drawLine(C.am.copy(alpha = 0.8f), Offset(c.x + dx * rr * 0.27f, c.y + dy * rr * 0.27f), Offset(p.x - dx * rr * 0.16f, p.y - dy * rr * 0.16f), 2.5f)
                    drawCircle(C.am, rr * 0.16f, p, style = Stroke(3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))))
                }
            }
            // letters
            LETTERS.forEachIndexed { i, l ->
                val ang = Math.toRadians(-90.0 + i * 360.0 / LETTERS.size)
                val x = (r * 0.84f * cos(ang)).toFloat()
                val y = (r * 0.84f * sin(ang)).toFloat()
                val on = hud.query.isEmpty() && hud.letter == l
                Box(Modifier.size(26.dp).offset(x.dp, y.dp), contentAlignment = Alignment.Center) {
                    Text(
                        l, color = if (on) C.cy else C.dim, fontFamily = Mono,
                        fontSize = if (on) 15.sp else 11.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
            // apps
            val n = nodes.count
            for (i in 0 until n) {
                val ang = Math.toRadians(-90.0 + i * 360.0 / n)
                val x = (r * 0.50f * cos(ang)).toFloat()
                val y = (r * 0.50f * sin(ang)).toFloat()
                val m = Modifier.offset(x.dp, y.dp)
                if (i < nodes.items.size) AppNode(nodes.items[i], hud.focus == i, if (n > 9) 34.dp else 40.dp, m)
                else MoreNode(nodes.more, hud.focus == i, m)
            }
            // centre text
            val fa = nodes.items.getOrNull(hud.focus)
            Column(
                Modifier.size((r * 0.46f).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (fa != null) {
                    Text(fa.label, color = C.am, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
                } else if (hud.focus >= 0 && hud.focus == nodes.items.size && nodes.more != 0) {
                    Text("NEXT PAGE", color = C.am, fontFamily = Mono, fontSize = 11.sp, textAlign = TextAlign.Center)
                } else {
                    Text(if (hud.query.isNotEmpty()) "?" else hud.letter, color = C.cy, fontSize = 34.sp, fontWeight = FontWeight.Light)
                    Text("${nodes.total} APPS", color = C.dim, fontFamily = Mono, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
fun Header(status: String, onSettings: () -> Unit) {
    val ctx = LocalContext.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var bat by remember { mutableIntStateOf(-1) }
    LaunchedEffect(Unit) {
        while (true) { now = LocalDateTime.now(); bat = battery(ctx); delay(1000) }
    }
    val greet = when {
        now.hour < 12 -> "GOOD MORNING"
        now.hour < 18 -> "GOOD AFTERNOON"
        else -> "GOOD EVENING"
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(greet, color = C.cy, fontFamily = Mono, fontSize = 13.sp, letterSpacing = 2.sp)
            Text(status, color = if (status == DEFAULT_STATUS) C.dim else C.am, fontFamily = Mono, fontSize = 10.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(now.format(DateTimeFormatter.ofPattern("HH:mm:ss")), color = C.tx, fontFamily = Mono, fontSize = 16.sp)
            val d = now.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)).uppercase()
            Text(if (bat >= 0) "$d \u00B7 $bat%" else d, color = C.dim, fontFamily = Mono, fontSize = 10.sp)
        }
        Spacer(Modifier.width(10.dp))
        Tag("SET") { onSettings() }
    }
}

@Composable
fun TargetPanel(app: AppItem?, uses: Int, onInfo: () -> Unit, onLaunch: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(170.dp)
            .border(1.dp, C.line).background(C.panel.copy(alpha = 0.6f)).brackets(C.cy).padding(12.dp)
    ) {
        if (app == null) {
            Text(
                "SWEEP THE OUTER RING TO PICK A LETTER, SLIDE IN TO AN APP, RELEASE TO LAUNCH.\n\n" +
                    "\u2605 = MOST USED.  TYPE OR SAY \"OPEN <APP>\" FOR INSTANT ACCESS.",
                color = C.dim, fontFamily = Mono, fontSize = 11.sp, lineHeight = 16.sp
            )
        } else {
            Text(app.label, color = C.am, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(app.pkg, color = C.dim, fontFamily = Mono, fontSize = 10.sp, maxLines = 1)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                Meta("VERSION", app.version, Modifier.weight(1f))
                Meta(app.dateLabel, app.date, Modifier.weight(1f))
                Meta("LAUNCHES", uses.toString(), Modifier.weight(1f))
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tag("INFO", modifier = Modifier.weight(1f)) { onInfo() }
                Tag("LAUNCH", filled = true, modifier = Modifier.weight(2f)) { onLaunch() }
            }
        }
    }
}

@Composable
fun BootOverlay(lines: List<String>) {
    Box(Modifier.fillMaxSize().background(C.bg), contentAlignment = Alignment.CenterStart) {
        Column(Modifier.padding(28.dp)) {
            lines.forEach {
                Text(it, color = C.cy, fontFamily = Mono, fontSize = 12.sp, letterSpacing = 2.sp, modifier = Modifier.padding(vertical = 3.dp))
            }
        }
    }
}

@Composable
fun HudScreen(vm: ShelfVM, onSettings: () -> Unit) {
    val apps by vm.shown.collectAsState()
    val usage by vm.usage.collectAsState()
    val soundOn by vm.sound.collectAsState()
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val sfx = remember { Sfx() }
    DisposableEffect(Unit) { onDispose { sfx.release() } }
    val soundNow by rememberUpdatedState(soundOn)
    val hud = remember { HudState() }
    val nodes = remember(apps, usage, hud.letter, hud.query, hud.page) {
        buildNodes(apps, usage, hud.letter, hud.query, hud.page)
    }

    fun play(n: String) { if (soundNow) sfx.play(n) }
    fun buzz() { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }

    fun open(app: AppItem) {
        val i = ctx.packageManager.getLaunchIntentForPackage(app.pkg) ?: return
        play("launch")
        vm.recordLaunch(app.pkg)
        hud.query = ""; hud.focus = -1; hud.letter = STAR; hud.page = 0
        ctx.startActivity(i)
    }

    fun release() {
        if (hud.zone != 2) { if (hud.zone == 3) play("cancel"); return }
        val i = hud.focus
        val app = nodes.items.getOrNull(i)
        if (app != null) open(app)
        else if (i >= 0 && i == nodes.items.size && nodes.more != 0) {
            hud.page++; hud.focus = -1; play("lock"); buzz()
        }
    }

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val said = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (res.resultCode == Activity.RESULT_OK && said != null) {
            val m = matchSpoken(said, apps)
            if (m != null) {
                hud.query = m.label; hud.focus = 0
                hud.status = "OPENING ${m.label.uppercase()}"
                play("lock")
                scope.launch { delay(500); open(m) }
            } else {
                hud.status = "NO MATCH FOR \"${said.uppercase()}\""
                play("cancel")
            }
        }
    }

    fun startVoice() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say: open <app name>")
        }
        try { voice.launch(i) } catch (e: ActivityNotFoundException) {
            Toast.makeText(ctx, "Voice input is not available on this phone", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(hud.status) {
        if (hud.status != DEFAULT_STATUS) { delay(2500); hud.status = DEFAULT_STATUS }
    }

    var booting by remember { mutableStateOf(!Boot.shown) }
    var bootLines by remember { mutableStateOf(listOf<String>()) }
    LaunchedEffect(Unit) {
        if (booting) {
            play("boot")
            delay(300)
            val seq = listOf("STARK SHELF // ONLINE", "SCANNING PACKAGES", "${apps.size} APPS INDEXED", DEFAULT_STATUS)
            for (s in seq) { bootLines = bootLines + s; delay(260) }
            delay(250)
            Boot.shown = true
            booting = false
        }
    }

    val imeUp = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val focusApp = nodes.items.getOrNull(hud.focus)

    Box(
        Modifier.fillMaxSize().background(C.bg).drawBehind {
            val step = 40.dp.toPx()
            var x = 0f
            while (x < size.width) { drawLine(C.cy.copy(alpha = 0.04f), Offset(x, 0f), Offset(x, size.height), 1f); x += step }
            var y = 0f
            while (y < size.height) { drawLine(C.cy.copy(alpha = 0.04f), Offset(0f, y), Offset(size.width, y), 1f); y += step }
            drawRect(
                Brush.radialGradient(
                    listOf(Color(0x221E9BE8), Color.Transparent),
                    center = Offset(size.width / 2f, size.height * 0.4f), radius = size.width
                )
            )
        }
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(vertical = 10.dp)) {
            Header(hud.status, onSettings)
            Dial(
                hud, nodes,
                onTouchStart = { focusManager.clearFocus() },
                onLetter = { play("tick"); buzz() },
                onFocus = { play("lock"); buzz() },
                onRelease = { release() },
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            )
            if (!imeUp) {
                TargetPanel(
                    focusApp, focusApp?.let { usage[it.pkg] } ?: 0,
                    onInfo = {
                        focusApp?.let {
                            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${it.pkg}")))
                        }
                    },
                    onLaunch = { focusApp?.let { open(it) } }
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .border(1.dp, C.cy.copy(alpha = 0.5f)).background(C.panel),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("\u203A", color = C.cy, fontFamily = Mono, fontSize = 20.sp, modifier = Modifier.padding(start = 14.dp, end = 8.dp))
                BasicTextField(
                    value = hud.query,
                    onValueChange = {
                        hud.query = it; hud.page = 0
                        hud.focus = if (it.isBlank()) -1 else 0
                        play("tick")
                    },
                    singleLine = true,
                    textStyle = TextStyle(color = C.tx, fontFamily = Mono, fontSize = 14.sp),
                    cursorBrush = SolidColor(C.cy),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        nodes.items.getOrNull(maxOf(hud.focus, 0))?.let { open(it) }
                    }),
                    decorationBox = { inner ->
                        if (hud.query.isEmpty()) Text("TYPE OR SAY A COMMAND", color = C.dim, fontFamily = Mono, fontSize = 12.sp)
                        inner()
                    },
                    modifier = Modifier.weight(1f).padding(vertical = 14.dp)
                )
                Tag("MIC", modifier = Modifier.padding(4.dp)) { startVoice() }
            }
        }
        if (booting) BootOverlay(bootLines)
    }
}
