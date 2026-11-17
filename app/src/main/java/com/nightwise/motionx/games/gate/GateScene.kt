package com.nightwise.motionx.games.gate

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import com.nightwise.motionx.games.pose.Bone
import com.nightwise.motionx.games.pose.Joint
import com.nightwise.motionx.games.pose.PoseDef
import com.nightwise.motionx.games.pose.PoseFigure
import com.nightwise.motionx.games.pose.PoseMatcher
import com.nightwise.motionx.tracking.Lm
import com.nightwise.motionx.ui.theme.MX
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private const val PLAYER_ALPHA = 0.55f

private fun lerp(a: Color, b: Color, t: Float) = Color(
    a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f,
)

private val HOLE_BONES = listOf(
    Joint.SL_SHOULDER to Joint.SR_SHOULDER, Joint.SL_HIP to Joint.SR_HIP,
    Joint.SL_SHOULDER to Joint.SL_HIP, Joint.SR_SHOULDER to Joint.SR_HIP,
    Joint.SL_SHOULDER to Joint.SL_ELBOW, Joint.SL_ELBOW to Joint.SL_WRIST,
    Joint.SR_SHOULDER to Joint.SR_ELBOW, Joint.SR_ELBOW to Joint.SR_WRIST,
    Joint.SL_HIP to Joint.SL_KNEE, Joint.SL_KNEE to Joint.SL_ANKLE,
    Joint.SR_HIP to Joint.SR_KNEE, Joint.SR_KNEE to Joint.SR_ANKLE,
)

/** Where the target figure sits on the final (arrived) wall: torso-unit scale + hip anchor. */
private class Placement(val unit: Float, val hip: Offset)

private fun placement(fig: PoseFigure, wall: Rect, holeWidth: Float): Placement {
    var minX = fig.headCenter[0] - fig.headR; var maxX = fig.headCenter[0] + fig.headR
    var minY = fig.headCenter[1] - fig.headR; var maxY = fig.headCenter[1] + fig.headR
    for (p in fig.joints.values) { minX = min(minX, p[0]); maxX = max(maxX, p[0]); minY = min(minY, p[1]); maxY = max(maxY, p[1]) }
    val pad = holeWidth
    val innerW = wall.width * 0.9f
    val innerH = wall.height * 0.9f
    // Cap so a standing person is ~85% of the wall height and never bigger.
    val unit = min(min(innerW / (maxX - minX + pad * 2), innerH / (maxY - minY + pad * 2)), wall.height * 0.86f / 3.1f)
    val cx = wall.center.x - (minX + maxX) / 2 * unit
    val bottom = wall.bottom - wall.height * 0.05f
    val cy = bottom - (maxY + pad * 0.6f) * unit
    return Placement(unit, Offset(cx, cy))
}

/**
 * Pseudo-3D Pose Gate stage: synthwave floor, a wall with a pose-shaped hole flying towards the
 * camera, and the player's live figure standing where the hole will arrive.
 */
