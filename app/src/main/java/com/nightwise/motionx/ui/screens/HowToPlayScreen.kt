package com.nightwise.motionx.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nightwise.motionx.ui.components.*
import com.nightwise.motionx.ui.theme.MX
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

private class Page(val title: String, val body: String, val art: @Composable (Modifier) -> Unit)

/** Text with *highlighted* segments. */
@Composable
private fun Rich(text: String) {
    val parts = text.split("*")
    Text(
        buildAnnotatedString {
            parts.forEachIndexed { i, s ->
                if (i % 2 == 1) withStyle(SpanStyle(color = MX.TextHi, fontWeight = FontWeight.Bold)) { append(s) } else append(s)
            }
        },
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 27.sp),
        color = MX.TextHi,
    )
}

@Composable
fun HowToPlayScreen(onBack: () -> Unit, onTraining: () -> Unit) {
    val pages = listOf(
        Page("Set up", "Stand your phone *sideways (landscape) at waist height*. Step back *2–3 big steps* so your whole body fits on screen, and clear about 2.5 m × 1.5 m of floor.") { SetupArt(it) },
        Page("Three lanes", "The camera splits into three lanes. *Hop to your left* to go left, *hop to your right* to go right, hop back to the *middle* for the centre lane.") { LanesArt(it) },
        Page("Jump", "*Raise both hands* above your shoulders and *jump straight up* so your head crosses the *yellow JUMP line*. Hands up tells the game it's a jump, so hopping sideways never jumps by accident.") { JumpArt(it) },
        Page("Duck", "The *blue DUCK line* sits below your head. *Bend your knees* or lower your head below it to slide under blue bars. Stay down until you pass.") { DuckArt(it) },
        Page("Obstacles & pickups", "Jump *yellow hurdles*, duck *blue bars*, change lane around *red walls*. Grab coins and stars. Hit an obstacle and you lose a heart.") { LegendArt(it) },
        Page("You're ready", "Start with a *training run*: constant speed and unlimited lives. Wear shoes, keep the floor clear, and *stop if you feel tired or dizzy*.") { ReadyArt(it) },
    )
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    AuroraBackground {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 14.dp)) {
            ScreenHeader("How to play", "${pager.currentPage + 1} / ${pages.size}", onBack)
            VSpace(10.dp)
            HorizontalPager(pager, Modifier.weight(1f)) { i ->
                val p = pages[i]
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(20.dp))
                            .background(MX.Surface),
                    ) { p.art(Modifier.fillMaxSize()) }
                    Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                        Text(p.title, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MX.TextHi)
                        VSpace(12.dp)
                        Rich(p.body)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Start) {
                repeat(pages.size) { i ->
                    val sel = i == pager.currentPage
                    val w by animateDpAsState(if (sel) 26.dp else 8.dp, label = "dot")
                    val c by animateColorAsState(if (sel) MX.Cyan else MX.SurfaceHi, label = "dotc")
                    Box(Modifier.padding(horizontal = 4.dp).height(8.dp).width(w).clip(CircleShape).background(c))
                }
            }
            Row(Modifier.width(420.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (pager.currentPage > 0) {
                    GhostButton("Back", Modifier.weight(1f), height = 50.dp) { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }
                }
                if (pager.currentPage < pages.size - 1) {
                    GlowButton("Next", Modifier.weight(2f), height = 50.dp) { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }
                } else {
                    GlowButton("Start training run", Modifier.weight(2f), height = 50.dp, color = MX.Lime, leading = Ico.PLAY, onClick = onTraining)
                }
            }
            }
        }
    }
}

// ============================================================ art

@Composable
private fun loop(ms: Int): Float {
    val t = rememberInfiniteTransition(label = "loop")
    val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(ms, easing = LinearEasing)), label = "v")
    return v
}

