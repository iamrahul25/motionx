package com.nightwise.motionx.tracking

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.nightwise.motionx.ui.theme.MX
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Live tracking view: the analysed camera frame (letterboxed, never cropped so all three lanes are
 * visible) with lane zones, jump/duck lines and a neon skeleton drawn on top.
 */
@Composable
fun TrackingView(
    frame: PoseFrame?,
    body: BodyController.State,
    modifier: Modifier = Modifier,
    showLines: Boolean = true,
    /**
     * true: fill the view (full camera height, head to feet) and crop the sides, keeping the
     * calibrated centre lane in the middle. false: letterbox the whole frame.
     */
    fill: Boolean = true,
) {
    Canvas(modifier.clipToBounds()) {
        drawRect(Color(0xFF05070F))
        if (frame == null) return@Canvas
        val iw = frame.width.toFloat()
        val ih = frame.height.toFloat()
        val scale = if (fill) max(size.width / iw, size.height / ih) else min(size.width / iw, size.height / ih)
        val dw = iw * scale
        val dh = ih * scale
        val focusX = if (body.phase == BodyController.Phase.READY && !body.centerX.isNaN()) body.centerX else 0.5f
        val ox = if (dw > size.width) (size.width / 2 - focusX * dw).coerceIn(size.width - dw, 0f) else (size.width - dw) / 2
        val oy = (size.height - dh) / 2

        drawImage(
            frame.bitmap.asImageBitmap(),
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(frame.width, frame.height),
            dstOffset = IntOffset(ox.roundToInt(), oy.roundToInt()),
            dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
        )
        // Darken so overlays pop.
        drawRect(Color.Black.copy(alpha = 0.35f), Offset(ox, oy), Size(dw, dh))

        fun px(nx: Float) = ox + nx * dw
        fun py(ny: Float) = oy + ny * dh

        val ready = body.phase == BodyController.Phase.READY
        if (ready && showLines && !body.laneBoundLeft.isNaN()) {
            drawLanes(px(body.laneBoundLeft), px(body.laneBoundRight), oy, dh, ox, dw, body.lane)
            drawGuide(py(body.jumpLineY), ox, dw, MX.Amber, if (body.handsUp) "▲ JUMP  ✋✋ READY" else "▲ JUMP  (hands up + jump)", body.jumping || body.handsUp)
            drawGuide(py(body.duckLineY), ox, dw, MX.Sky, "▼ DUCK", body.ducking)
        } else if (!ready) {
            // Target box for calibration: stand in the middle.
            val bx = px(0.33f)
            val bw = dw * 0.34f
            val col = if (body.phase == BodyController.Phase.CALIBRATING) MX.Lime else Color.White.copy(alpha = 0.6f)
            drawRoundRect(
                col, Offset(bx, oy + dh * 0.04f), Size(bw, dh * 0.92f), CornerRadius(18f),
                style = Stroke(3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))),
            )
            if (body.phase == BodyController.Phase.CALIBRATING) {
                drawRoundRect(
                    MX.Lime.copy(alpha = 0.18f), Offset(bx, oy + dh * 0.04f + dh * 0.92f * (1 - body.calibration)),
                    Size(bw, dh * 0.92f * body.calibration), CornerRadius(18f),
                )
            }
        }

        val lm = frame.landmarks ?: return@Canvas
        val boneColor = when {
            body.ducking -> MX.Sky
            body.jumping -> MX.Amber
            ready -> MX.Cyan
            else -> Color.White
        }
        for (b in Lm.BONES) {
            val a = b[0]
            val c = b[1]
            if (lm[a * 4 + 3] < 0.4f || lm[c * 4 + 3] < 0.4f) continue
            val p0 = Offset(px(lm[a * 4]), py(lm[a * 4 + 1]))
            val p1 = Offset(px(lm[c * 4]), py(lm[c * 4 + 1]))
            drawLine(boneColor.copy(alpha = 0.25f), p0, p1, 14f, StrokeCap.Round)
            drawLine(boneColor, p0, p1, 5f, StrokeCap.Round)
        }
        for (j in Lm.JOINTS) {
            if (lm[j * 4 + 3] < 0.4f) continue
            val p = Offset(px(lm[j * 4]), py(lm[j * 4 + 1]))
            drawCircle(Color.White, 6f, p)
            drawCircle(boneColor, 6f, p, style = Stroke(2.5f))
        }
        // Raised hands glow so the player sees the jump is "armed".
        if (body.handsUp) {
            for (w in intArrayOf(Lm.L_WRIST, Lm.R_WRIST)) {
                val p = Offset(px(lm[w * 4]), py(lm[w * 4 + 1]))
                drawCircle(MX.Amber.copy(alpha = 0.35f), 26f, p)
                drawCircle(MX.Amber, 12f, p)
            }
        }
        // Head ring
        if (lm[Lm.NOSE * 4 + 3] > 0.5f) {
            val sw = kotlin.math.abs(lm[Lm.L_SHOULDER * 4] - lm[Lm.R_SHOULDER * 4]) * dw
            drawCircle(boneColor, (sw * 0.32f).coerceIn(10f, 60f), Offset(px(lm[0]), py(lm[1])), style = Stroke(5f))
        }
    }
}

private fun DrawScope.drawLanes(left: Float, right: Float, top: Float, h: Float, ox: Float, w: Float, lane: Int) {
    val laneW = right - left
    val zones = listOf(
        Triple(-1, maxOf(ox, left - laneW), left),
        Triple(0, left, right),
        Triple(1, right, minOf(ox + w, right + laneW)),
    )
    for ((id, x0, x1) in zones) {
        if (x1 <= x0) continue
        val active = id == lane
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Transparent, (if (active) MX.Cyan else Color.White).copy(alpha = if (active) 0.28f else 0.06f)),
                startY = top, endY = top + h,
            ),
            Offset(x0, top), Size(x1 - x0, h),
        )
    }
    val dash = PathEffect.dashPathEffect(floatArrayOf(16f, 12f))
    drawLine(Color.White.copy(alpha = 0.75f), Offset(left, top), Offset(left, top + h), 3f, pathEffect = dash)
    drawLine(Color.White.copy(alpha = 0.75f), Offset(right, top), Offset(right, top + h), 3f, pathEffect = dash)
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        textSize = h * 0.07f
        isFakeBoldText = true
        textAlign = android.graphics.Paint.Align.CENTER
    }
    val labels = listOf("L" to (left - laneW / 2), "C" to (left + laneW / 2), "R" to (right + laneW / 2))
    labels.forEachIndexed { i, (s, x) ->
        if (x < ox || x > ox + w) return@forEachIndexed
        paint.color = if (i - 1 == lane) 0xFF22E1FF.toInt() else 0xCCFFFFFF.toInt()
        drawContext.canvas.nativeCanvas.drawText(s, x, top + h - h * 0.03f, paint)
    }
}

private fun DrawScope.drawGuide(y: Float, ox: Float, w: Float, color: Color, label: String, active: Boolean) {
    val a = if (active) 1f else 0.75f
    if (active) drawLine(color.copy(alpha = 0.3f), Offset(ox, y), Offset(ox + w, y), 14f)
    drawLine(color.copy(alpha = a), Offset(ox, y), Offset(ox + w, y), if (active) 5f else 3f)
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        textSize = 26f
        isFakeBoldText = true
        this.color = android.graphics.Color.argb((255 * a).toInt(), (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt())
    }
    drawContext.canvas.nativeCanvas.drawText(label, ox + 12f, y - 10f, paint)
}
