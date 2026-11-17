package com.nightwise.motionx.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.nightwise.motionx.ui.theme.MX
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Animated flat line-art track used as hero art for Neon Dash. */
@Composable
fun TrackArt(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "track")
    val s by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "s")
    val hop by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing)), label = "hop")
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val hy = h * 0.30f
        val vx = w / 2
        fun persp(x: Float, depth: Float): Offset { // depth 0 = horizon, 1 = bottom
            val y = hy + (h - hy) * depth
            return Offset(vx + (x - vx) * depth, y)
        }
        // horizon + scrolling floor ticks
        drawLine(MX.Stroke, Offset(0f, hy), Offset(w, hy), 2f)
        for (j in 0 until 8) {
            val d = ((j + s) / 8f).let { it * it }
            val y = hy + (h - hy) * d
            drawLine(Color.White.copy(alpha = 0.03f + 0.07f * d), Offset(0f, y), Offset(w, y), 1.5f)
        }
        // track edges + lane dashes
        val tw = w * 0.34f
        drawLine(MX.TextLow, Offset(vx, hy), Offset(vx - tw, h), 3f)
        drawLine(MX.TextLow, Offset(vx, hy), Offset(vx + tw, h), 3f)
        val dash = PathEffect.dashPathEffect(floatArrayOf(18f, 16f), -s * 34f)
        drawLine(MX.Cyan, Offset(vx, hy), Offset(vx - tw / 3, h), 2.5f, pathEffect = dash)
        drawLine(MX.Cyan, Offset(vx, hy), Offset(vx + tw / 3, h), 2.5f, pathEffect = dash)
        // obstacles approaching
        val od = ((s + 0.3f) % 1f).let { it * it }
        val op = persp(vx + tw * 0.66f, od)
        drawRoundRect(MX.Amber, Offset(op.x - 30f * od, op.y - 10f * od), Size(60f * od, 10f * od), CornerRadius(3f))
        val od2 = ((s + 0.8f) % 1f).let { it * it }
        val op2 = persp(vx - tw * 0.66f, od2)
        drawRoundRect(MX.Coral, Offset(op2.x - 26f * od2, op2.y - 56f * od2), Size(52f * od2, 56f * od2), CornerRadius(4f))
        // runner (stick figure from behind)
        val lane = when { hop < 0.33f -> 0f; hop < 0.66f -> 1f; else -> -1f }
        val rx = vx + lane * tw * 0.66f * 0.82f
        val jump = if (hop in 0.15f..0.25f) sin((hop - 0.15f) / 0.1f * PI.toFloat()) * h * 0.08f else 0f
        drawSkeleton(Offset(rx, h * 0.93f - jump), h * 0.36f, runPhase = s * 2 * PI.toFloat() * 2, color = Color.White)
    }
}

/**
 * Draws a simple neon stick figure with feet at [feet] and total height [height].
 * [squat] 0..1 bends the knees, [runPhase] swings limbs.
 */
fun DrawScope.drawSkeleton(
    feet: Offset,
    height: Float,
    squat: Float = 0f,
    runPhase: Float? = null,
    /** 0 = arms down, 1 = both hands raised above the head. */
    armsUp: Float = 0f,
    color: Color = Color.White,
    stroke: Float = height * 0.035f,
) {
    val legLen = height * 0.46f
    val kneeBend = squat * 0.5f
    val hipY = feet.y - legLen * (1f - kneeBend * 0.9f)
    val hipW = height * 0.07f
    val shY = hipY - height * 0.30f
    val shW = height * 0.11f
    val headR = height * 0.07f
    val headC = Offset(feet.x, shY - headR * 1.6f)
    val swing = runPhase?.let { sin(it) } ?: 0f
    fun bone(a: Offset, b: Offset) {
        drawLine(color, a, b, stroke, StrokeCap.Round)
    }
    val lHip = Offset(feet.x - hipW, hipY)
    val rHip = Offset(feet.x + hipW, hipY)
    val lSh = Offset(feet.x - shW, shY)
    val rSh = Offset(feet.x + shW, shY)
    bone(lSh, rSh); bone(lHip, rHip); bone(lSh, lHip); bone(rSh, rHip)
    // legs
    for (side in intArrayOf(-1, 1)) {
        val hip = if (side < 0) lHip else rHip
        val footX = feet.x + side * hipW * 1.1f + swing * side * height * 0.05f
        val knee = Offset(hip.x + side * height * 0.06f * kneeBend * 2 + swing * side * height * 0.03f, (hip.y + feet.y) / 2 - abs(swing) * height * 0.02f)
        val foot = Offset(footX, feet.y - (if (swing * side > 0) abs(swing) * height * 0.04f else 0f))
        bone(hip, knee); bone(knee, foot)
        drawCircle(color, stroke * 1.1f, knee)
        drawCircle(color, stroke * 1.1f, foot)
    }
    // arms
    for (side in intArrayOf(-1, 1)) {
        val sh = if (side < 0) lSh else rSh
        val downElbow = Offset(sh.x + side * height * 0.04f, sh.y + height * 0.13f - swing * side * height * 0.03f)
        val downHand = Offset(sh.x + side * height * 0.05f + swing * -side * height * 0.03f, sh.y + height * 0.26f - swing * side * height * 0.06f)
        val upElbow = Offset(sh.x + side * height * 0.07f, sh.y - height * 0.11f)
        val upHand = Offset(sh.x + side * height * 0.09f, sh.y - height * 0.24f)
        fun lerp(a: Offset, b: Offset) = Offset(a.x + (b.x - a.x) * armsUp, a.y + (b.y - a.y) * armsUp)
        val elbow = lerp(downElbow, upElbow)
        val hand = lerp(downHand, upHand)
        bone(sh, elbow); bone(elbow, hand)
        drawCircle(color, stroke * 1.1f, elbow)
    }
    drawCircle(color, headR, headC, style = Stroke(stroke))
    for (p in listOf(lSh, rSh, lHip, rHip)) drawCircle(color, stroke * 1.2f, p)
}
