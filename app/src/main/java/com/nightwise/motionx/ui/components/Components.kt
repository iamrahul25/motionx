package com.nightwise.motionx.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nightwise.motionx.ui.theme.MX
import kotlin.math.cos
import kotlin.math.sin

/** Plain true-black backdrop used by every menu screen. Accent params are kept for call-site compatibility. */
@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") accentA: Color = MX.Violet,
    @Suppress("UNUSED_PARAMETER") accentB: Color = MX.Cyan,
    @Suppress("UNUSED_PARAMETER") accentC: Color = MX.Magenta,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().background(MX.Night), content = content)
}

/** Flat dark card. [glow] (if given) becomes a 1.5dp accent outline, used to mark the selected item. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    glow: Color? = null,
    corner: Dp = 20.dp,
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
            .clip(shape)
            .background(MX.Surface)
            .then(if (glow != null) Modifier.border(1.5.dp, glow, shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(interaction, null, onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** Primary call-to-action: one solid pill, no glow, no gradient, gentle press scale. */
@Composable
fun GlowButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MX.Cyan,
    height: Dp = 56.dp,
    textColor: Color = Color.Black,
    leading: Ico? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.96f else 1f, spring(stiffness = 700f), label = "press")
    val fg = if (enabled) textColor else MX.TextLow
    Box(
        modifier
            .height(height)
            .scale(s)
            .clip(CircleShape)
            .background(if (enabled) color else MX.SurfaceHi)
            .clickable(interaction, null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                Icon(leading, fg, size = 16.dp)
                Box(Modifier.size(10.dp))
            }
            Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = fg)
        }
    }
}

/** Secondary button: dark filled pill with an optional coloured icon. */
@Composable
fun GhostButton(
    text: String,
    modifier: Modifier = Modifier,
    leading: Ico? = null,
    accent: Color = MX.Cyan,
    height: Dp = 50.dp,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.96f else 1f, spring(stiffness = 700f), label = "press")
    Box(
        modifier
            .height(height)
            .scale(s)
            .clip(CircleShape)
            .background(MX.SurfaceHi)
            .clickable(interaction, null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                Icon(leading, accent, size = 16.dp)
                Box(Modifier.size(8.dp))
            }
            Text(text, style = MaterialTheme.typography.titleMedium, color = MX.TextHi)
        }
    }
}

@Composable
fun Pill(text: String, color: Color = MX.Cyan, modifier: Modifier = Modifier, icon: Ico? = null) {
    Row(
        modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, color, size = 12.dp)
            Box(Modifier.width(5.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** Small icon + value line, like the complication rows on a watch face. */
@Composable
fun IconStat(ico: Ico, color: Color, value: String, modifier: Modifier = Modifier, label: String? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(ico, color, size = 15.dp)
        Box(Modifier.width(7.dp))
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MX.TextHi)
        if (label != null) {
            Box(Modifier.width(5.dp))
            Text(label, fontSize = 13.sp, color = MX.TextMid)
        }
    }
}

@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(44.dp).clip(CircleShape).background(MX.SurfaceHi).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(18.dp)) {
            scaleMirror { drawIco(Ico.CHEVRON, MX.TextHi) }
        }
    }
}

private inline fun DrawScope.scaleMirror(block: DrawScope.() -> Unit) {
    drawContext.transform.scale(-1f, 1f, center)
    block()
    drawContext.transform.scale(-1f, 1f, center)
}

/** Round icon button (settings, pause, etc). */
@Composable
fun RoundIconButton(ico: Ico, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = MX.TextHi, size: Dp = 44.dp) {
    Box(
        modifier.size(size).clip(CircleShape).background(MX.SurfaceHi).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(ico, tint, size = size * 0.42f) }
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

/** Segmented toggle. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, accent: Color = MX.Cyan) {
    Row(
        modifier.clip(CircleShape).background(MX.SurfaceHi).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(if (sel) accent else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (sel) Color.Black else MX.TextMid,
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
            .clip(RoundedCornerShape(16.dp))
            .background(MX.Surface)
            .padding(vertical = 12.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MX.TextLow)
    }
}

/** Star rating drawn with icons. */
@Composable
fun Stars(count: Int, max: Int = 5, color: Color = MX.Yellow, size: Dp = 16.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(max) { i ->
            Icon(if (i < count) Ico.STAR else Ico.STAR_OUTLINE, if (i < count) color else MX.TextLow, size = size)
        }
    }
}