/** Shared "camera view" backdrop with three lane zones; returns lane centres. */
private fun DrawScope.laneBackdrop(active: Int): FloatArray {
    val w = size.width
    val h = size.height
    val lw = w * 0.2f
    val cx = w / 2
    val xs = floatArrayOf(cx - lw, cx, cx + lw)
    for (i in 0..2) {
        val on = i - 1 == active
        drawRect(
            (if (on) MX.Cyan else Color.White).copy(alpha = if (on) 0.18f else 0.04f),
            Offset(xs[i] - lw / 2, 0f), Size(lw, h),
        )
    }
    val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
    drawLine(Color.White.copy(alpha = 0.7f), Offset(cx - lw / 2, 0f), Offset(cx - lw / 2, h), 3f, pathEffect = dash)
    drawLine(Color.White.copy(alpha = 0.7f), Offset(cx + lw / 2, 0f), Offset(cx + lw / 2, h), 3f, pathEffect = dash)
    drawRect(MX.SurfaceHi, Offset(0f, h * 0.86f), Size(w, h * 0.14f))
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true; textSize = h * 0.08f; isFakeBoldText = true; textAlign = android.graphics.Paint.Align.CENTER
    }
    listOf("L", "C", "R").forEachIndexed { i, s ->
        paint.color = if (i - 1 == active) MX.Cyan.toArgb() else 0xCCFFFFFF.toInt()
        drawContext.canvas.nativeCanvas.drawText(s, xs[i], h * 0.96f, paint)
    }
    return xs
}

private fun DrawScope.guideLine(y: Float, color: Color, label: String) {
    val w = size.width
    drawLine(color, Offset(w * 0.18f, y), Offset(w * 0.82f, y), 5f)
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true; textSize = 30f; isFakeBoldText = true
        this.color = android.graphics.Color.argb(255, (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt())
    }
    drawContext.canvas.nativeCanvas.drawText(label, w * 0.18f, y - 12f, paint)
}

@Composable
private fun SetupArt(modifier: Modifier) {
    val t = loop(2400)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        // floor
        drawLine(MX.Stroke, Offset(0f, h * 0.84f), Offset(w, h * 0.84f), 3f)
        // phone (landscape) on a stand
        val px = w * 0.2f
        val py = h * 0.5f
        drawRoundRect(MX.SurfaceHi, Offset(px - 38f, py - 24f), Size(76f, 48f), CornerRadius(8f))
        drawRoundRect(MX.Cyan, Offset(px - 32f, py - 18f), Size(64f, 36f), CornerRadius(4f))
        drawLine(MX.SurfaceHi, Offset(px, py + 24f), Offset(px, h * 0.82f), 6f)
        drawLine(MX.SurfaceHi, Offset(px - 24f, h * 0.82f), Offset(px + 24f, h * 0.82f), 6f)
        // field of view cone
        drawPath(
            androidx.compose.ui.graphics.Path().apply {
                moveTo(px + 34f, py); lineTo(w * 0.95f, h * 0.08f); lineTo(w * 0.95f, h * 0.92f); close()
            },
            MX.Cyan.copy(alpha = 0.1f),
        )
        // distance arrow
        val ay = h * 0.9f
        drawLine(MX.Cyan, Offset(px + 30f, ay), Offset(w * 0.68f, ay), 3f)
        val paint = android.graphics.Paint().apply { isAntiAlias = true; textSize = 34f; isFakeBoldText = true; color = MX.Cyan.toArgb(); textAlign = android.graphics.Paint.Align.CENTER }
        drawContext.canvas.nativeCanvas.drawText("2–3 m", (px + w * 0.68f) / 2, ay - 12f, paint)
        // person (breathing bob)
        drawSkeleton(Offset(w * 0.74f, h * 0.84f - sin(t * 2 * PI.toFloat()) * 3f), h * 0.66f, color = Color.White)
    }
}

@Composable
private fun LanesArt(modifier: Modifier) {
    val t = loop(4200)
    Canvas(modifier) {
        val seq = intArrayOf(0, -1, 0, 1)
        val seg = (t * 4).toInt().coerceAtMost(3)
        val local = t * 4 - seg
        val from = seq[seg]
        val to = seq[(seg + 1) % 4]
        val lane = if (local > 0.75f) to else from
        val xs = laneBackdrop(lane)
        val p = ((local - 0.75f) / 0.25f).coerceIn(0f, 1f)
        val x = xs[from + 1] + (xs[to + 1] - xs[from + 1]) * p
        val hopY = sin(p * PI.toFloat()) * size.height * 0.06f
        drawSkeleton(Offset(x, size.height * 0.84f - hopY), size.height * 0.72f, color = Color.White)
    }
}