@Composable
fun GateScene(
    pose: PoseDef,
    progress: Float,
    outcome: GateGame.Outcome,
    outcomeT: Float,
    time: Float,
    holeWidth: Float,
    accent: Color,
    inWindow: Boolean,
    playerLm: FloatArray?,
    playerAspect: Float,
    result: PoseMatcher.Result?,
    modifier: Modifier = Modifier,
    showPlayer: Boolean = true,
) {
    val fig = remember(pose) { PoseFigure.build(pose) }
    val shards = remember(pose, outcome) { Random(pose.id.hashCode()).let { r -> List(28) { floatArrayOf(r.nextFloat(), r.nextFloat(), r.nextFloat() * 2 - 1, r.nextFloat()) } } }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val horizon = h * 0.36f
        val vp = Offset(w / 2, horizon)
        drawStage(vp, time, accent)

        val finalWall = Rect(w * 0.07f, h * 0.04f, w * 0.93f, h * 0.97f)
        val place = placement(fig, finalWall, holeWidth)

        // ---------- wall ----------
        var p = progress
        var alpha = 1f
        var dropY = 0f
        when (outcome) {
            GateGame.Outcome.PASS, GateGame.Outcome.PERFECT -> {
                p = 1f + outcomeT * 0.55f; alpha = (1f - outcomeT / 0.7f).coerceIn(0f, 1f)
            }
            GateGame.Outcome.FAIL -> alpha = 0f
            GateGame.Outcome.NONE -> Unit
        }
        if (alpha > 0f) {
            val s = 1f / (1f + 6f * (1f - p))
            fun proj(o: Offset) = Offset(vp.x + (o.x - vp.x) * s, vp.y + (o.y - vp.y) * s + dropY)
            val tl = proj(finalWall.topLeft)
            val br = proj(finalWall.bottomRight)
            val wallRect = Rect(tl, br)
            val holeU = place.unit * s
            val hip = proj(place.hip)
            drawWall(wallRect, fig, hip, holeU, holeWidth, accent, alpha, inWindow, time)
        }
        // ---------- crash shards ----------
        if (outcome == GateGame.Outcome.FAIL) drawShards(finalWall, shards, outcomeT, accent)

        // ---------- player ----------
        if (showPlayer && playerLm != null) {
            drawPlayer(playerLm, playerAspect, place, result, outcome)
        } else if (showPlayer) {
            // ghost outline so the player knows where to stand
            drawFigureOutline(fig, place.hip, place.unit, Color.White.copy(alpha = 0.18f), 0.08f * place.unit)
        }
        if (outcome == GateGame.Outcome.FAIL && outcomeT < 0.5f) {
            drawRect(MX.Coral.copy(alpha = 0.35f * (1f - outcomeT / 0.5f)))
        }
        if ((outcome == GateGame.Outcome.PASS || outcome == GateGame.Outcome.PERFECT) && outcomeT < 0.6f) {
            drawRect(Color.White.copy(alpha = 0.08f * (1f - outcomeT / 0.6f)))
        }
    }
}

