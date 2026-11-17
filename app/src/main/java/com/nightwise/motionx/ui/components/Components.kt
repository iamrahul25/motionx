package com.nightwise.motionx.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nightwise.motionx.ui.theme.MX
import kotlin.math.cos
import kotlin.math.sin

/** Slowly drifting aurora orbs over a deep gradient: the backdrop of every menu screen. */
@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    accentA: Color = MX.Violet,
    accentB: Color = MX.Cyan,
    accentC: Color = MX.Magenta,
    content: @Composable BoxScope.() -> Unit,
) {
    val t = rememberInfiniteTransition(label = "aurora")
    val phase by t.animateFloat(
        0f, (2 * Math.PI).toFloat(),
        infiniteRepeatable(tween(18000, easing = LinearEasing)), label = "phase",
    )
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(MX.Night2, MX.Night, Color(0xFF05060D))))
            .drawBehind {
                val w = size.width
                val h = size.height
                val r = maxOf(w, h)
                fun orb(c: Color, cx: Float, cy: Float, rad: Float, a: Float) {
                    drawCircle(
                        Brush.radialGradient(listOf(c.copy(alpha = a), Color.Transparent), Offset(cx, cy), rad),
                        rad, Offset(cx, cy),
                    )
                }
                orb(accentA, w * (0.15f + 0.1f * cos(phase)), h * (0.12f + 0.05f * sin(phase)), r * 0.55f, 0.35f)
                orb(accentB, w * (0.95f + 0.08f * sin(phase * 2)), h * (0.42f + 0.08f * cos(phase)), r * 0.45f, 0.22f)
                orb(accentC, w * (0.3f + 0.12f * sin(phase)), h * (1.0f + 0.04f * cos(phase * 2)), r * 0.5f, 0.22f)
                // subtle perspective grid at the bottom = "track" motif
                val gridTop = h * 0.78f
                val lineColor = MX.Cyan.copy(alpha = 0.06f)
                for (i in -8..8) {
                    val x0 = w / 2 + i * w * 0.02f
                    val x1 = w / 2 + i * w * 0.18f
                    drawLine(lineColor, Offset(x0, gridTop), Offset(x1, h), 1.5f)
                }
                val scroll = (phase / (2 * Math.PI).toFloat() * 6f) % 1f
                for (j in 0 until 7) {
                    val p = ((j + scroll) / 7f)
                    val y = gridTop + (h - gridTop) * p * p
                    drawLine(lineColor, Offset(0f, y), Offset(w, y), 1.5f)
                }
            },
        content = content,
    )
}

/** Translucent card with a hairline border; optional neon edge glow. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    glow: Color? = null,
    corner: Dp = 24.dp,
    onClick: (() -> Unit)? = null,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.97f else 1f, spring(stiffness = 600f), label = "press")
    Column(
        modifier
            .scale(s)
            .drawBehind {
                if (glow != null) {
                    val cr = corner.toPx()
                    for (i in 1..4) {
                        val g = i * 3.dp.toPx()
                        drawRoundRect(
                            glow.copy(alpha = 0.10f / i),
                            topLeft = Offset(-g, -g),
                            size = Size(size.width + 2 * g, size.height + 2 * g),
                            cornerRadius = CornerRadius(cr + g),
                        )
                    }
                }
            }
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF1A2050).copy(alpha = 0.75f), Color(0xFF0F1330).copy(alpha = 0.85f)),
                ),
            )
            .border(1.dp, Brush.linearGradient(listOf((glow ?: Color.White).copy(alpha = 0.45f), Color.White.copy(alpha = 0.06f))), shape)
            .then(if (onClick != null) Modifier.clickable(interaction, null, onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** Primary call-to-action: gradient pill with press bounce and soft glow. */
