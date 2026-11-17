package com.nightwise.motionx.games.pose

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.nightwise.motionx.tracking.Lm
import com.nightwise.motionx.tracking.PoseFrame
import com.nightwise.motionx.ui.theme.MX
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val FIGURE_BONES = listOf(
    Joint.SL_SHOULDER to Joint.SR_SHOULDER, Joint.SL_HIP to Joint.SR_HIP,
    Joint.SL_SHOULDER to Joint.SL_HIP, Joint.SR_SHOULDER to Joint.SR_HIP,
)
private val LIMBS = listOf(
    Triple(Bone.SL_UPPER_ARM, Joint.SL_SHOULDER, Joint.SL_ELBOW), Triple(Bone.SL_FOREARM, Joint.SL_ELBOW, Joint.SL_WRIST),
    Triple(Bone.SR_UPPER_ARM, Joint.SR_SHOULDER, Joint.SR_ELBOW), Triple(Bone.SR_FOREARM, Joint.SR_ELBOW, Joint.SR_WRIST),
    Triple(Bone.SL_THIGH, Joint.SL_HIP, Joint.SL_KNEE), Triple(Bone.SL_SHIN, Joint.SL_KNEE, Joint.SL_ANKLE),
    Triple(Bone.SR_THIGH, Joint.SR_HIP, Joint.SR_KNEE), Triple(Bone.SR_SHIN, Joint.SR_KNEE, Joint.SR_ANKLE),
)

/**
 * Neon mannequin showing the target pose. Limbs the pose doesn't care about are drawn faint.
 * [progress] (0..1) fills the figure with [fillColor] as the player holds the pose.
 */
@Composable
fun PoseMannequin(
    pose: PoseDef,
    modifier: Modifier = Modifier,
    color: Color = MX.Cyan,
    progress: Float = 0f,
    fillColor: Color = MX.Lime,
    animate: Boolean = true,
) {
    val fig = remember(pose) { PoseFigure.build(pose) }
    val breathe = if (animate) {
        val t = rememberInfiniteTransition(label = "breathe")
        val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "b")
        v
    } else 0f
    Canvas(modifier) {
        drawMannequin(fig, color, progress, fillColor, breathe)
    }
}

fun DrawScope.drawMannequin(fig: PoseFigure, color: Color, progress: Float, fillColor: Color, breathe: Float) {
    // bounds incl. head
    var minX = fig.headCenter[0] - fig.headR
    var maxX = fig.headCenter[0] + fig.headR
    var minY = fig.headCenter[1] - fig.headR
    var maxY = fig.headCenter[1] + fig.headR
    for (p in fig.joints.values) {
        minX = min(minX, p[0]); maxX = max(maxX, p[0]); minY = min(minY, p[1]); maxY = max(maxY, p[1])
    }
    val pad = 0.18f
    val w = (maxX - minX) + pad * 2
    val h = (maxY - minY) + pad * 2
    val s = min(size.width / w, size.height / h) * (0.97f + 0.03f * breathe)
    val ox = (size.width - (maxX - minX) * s) / 2 - minX * s
    val oy = (size.height - (maxY - minY) * s) / 2 - minY * s
    fun P(j: Joint): Offset = fig.joints.getValue(j).let { Offset(ox + it[0] * s, oy + it[1] * s) }

    // floor glow under the lowest point
    val floorY = oy + maxY * s + 6f
    drawOval(
        Brush.radialGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent), Offset(size.width / 2, floorY), size.width * 0.3f),
        Offset(size.width * 0.2f, floorY - 10f), Size(size.width * 0.6f, 20f),
    )

    val stroke = s * 0.11f
    val c = lerpColor(color, fillColor, progress)
    fun limb(a: Offset, b: Offset, col: Color, faint: Boolean) {
        val alpha = if (faint) 0.25f else 1f
        drawLine(col.copy(alpha = 0.22f * alpha), a, b, stroke * 2.4f, StrokeCap.Round)
        drawLine(col.copy(alpha = alpha), a, b, stroke, StrokeCap.Round)
    }
    // torso as a filled tapered quad
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(P(Joint.SL_SHOULDER).x, P(Joint.SL_SHOULDER).y)
        lineTo(P(Joint.SR_SHOULDER).x, P(Joint.SR_SHOULDER).y)
        lineTo(P(Joint.SR_HIP).x, P(Joint.SR_HIP).y)
        lineTo(P(Joint.SL_HIP).x, P(Joint.SL_HIP).y)
        close()
    }
    drawPath(path, c.copy(alpha = 0.35f))
    FIGURE_BONES.forEach { (a, b) -> limb(P(a), P(b), c, Bone.TORSO in fig.free) }
    LIMBS.forEach { (bone, a, b) -> limb(P(a), P(b), c, bone in fig.free) }
    for (j in listOf(Joint.SL_ELBOW, Joint.SR_ELBOW, Joint.SL_KNEE, Joint.SR_KNEE, Joint.SL_WRIST, Joint.SR_WRIST, Joint.SL_ANKLE, Joint.SR_ANKLE)) {
        drawCircle(Color.White, stroke * 0.55f, P(j))
    }
    val head = Offset(ox + fig.headCenter[0] * s, oy + fig.headCenter[1] * s)
    drawCircle(c.copy(alpha = 0.25f), fig.headR * s * 1.35f, head)
    drawCircle(c, fig.headR * s, head)
    drawCircle(Color.White.copy(alpha = 0.85f), fig.headR * s, head, style = Stroke(stroke * 0.35f))
}

