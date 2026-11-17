package com.nightwise.motionx.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Hand-drawn icon set on a 24-unit grid (rounded 2-unit strokes, a few solid glyphs),
 * so the app never relies on emoji or an icon font.
 */
enum class Ico {
    PLAY, PAUSE, REPLAY, HEART, HEART_OUTLINE, STAR, STAR_OUTLINE, LOCK, UNLOCK, CHECK, CLOSE,
    EYE, EYE_OFF, CAMERA, HAND, SOUND, TARGET, FLAG, TROPHY, PERSON, LEAF, FLAME, BOLT, DIAMOND,
    CROWN, SLIDERS, HELP, UP, DOWN, LEFT, RIGHT, GEAR, CLOCK, RUN, COIN, MOON, INFINITY, PLUS,
    MINUS, CHEVRON, BUBBLES, WALL, POSE, SPARK,
}

@Composable
fun Icon(ico: Ico, color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    Canvas(modifier.size(size)) { drawIco(ico, color) }
}

/** Draws [ico] filling the current DrawScope. */
fun DrawScope.drawIco(ico: Ico, color: Color) {
    val k = size.minDimension / 24f
    val dx = (size.width - 24f * k) / 2
    val dy = (size.height - 24f * k) / 2
    drawContext.transform.translate(dx, dy)
    scale(k, k, pivot = Offset.Zero) { glyph(ico, color) }
    drawContext.transform.translate(-dx, -dy)
}

private fun p(vararg pts: Float, close: Boolean = false) = Path().apply {
    moveTo(pts[0], pts[1])
    var i = 2
    while (i < pts.size) { lineTo(pts[i], pts[i + 1]); i += 2 }
    if (close) close()
}