private fun DrawScope.drawStage(vp: Offset, time: Float, accent: Color) {
    val w = size.width
    val h = size.height
    drawRect(MX.Night, Offset.Zero, Size(w, vp.y))
    // floor
    drawRect(MX.Night2, Offset(0f, vp.y), Size(w, h - vp.y))
    drawLine(MX.Stroke, Offset(0f, vp.y), Offset(w, vp.y), 2f)
    for (i in -14..14) {
        val x = vp.x + i * w * 0.09f
        drawLine(Color.White.copy(alpha = 0.06f), vp, Offset(vp.x + (x - vp.x) * 1f, h), 1.5f)
    }
    val scroll = (time * 0.6f) % 1f
    for (j in 0 until 12) {
        val d = ((j + scroll) / 12f).let { it * it }
        val y = vp.y + (h - vp.y) * d
        drawLine(Color.White.copy(alpha = 0.02f + 0.08f * d), Offset(0f, y), Offset(w, y), 1.5f)
    }
    // stage pad under the player
    drawOval(accent.copy(alpha = 0.6f), Offset(w * 0.3f, h * 0.92f), Size(w * 0.4f, h * 0.06f), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
}

private fun DrawScope.drawWall(
    r: Rect, fig: PoseFigure, hip: Offset, u: Float, holeWidth: Float, accent: Color, alpha: Float, inWindow: Boolean, time: Float,
) {
    fun J(j: Joint) = fig.joints.getValue(j).let { Offset(hip.x + it[0] * u, hip.y + it[1] * u) }
    val head = Offset(hip.x + fig.headCenter[0] * u, hip.y + fig.headCenter[1] * u)
    val holeW = holeWidth * u
    val rim = max(3f, u * 0.06f)

    drawContext.canvas.saveLayer(Rect(Offset.Zero, size), Paint())
    // wall body
    drawRect(
        MX.SurfaceHi, r.topLeft, r.size, alpha = alpha,
    )
    // bricks
    val rows = 9
    val bh = r.height / rows
    for (i in 1 until rows) drawLine(Color.White.copy(alpha = 0.08f * alpha), Offset(r.left, r.top + i * bh), Offset(r.right, r.top + i * bh), 1.5f)
    for (i in 0 until rows) {
        val off = if (i % 2 == 0) 0f else 0.5f
        var x = r.left + (off * r.width / 6)
        while (x < r.right) {
            drawLine(Color.White.copy(alpha = 0.06f * alpha), Offset(x, r.top + i * bh), Offset(x, r.top + (i + 1) * bh), 1.5f)
            x += r.width / 6
        }
    }
    // hole rim (bright), then punch the hole
    val rimCol = if (inWindow) lerp(MX.Amber, Color.White, 0.5f + 0.5f * sin(time * 12f)) else MX.Amber
    val pulse = 1f
    for ((a, b) in HOLE_BONES) drawLine(rimCol.copy(alpha = alpha * pulse), J(a), J(b), holeW + rim * 2, StrokeCap.Round)
    drawCircle(rimCol.copy(alpha = alpha * pulse), fig.headR * u * 1.45f + rim, head)
    for ((a, b) in HOLE_BONES) drawLine(Color.Black, J(a), J(b), holeW, StrokeCap.Round, blendMode = BlendMode.Clear)
    drawCircle(Color.Black, fig.headR * u * 1.45f, head, blendMode = BlendMode.Clear)
    // torso fill so the body hole is solid
    val torso = Path().apply {
        moveTo(J(Joint.SL_SHOULDER).x, J(Joint.SL_SHOULDER).y); lineTo(J(Joint.SR_SHOULDER).x, J(Joint.SR_SHOULDER).y)
        lineTo(J(Joint.SR_HIP).x, J(Joint.SR_HIP).y); lineTo(J(Joint.SL_HIP).x, J(Joint.SL_HIP).y); close()
    }
    drawPath(torso, Color.Black, blendMode = BlendMode.Clear)
    drawContext.canvas.restore()
    // soft gold tint inside the hole so the shape reads clearly
    val tint = MX.Amber.copy(alpha = 0.16f * alpha)
    for ((a, b) in HOLE_BONES) drawLine(tint, J(a), J(b), holeW, StrokeCap.Round)
    drawCircle(tint, fig.headR * u * 1.45f, head)
    drawPath(torso, tint)
    // neon frame
    drawRect(accent.copy(alpha = alpha), r.topLeft, r.size, style = Stroke(rim * 1.6f))
}

private fun DrawScope.drawShards(wall: Rect, shards: List<FloatArray>, t: Float, accent: Color) {
    val a = (1f - t / GateGame.OUTCOME_SECONDS).coerceIn(0f, 1f)
    for (s in shards) {
        val cx = wall.left + s[0] * wall.width
        val cy = wall.top + s[1] * wall.height
        val vx = (s[0] - 0.5f) * wall.width * 1.6f
        val vy = -wall.height * 0.4f * s[3]
        val x = cx + vx * t
        val y = cy + vy * t + 0.5f * wall.height * 2.2f * t * t
        val sz = wall.width * (0.05f + 0.06f * s[3])
        translate(x, y) {
            rotate(s[2] * 400f * t, Offset.Zero) {
                drawRect(accent.copy(alpha = 0.8f * a), Offset(-sz / 2, -sz / 2), Size(sz, sz * 0.7f))
                drawRect(Color.White.copy(alpha = 0.4f * a), Offset(-sz / 2, -sz / 2), Size(sz, sz * 0.7f), style = Stroke(2f))
            }
        }
    }
}

private fun DrawScope.drawFigureOutline(fig: PoseFigure, hip: Offset, u: Float, col: Color, stroke: Float) {
    fun J(j: Joint) = fig.joints.getValue(j).let { Offset(hip.x + it[0] * u, hip.y + it[1] * u) }
    for ((a, b) in HOLE_BONES) drawLine(col, J(a), J(b), stroke, StrokeCap.Round)
    drawCircle(col, fig.headR * u, Offset(hip.x + fig.headCenter[0] * u, hip.y + fig.headCenter[1] * u), style = Stroke(stroke))
}

/** Live player figure in torso units around their hips, drawn at the hole's placement. */
private fun DrawScope.drawPlayer(lm: FloatArray, aspect: Float, place: Placement, result: PoseMatcher.Result?, outcome: GateGame.Outcome) {
    fun x(i: Int) = lm[i * 4] * aspect
    fun y(i: Int) = lm[i * 4 + 1]
    fun v(i: Int) = lm[i * 4 + 3] > 0.3f
    if (!v(Lm.L_HIP) || !v(Lm.R_HIP) || !v(Lm.L_SHOULDER) || !v(Lm.R_SHOULDER)) return
    val hx = (x(Lm.L_HIP) + x(Lm.R_HIP)) / 2
    val hy = (y(Lm.L_HIP) + y(Lm.R_HIP)) / 2
    val sx = (x(Lm.L_SHOULDER) + x(Lm.R_SHOULDER)) / 2
    val sy = (y(Lm.L_SHOULDER) + y(Lm.R_SHOULDER)) / 2
    val torso = hypot(sx - hx, sy - hy).coerceAtLeast(0.02f)
    val u = place.unit
    fun P(i: Int) = Offset(place.hip.x + (x(i) - hx) / torso * u, place.hip.y + (y(i) - hy) / torso * u)

    val stroke = u * 0.13f
    val matched = result?.matched == true
    fun col(st: Int) = when {
        outcome == GateGame.Outcome.FAIL -> MX.Coral
        matched -> MX.Lime
        st == 2 -> MX.Lime
        st == 1 -> MX.Amber
        st == 0 -> MX.Coral
        else -> MX.Cyan
    }
    // Player is see-through so the wall and hole stay readable behind it.
    fun limb(a: Int, b: Int, c: Color) {
        if (!v(a) || !v(b)) return
        drawLine(c.copy(alpha = 0.14f), P(a), P(b), stroke * 2.0f, StrokeCap.Round)
        drawLine(c.copy(alpha = PLAYER_ALPHA), P(a), P(b), stroke, StrokeCap.Round)
    }
    val torsoCol = col(result?.status(Bone.TORSO) ?: -1)
    val tp = Path().apply {
        moveTo(P(Lm.L_SHOULDER).x, P(Lm.L_SHOULDER).y); lineTo(P(Lm.R_SHOULDER).x, P(Lm.R_SHOULDER).y)
        lineTo(P(Lm.R_HIP).x, P(Lm.R_HIP).y); lineTo(P(Lm.L_HIP).x, P(Lm.L_HIP).y); close()
    }
    drawPath(tp, torsoCol.copy(alpha = 0.16f))
    limb(Lm.L_SHOULDER, Lm.R_SHOULDER, torsoCol); limb(Lm.L_HIP, Lm.R_HIP, torsoCol)
    limb(Lm.L_SHOULDER, Lm.L_HIP, torsoCol); limb(Lm.R_SHOULDER, Lm.R_HIP, torsoCol)
    if (result != null) {
        for (b in Bone.entries) {
            if (b == Bone.TORSO) continue
            val pr = result.boneLandmarks[b.ordinal]
            limb(pr[0], pr[1], col(result.status(b)))
        }
    } else {
        for (bn in Lm.BONES) if (bn[0] <= Lm.R_ANKLE && bn[1] <= Lm.R_ANKLE) limb(bn[0], bn[1], MX.Cyan)
    }
    if (v(Lm.NOSE)) {
        drawCircle(torsoCol.copy(alpha = 0.18f), u * 0.18f, P(Lm.NOSE))
        drawCircle(torsoCol.copy(alpha = PLAYER_ALPHA), u * 0.18f, P(Lm.NOSE), style = Stroke(stroke * 0.6f))
    }
}