@Composable
fun GlowButton(
    text: String,
    modifier: Modifier = Modifier,
    brush: Brush = MX.PrimaryGradient,
    glow: Color = MX.Cyan,
    height: Dp = 60.dp,
    textColor: Color = MX.Night,
    leading: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.95f else 1f, spring(stiffness = 700f), label = "press")
    val pulse = rememberInfiniteTransition(label = "pulse")
    val glowA by pulse.animateFloat(0.18f, 0.34f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "glow")
    Box(
        modifier
            .height(height)
            .scale(s)
            .drawBehind {
                if (!enabled) return@drawBehind
                val cr = size.height / 2
                for (i in 1..5) {
                    val g = i * 3.dp.toPx()
                    drawRoundRect(
                        glow.copy(alpha = glowA / i),
                        topLeft = Offset(-g, -g + 4.dp.toPx()),
                        size = Size(size.width + 2 * g, size.height + 2 * g),
                        cornerRadius = CornerRadius(cr + g),
                    )
                }
            }
            .clip(CircleShape)
            .background(if (enabled) brush else Brush.linearGradient(listOf(MX.SurfaceHi, MX.Surface)))
            .clickable(interaction, null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                Text(leading, fontSize = 18.sp, color = if (enabled) textColor else MX.TextLow)
                Box(Modifier.size(10.dp))
            }
            Text(
                text.uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 17.sp, letterSpacing = 2.sp),
                fontWeight = FontWeight.Black,
                color = if (enabled) textColor else MX.TextLow,
            )
        }
    }
}

/** Secondary button: glass pill. */
@Composable
fun GhostButton(
    text: String,
    modifier: Modifier = Modifier,
    leading: String? = null,
    accent: Color = MX.Cyan,
    height: Dp = 52.dp,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.95f else 1f, spring(stiffness = 700f), label = "press")
    Box(
        modifier
            .height(height)
            .scale(s)
            .clip(RoundedCornerShape(18.dp))
            .background(MX.Glass.copy(alpha = 0.07f))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .clickable(interaction, null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                Text(leading, fontSize = 17.sp)
                Box(Modifier.size(8.dp))
            }
            Text(text, style = MaterialTheme.typography.titleMedium, color = MX.TextHi)
        }
    }
}

@Composable
fun Pill(text: String, color: Color = MX.Cyan, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.3f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** Circular back button with a drawn chevron (no icon font dependency). */
@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(MX.Glass.copy(alpha = 0.08f))
            .border(1.dp, MX.Stroke, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(16.dp)) {
            val p = Path().apply {
                moveTo(size.width * 0.68f, size.height * 0.1f)
                lineTo(size.width * 0.28f, size.height * 0.5f)
                lineTo(size.width * 0.68f, size.height * 0.9f)
            }
            drawPath(p, MX.TextHi, style = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

@Composable
fun ScreenHeader(title: String, subtitle: String?, onBack: (() -> Unit)?, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            BackButton(onBack)
            Box(Modifier.size(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = MX.TextHi)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MX.TextMid)
        }
        trailing()
    }
}

/** Two-option segmented toggle. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, accent: Color = MX.Cyan) {
    Row(
        modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, MX.StrokeSoft, CircleShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(if (sel) accent else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (sel) MX.Night else MX.TextMid,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun StatTile(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.22f), RoundedCornerShape(18.dp))
            .padding(vertical = 12.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Black)
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MX.TextLow)
    }
}

/** Star rating 1..5 drawn as text glyphs. */
@Composable
fun Stars(count: Int, max: Int = 5, color: Color = MX.Amber) {
    Row {
        repeat(max) { i ->
            Text(if (i < count) "★" else "☆", color = if (i < count) color else MX.TextLow, fontSize = 16.sp)
        }
    }
}

fun Modifier.neonUnderline(color: Color) = this.drawBehind {
    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, color, Color.Transparent)), Offset(0f, size.height - 2.dp.toPx()), Size(size.width, 2.dp.toPx()))
}

@Composable
fun VSpace(h: Dp) = Box(Modifier.height(h))

@Composable
fun FullScreenCenter(content: @Composable BoxScope.() -> Unit) =
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center, content = content)