private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val k = t.coerceIn(0f, 1f)
    return Color(a.red + (b.red - a.red) * k, a.green + (b.green - a.green) * k, a.blue + (b.blue - a.blue) * k, 1f)
}

/**
 * Live camera frame (letterboxed, whole frame visible) with the player's skeleton coloured per limb:
 * green = matches the target, amber = close, red = adjust.
 */
@Composable
fun PoseCameraView(frame: PoseFrame?, result: PoseMatcher.Result?, modifier: Modifier = Modifier, fill: Boolean = false) {
    Canvas(modifier.clipToBounds()) {
        drawRect(Color(0xFF05070F))
        if (frame == null) return@Canvas
        val iw = frame.width.toFloat()
        val ih = frame.height.toFloat()
        val scale = if (fill) max(size.width / iw, size.height / ih) else min(size.width / iw, size.height / ih)
        val dw = iw * scale
        val dh = ih * scale
        // In fill mode keep the player's hips centred horizontally.
        val lmh = frame.landmarks
        val focusX = if (fill && lmh != null && lmh[Lm.L_HIP * 4 + 3] > 0.3f) (lmh[Lm.L_HIP * 4] + lmh[Lm.R_HIP * 4]) / 2 else 0.5f
        val ox = if (dw > size.width) (size.width / 2 - focusX * dw).coerceIn(size.width - dw, 0f) else (size.width - dw) / 2
        val oy = (size.height - dh) / 2
        drawImage(
            frame.bitmap.asImageBitmap(), IntOffset.Zero, IntSize(frame.width, frame.height),
            IntOffset(ox.roundToInt(), oy.roundToInt()), IntSize(dw.roundToInt(), dh.roundToInt()),
        )
        drawRect(Color.Black.copy(alpha = 0.3f), Offset(ox, oy), Size(dw, dh))
        val lm = frame.landmarks ?: return@Canvas
        fun px(i: Int) = Offset(ox + lm[i * 4] * dw, oy + lm[i * 4 + 1] * dh)
        fun vis(i: Int) = lm[i * 4 + 3] > 0.3f
        val stroke = (dh * 0.012f).coerceIn(4f, 12f)
        fun line(a: Offset, b: Offset, col: Color) {
            drawLine(col.copy(alpha = 0.3f), a, b, stroke * 2.6f, StrokeCap.Round)
            drawLine(col, a, b, stroke, StrokeCap.Round)
        }
        fun statusColor(st: Int) = when (st) {
            2 -> MX.Lime
            1 -> MX.Amber
            0 -> MX.Coral
            else -> Color.White.copy(alpha = 0.7f)
        }
        // torso frame
        val torsoCol = statusColor(result?.status(Bone.TORSO) ?: -1)
        for ((a, b) in listOf(Lm.L_SHOULDER to Lm.R_SHOULDER, Lm.L_HIP to Lm.R_HIP, Lm.L_SHOULDER to Lm.L_HIP, Lm.R_SHOULDER to Lm.R_HIP)) {
            if (vis(a) && vis(b)) line(px(a), px(b), torsoCol)
        }
        if (result != null) {
            for (b in Bone.entries) {
                if (b == Bone.TORSO) continue
                val pr = result.boneLandmarks[b.ordinal]
                if (!vis(pr[0]) || !vis(pr[1])) continue
                line(px(pr[0]), px(pr[1]), statusColor(result.status(b)))
            }
        } else {
            for (bn in Lm.BONES) if (vis(bn[0]) && vis(bn[1])) line(px(bn[0]), px(bn[1]), Color.White)
        }
        for (j in Lm.JOINTS) if (vis(j)) drawCircle(Color.White, stroke * 0.8f, px(j))
        if (vis(Lm.NOSE)) {
            val sw = kotlin.math.abs(lm[Lm.L_SHOULDER * 4] - lm[Lm.R_SHOULDER * 4]) * dw
            drawCircle(torsoCol, (sw * 0.35f).coerceIn(14f, 70f), px(Lm.NOSE), style = Stroke(stroke))
        }
    }
}