@Composable
private fun JumpArt(modifier: Modifier) {
    val t = loop(2000)
    Canvas(modifier) {
        val h = size.height
        val xs = laneBackdrop(0)
        val jump = if (t in 0.35f..0.75f) sin((t - 0.35f) / 0.4f * PI.toFloat()) else 0f
        val crouch = if (t in 0.2f..0.35f) (t - 0.2f) / 0.15f * 0.4f else 0f
        val lineY = h * 0.08f
        val jumping = jump > 0.55f
        val arms = when {
            t < 0.1f -> 0f
            t < 0.25f -> (t - 0.1f) / 0.15f
            t < 0.8f -> 1f
            t < 0.92f -> 1f - (t - 0.8f) / 0.12f
            else -> 0f
        }
        guideLine(lineY, MX.Amber, if (arms > 0.9f) "JUMP  ·  hands up" else "JUMP")
        drawSkeleton(Offset(xs[1], h * 0.84f - jump * h * 0.16f), h * 0.72f, squat = crouch, armsUp = arms, color = if (jumping) MX.Amber else Color.White)
    }
}

@Composable
private fun DuckArt(modifier: Modifier) {
    val t = loop(2400)
    Canvas(modifier) {
        val h = size.height
        val xs = laneBackdrop(0)
        val squat = when {
            t < 0.25f -> 0f
            t < 0.4f -> (t - 0.25f) / 0.15f
            t < 0.75f -> 1f
            t < 0.9f -> 1f - (t - 0.75f) / 0.15f
            else -> 0f
        }
        val lineY = h * 0.3f
        guideLine(lineY, MX.Sky, "DUCK")
        drawSkeleton(Offset(xs[1], h * 0.84f), h * 0.72f, squat = squat, color = if (squat > 0.8f) MX.Sky else Color.White)
    }
}

@Composable
private fun LegendArt(modifier: Modifier) {
    val rows = listOf(
        Triple(MX.Amber, "Orange hurdle", "Jump"),
        Triple(MX.Sky, "Blue bar", "Duck"),
        Triple(MX.Coral, "Red wall", "Hop aside"),
        Triple(MX.Yellow, "Coin", "+10"),
        Triple(MX.Yellow, "Star", "+100"),
    )
    Column(modifier.padding(14.dp), verticalArrangement = Arrangement.SpaceEvenly) {
        rows.forEachIndexed { i, (c, name, act) ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MX.SurfaceHi).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Canvas(Modifier.size(width = 40.dp, height = 24.dp)) {
                    when (i) {
                        0 -> drawRoundRect(c, Offset(0f, size.height * 0.55f), Size(size.width, size.height * 0.3f), CornerRadius(4f))
                        1 -> {
                            drawRect(c, Offset(0f, 0f), Size(size.width, size.height * 0.28f))
                            drawRect(c.copy(alpha = 0.5f), Offset(0f, 0f), Size(5f, size.height))
                            drawRect(c.copy(alpha = 0.5f), Offset(size.width - 5f, 0f), Size(5f, size.height))
                        }
                        2 -> drawRoundRect(c, Offset(size.width * 0.2f, 0f), Size(size.width * 0.6f, size.height), CornerRadius(4f))
                        3 -> drawCircle(c, size.height * 0.4f)
                        else -> {
                            val p = androidx.compose.ui.graphics.Path()
                            for (k in 0 until 10) {
                                val a = (-PI / 2 + k * PI / 5).toFloat()
                                val r = if (k % 2 == 0) size.height * 0.5f else size.height * 0.2f
                                val pt = Offset(center.x + kotlin.math.cos(a) * r, center.y + sin(a) * r)
                                if (k == 0) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y)
                            }
                            p.close(); drawPath(p, c)
                        }
                    }
                }
                Text("  $name", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = MX.TextHi)
                Text(act, style = MaterialTheme.typography.titleMedium, color = MX.TextMid)
            }
        }
    }
}

@Composable
private fun ReadyArt(modifier: Modifier) {
    val t = loop(900)
    Canvas(modifier) {
        val h = size.height
        val w = size.width
        drawArc(MX.Lime.copy(alpha = 0.18f), 0f, 360f, false, Offset(w / 2 - h * 0.42f, h * 0.06f), Size(h * 0.84f, h * 0.84f), style = Stroke(h * 0.035f))
        drawArc(MX.Lime, -90f, 360f * t, false, Offset(w / 2 - h * 0.42f, h * 0.06f), Size(h * 0.84f, h * 0.84f), style = Stroke(h * 0.035f, cap = StrokeCap.Round))
        drawSkeleton(Offset(w / 2, h * 0.8f), h * 0.6f, runPhase = t * 2 * PI.toFloat(), color = MX.Lime)
    }
}
