package com.stark.shelf

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Mono = FontFamily.Monospace

/** Corner brackets (top-left and bottom-right) */
fun Modifier.brackets(color: Color, len: Dp = 14.dp, w: Dp = 2.dp) = drawBehind {
    val l = len.toPx()
    val s = w.toPx()
    val W = size.width
    val H = size.height
    drawLine(color, Offset(0f, 0f), Offset(l, 0f), s)
    drawLine(color, Offset(0f, 0f), Offset(0f, l), s)
    drawLine(color, Offset(W, H), Offset(W - l, H), s)
    drawLine(color, Offset(W, H), Offset(W, H - l), s)
}

@Composable
fun Hud(color: Color, modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "hud")
    val a by t.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing)), label = "a"
    )
    val b by t.animateFloat(
        initialValue = 360f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(11000, easing = LinearEasing)), label = "b"
    )
    Canvas(modifier) {
        val r = size.minDimension / 2
        drawCircle(color.copy(alpha = 0.25f), r - 2f, style = Stroke(2f))
        rotate(a) {
            drawCircle(
                color, r * 0.9f,
                style = Stroke(4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(120f, 80f, 16f, 80f)))
            )
        }
        rotate(b) {
            drawCircle(
                color.copy(alpha = 0.6f), r * 0.75f,
                style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 14f)))
            )
        }
    }
}

@Composable
fun Tag(text: String, filled: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .defaultMinSize(minHeight = 40.dp)
            .background(if (filled) C.cy else Color.Transparent)
            .border(1.dp, if (filled) C.cy else C.line)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (filled) C.bg else C.cy, fontFamily = Mono, fontSize = 12.sp, letterSpacing = 1.5.sp)
    }
}