private fun DrawScope.glyph(ico: Ico, c: Color) {
    val st = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun line(x0: Float, y0: Float, x1: Float, y1: Float, w: Float = 2f) =
        drawLine(c, Offset(x0, y0), Offset(x1, y1), w, StrokeCap.Round)
    fun stroke(path: Path) = drawPath(path, c, style = st)
    fun fill(path: Path) = drawPath(path, c)
    fun arc(cx: Float, cy: Float, r: Float, start: Float, sweep: Float) =
        drawArc(c, start, sweep, false, Offset(cx - r, cy - r), Size(r * 2, r * 2), style = st)
    fun heartPath() = Path().apply {
        moveTo(12f, 20.5f)
        cubicTo(5f, 15.5f, 2.5f, 12f, 2.5f, 8.5f)
        cubicTo(2.5f, 5.5f, 4.8f, 3.5f, 7.4f, 3.5f)
        cubicTo(9.3f, 3.5f, 11f, 4.6f, 12f, 6.3f)
        cubicTo(13f, 4.6f, 14.7f, 3.5f, 16.6f, 3.5f)
        cubicTo(19.2f, 3.5f, 21.5f, 5.5f, 21.5f, 8.5f)
        cubicTo(21.5f, 12f, 19f, 15.5f, 12f, 20.5f)
        close()
    }
    fun starPath() = Path().apply {
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) 10f else 4.4f
            val a = -PI / 2 + i * PI / 5
            val x = 12f + (r * cos(a)).toFloat()
            val y = 12.6f + (r * sin(a)).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    when (ico) {
        Ico.PLAY -> fill(Path().apply { moveTo(7f, 4.5f); lineTo(19.5f, 12f); lineTo(7f, 19.5f); close() })
        Ico.PAUSE -> { drawRoundRect(c, Offset(6f, 4.5f), Size(4f, 15f), CornerRadius(1.2f)); drawRoundRect(c, Offset(14f, 4.5f), Size(4f, 15f), CornerRadius(1.2f)) }
        Ico.REPLAY -> { arc(12f, 12f, 7.5f, -60f, 300f); stroke(p(19.5f, 3.5f, 18.5f, 8.6f, 13.6f, 7.2f)) }
        Ico.HEART -> fill(heartPath())
        Ico.HEART_OUTLINE -> stroke(heartPath())
        Ico.STAR -> fill(starPath())
        Ico.STAR_OUTLINE -> stroke(starPath())
        Ico.LOCK, Ico.UNLOCK -> {
            drawRoundRect(c, Offset(5f, 10.5f), Size(14f, 10f), CornerRadius(2.5f))
            if (ico == Ico.LOCK) arc(12f, 9f, 4.5f, 180f, 180f) else arc(12f, 7.5f, 4.5f, 180f, 160f)
            if (ico == Ico.LOCK) { line(7.5f, 9f, 7.5f, 10.5f); line(16.5f, 9f, 16.5f, 10.5f) } else line(7.5f, 7.5f, 7.5f, 10.5f)
        }
        Ico.CHECK -> stroke(p(4.5f, 12.5f, 9.5f, 17.5f, 19.5f, 6.5f))
        Ico.CLOSE -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
        Ico.EYE, Ico.EYE_OFF -> {
            stroke(Path().apply { moveTo(2.5f, 12f); quadraticTo(12f, 2f, 21.5f, 12f); quadraticTo(12f, 22f, 2.5f, 12f); close() })
            drawCircle(c, 3f, Offset(12f, 12f))
            if (ico == Ico.EYE_OFF) line(4f, 20f, 20f, 4f)
        }
        Ico.CAMERA -> {
            stroke(Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(3f, 7f, 21f, 19.5f, CornerRadius(2.5f))) })
            stroke(p(8f, 7f, 9.5f, 4.5f, 14.5f, 4.5f, 16f, 7f))
            drawCircle(c, 3.6f, Offset(12f, 13f), style = st)
        }
        Ico.HAND -> {
            line(9f, 12f, 9f, 5f); line(12f, 11f, 12f, 3.5f); line(15f, 11f, 15f, 4.5f); line(18f, 12f, 18f, 7f)
            stroke(Path().apply { moveTo(18f, 12f); lineTo(18f, 15f); quadraticTo(18f, 21f, 12.5f, 21f); quadraticTo(8.5f, 21f, 6.5f, 17f); lineTo(4.5f, 12.5f); quadraticTo(5f, 11f, 6.5f, 11.8f); lineTo(9f, 14.5f) })
        }
        Ico.SOUND -> {
            fill(p(3.5f, 9f, 7.5f, 9f, 12.5f, 4.5f, 12.5f, 19.5f, 7.5f, 15f, 3.5f, 15f, close = true))
            arc(13f, 12f, 4f, -50f, 100f); arc(13f, 12f, 7.5f, -50f, 100f)
        }
        Ico.TARGET -> { drawCircle(c, 8.5f, Offset(12f, 12f), style = st); drawCircle(c, 4.5f, Offset(12f, 12f), style = st); drawCircle(c, 1.6f, Offset(12f, 12f)) }
        Ico.FLAG -> { line(5.5f, 3.5f, 5.5f, 21f); fill(p(6.5f, 4f, 19f, 4f, 16f, 8.5f, 19f, 13f, 6.5f, 13f, close = true)) }
        Ico.TROPHY -> {
            stroke(Path().apply { moveTo(7f, 4f); lineTo(17f, 4f); lineTo(17f, 9f); quadraticTo(17f, 14.5f, 12f, 14.5f); quadraticTo(7f, 14.5f, 7f, 9f); close() })
            arc(7f, 8f, 3f, 90f, 180f); arc(17f, 8f, 3f, -90f, 180f)
            line(12f, 14.5f, 12f, 18f); line(8f, 20f, 16f, 20f)
        }
        Ico.PERSON -> {
            drawCircle(c, 2.6f, Offset(12f, 4.8f), style = st)
            line(12f, 8.5f, 12f, 14.5f); line(6f, 10.5f, 18f, 10.5f); line(12f, 14.5f, 8.5f, 21f); line(12f, 14.5f, 15.5f, 21f)
        }
        Ico.LEAF -> {
            stroke(Path().apply { moveTo(5f, 19f); cubicTo(4f, 9f, 11f, 4f, 20f, 4f); cubicTo(20f, 13f, 15f, 20f, 5f, 19f); close() })
            line(5f, 19f, 13f, 11f)
        }
        Ico.FLAME -> fill(Path().apply {
            moveTo(12f, 2.5f)
            cubicTo(13f, 6.5f, 19f, 9f, 19f, 14.5f)
            cubicTo(19f, 18.5f, 16f, 21.5f, 12f, 21.5f)
            cubicTo(8f, 21.5f, 5f, 18.5f, 5f, 14.5f)
            cubicTo(5f, 11.5f, 7f, 9.5f, 8.5f, 8.5f)
            cubicTo(8.5f, 11f, 9.5f, 12.5f, 11f, 13f)
            cubicTo(10.5f, 9f, 11f, 5.5f, 12f, 2.5f)
            close()
        })
        Ico.BOLT -> fill(p(13.5f, 2.5f, 5f, 13.5f, 11f, 13.5f, 10f, 21.5f, 19f, 10f, 13f, 10f, close = true))
        Ico.DIAMOND -> { stroke(p(7f, 4f, 17f, 4f, 21f, 9f, 12f, 20.5f, 3f, 9f, close = true)); line(3f, 9f, 21f, 9f) }
        Ico.CROWN -> { stroke(p(3.5f, 7f, 8f, 12f, 12f, 5f, 16f, 12f, 20.5f, 7f, 18.5f, 18f, 5.5f, 18f, close = true)) }
        Ico.SLIDERS -> {
            line(4f, 7f, 20f, 7f); line(4f, 17f, 20f, 17f)
            drawCircle(Color.Black, 2.6f, Offset(9f, 7f)); drawCircle(c, 2.6f, Offset(9f, 7f), style = st)
            drawCircle(Color.Black, 2.6f, Offset(15f, 17f)); drawCircle(c, 2.6f, Offset(15f, 17f), style = st)
        }
        Ico.HELP -> {
            stroke(Path().apply { moveTo(8.5f, 8.5f); cubicTo(8.5f, 4f, 15.5f, 4f, 15.5f, 8.5f); cubicTo(15.5f, 11.5f, 12f, 11.5f, 12f, 14.5f) })
            drawCircle(c, 1.4f, Offset(12f, 19f))
        }
        Ico.UP -> fill(p(12f, 5f, 20f, 18f, 4f, 18f, close = true))
        Ico.DOWN -> fill(p(12f, 19f, 20f, 6f, 4f, 6f, close = true))
        Ico.LEFT -> fill(p(5f, 12f, 18f, 4f, 18f, 20f, close = true))
        Ico.RIGHT -> fill(p(19f, 12f, 6f, 4f, 6f, 20f, close = true))
        Ico.GEAR -> {
            for (i in 0 until 8) {
                val a = i * PI / 4
                line(12f + 6.2f * cos(a).toFloat(), 12f + 6.2f * sin(a).toFloat(), 12f + 9.2f * cos(a).toFloat(), 12f + 9.2f * sin(a).toFloat(), 3f)
            }
            drawCircle(c, 6.5f, Offset(12f, 12f), style = st)
            drawCircle(c, 2.6f, Offset(12f, 12f), style = st)
        }
        Ico.CLOCK -> { drawCircle(c, 9f, Offset(12f, 12f), style = st); stroke(p(12f, 6.5f, 12f, 12f, 15.5f, 14f)) }
        Ico.RUN -> {
            drawCircle(c, 2.2f, Offset(15f, 4.2f))
            stroke(p(6f, 10f, 10f, 7.5f, 14f, 8.5f, 12f, 13.5f, 16f, 16f, 15f, 21f))
            stroke(p(12f, 13.5f, 9f, 17f, 4.5f, 17f)); stroke(p(14f, 8.5f, 16f, 11.5f, 19.5f, 11.5f))
        }
        Ico.COIN -> { drawCircle(c, 9f, Offset(12f, 12f), style = st); drawCircle(c, 5f, Offset(12f, 12f), style = Stroke(1.6f)) }
        Ico.MOON -> fill(Path().apply {
            moveTo(15f, 3f)
            cubicTo(9f, 3.5f, 5f, 8f, 5.5f, 13.5f)
            cubicTo(6f, 18.5f, 11f, 22f, 16f, 20.5f)
            cubicTo(17.5f, 20f, 18.5f, 19.3f, 19.5f, 18.5f)
            cubicTo(13.5f, 18.5f, 10f, 14f, 10.5f, 9.5f)
            cubicTo(11f, 6.5f, 12.5f, 4.5f, 15f, 3f)
            close()
        })
        Ico.INFINITY -> stroke(Path().apply {
            moveTo(12f, 12f); cubicTo(9f, 7f, 3f, 7.5f, 3f, 12f); cubicTo(3f, 16.5f, 9f, 17f, 12f, 12f)
            cubicTo(15f, 7f, 21f, 7.5f, 21f, 12f); cubicTo(21f, 16.5f, 15f, 17f, 12f, 12f)
        })
        Ico.PLUS -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
        Ico.MINUS -> line(5f, 12f, 19f, 12f)
        Ico.CHEVRON -> stroke(p(9f, 5f, 16f, 12f, 9f, 19f))
        Ico.BUBBLES -> { drawCircle(c, 5.5f, Offset(9f, 14f), style = st); drawCircle(c, 3.5f, Offset(17f, 7f), style = st); drawCircle(c, 2f, Offset(18f, 16.5f), style = st) }
        Ico.WALL -> {
            stroke(Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(3f, 4f, 21f, 20f, CornerRadius(1.5f))) })
            line(3f, 9.3f, 21f, 9.3f, 1.6f); line(3f, 14.6f, 21f, 14.6f, 1.6f)
            line(9f, 4f, 9f, 9.3f, 1.6f); line(15f, 9.3f, 15f, 14.6f, 1.6f); line(9f, 14.6f, 9f, 20f, 1.6f)
        }
        Ico.POSE -> {
            drawCircle(c, 2.4f, Offset(12f, 4.6f), style = st)
            line(12f, 8f, 12f, 14f); stroke(p(5f, 4f, 8f, 9.5f, 12f, 9.5f, 16f, 9.5f, 19f, 4f))
            line(12f, 14f, 7.5f, 21f); line(12f, 14f, 16.5f, 21f)
        }
        Ico.SPARK -> fill(Path().apply {
            moveTo(12f, 2.5f); quadraticTo(13f, 11f, 21.5f, 12f); quadraticTo(13f, 13f, 12f, 21.5f)
            quadraticTo(11f, 13f, 2.5f, 12f); quadraticTo(11f, 11f, 12f, 2.5f); close()
        })
    }
}