/** Row of heart icons for lives. */
@Composable
fun Hearts(alive: Int, max: Int, color: Color = MX.Magenta, size: Dp = 14.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(max) { i -> Icon(Ico.HEART, if (i < alive) color else MX.SurfaceHi, size = size) }
    }
}

// ---------- watch-face arcs ----------

/** Animates 0 -> [target] once when first shown (and on change). */
@Composable
fun rememberSweep(target: Float, durationMs: Int = 1100, delayMs: Int = 0): Float {
    val a = remember { Animatable(0f) }
    LaunchedEffect(target) {
        a.animateTo(target.coerceIn(0f, 1f), tween(durationMs, delayMs, FastOutSlowInEasing))
    }
    return a.value
}

/**
 * Solid arc gauge like the activity arcs on a watch face: a dim track plus a bright fill with a
 * round end-knob. Angles in degrees, 0 = 3 o'clock, clockwise.
 */
fun DrawScope.arcGauge(
    color: Color,
    progress: Float,
    startAngle: Float,
    sweep: Float,
    strokeWidth: Float,
    inset: Float = strokeWidth / 2,
    knob: Boolean = true,
) {
    val tl = Offset(inset, inset)
    val sz = Size(size.width - inset * 2, size.height - inset * 2)
    drawArc(color.copy(alpha = 0.18f), startAngle, sweep, false, tl, sz, style = Stroke(strokeWidth, cap = StrokeCap.Round))
    if (progress <= 0f) return
    val sw = sweep * progress.coerceIn(0f, 1f)
    drawArc(color, startAngle, sw, false, tl, sz, style = Stroke(strokeWidth, cap = StrokeCap.Round))
    if (knob) {
        val a = Math.toRadians((startAngle + sw).toDouble())
        val r = sz.width / 2
        val c = Offset(center.x + (r * cos(a)).toFloat(), center.y + (r * sin(a)).toFloat())
        drawCircle(Color.Black, strokeWidth * 0.62f, c)
        drawCircle(Color.White, strokeWidth * 0.48f, c)
    }
}

/** Dashed tick arc like the battery/percentage dial on a watch face. */
fun DrawScope.tickArc(
    color: Color,
    progress: Float,
    startAngle: Float,
    sweep: Float,
    ticks: Int,
    tickLen: Float,
    tickWidth: Float,
    inset: Float = tickLen / 2 + 2f,
) {
    val r = size.minDimension / 2 - inset
    val lit = (ticks * progress.coerceIn(0f, 1f)).toInt()
    for (i in 0 until ticks) {
        val a = Math.toRadians((startAngle + sweep * i / (ticks - 1)).toDouble())
        val ca = cos(a).toFloat()
        val sa = sin(a).toFloat()
        val p0 = Offset(center.x + (r - tickLen / 2) * ca, center.y + (r - tickLen / 2) * sa)
        val p1 = Offset(center.x + (r + tickLen / 2) * ca, center.y + (r + tickLen / 2) * sa)
        drawLine(if (i < lit) color else color.copy(alpha = 0.16f), p0, p1, tickWidth, StrokeCap.Round)
    }
}

@Composable
fun VSpace(h: Dp) = Box(Modifier.height(h))

@Composable
fun FullScreenCenter(content: @Composable BoxScope.() -> Unit) =
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center, content = content)

/** Hairline separator. */
@Composable
fun Hairline(modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth().height(1.dp).background(MX.StrokeSoft))
