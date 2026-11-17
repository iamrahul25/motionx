package com.nightwise.motionx.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nightwise.motionx.data.Prefs
import com.nightwise.motionx.games.runner.Moves
import com.nightwise.motionx.games.runner.RunnerConfig
import com.nightwise.motionx.games.runner.SpeedMode
import com.nightwise.motionx.ui.components.*
import com.nightwise.motionx.ui.theme.MX

@Composable
fun RunnerLobbyScreen(
    prefs: Prefs,
    config: RunnerConfig,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onTraining: () -> Unit,
    onDifficulty: () -> Unit,
    onHowTo: () -> Unit,
) {
    val totals = remember { prefs.runnerTotals }
    AuroraBackground {
        Row(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            // ---------- left: header + hero ----------
            Column(Modifier.weight(1f).fillMaxHeight()) {
                ScreenHeader("Neon Dash", "Hop, jump and duck with your whole body", onBack)
                VSpace(14.dp)
                Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(20.dp)).background(MX.Surface)) {
                    TrackArt(Modifier.fillMaxSize())
                    Column(Modifier.align(Alignment.TopStart).padding(18.dp)) {
                        Text("BEST RUN", style = MaterialTheme.typography.labelMedium, color = MX.Cyan)
                        Text(
                            if (totals.bestScore > 0) "%,d".format(totals.bestScore) else "--",
                            fontSize = 44.sp, fontWeight = FontWeight.Bold, color = MX.TextHi, letterSpacing = (-1).sp,
                        )
                    }
                }
                VSpace(12.dp)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MX.Surface)
                        .clickable(onClick = onDifficulty).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(config.icon(), MX.Cyan, size = 20.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(config.preset, style = MaterialTheme.typography.titleMedium, color = MX.TextHi)
                            Box(Modifier.width(8.dp))
                            Stars(config.stars, size = 12.dp)
                        }
                        Text(config.summary, style = MaterialTheme.typography.labelSmall, color = MX.TextLow, maxLines = 1)
                    }
                    Icon(Ico.CHEVRON, MX.TextLow, size = 16.dp)
                }
            }
            // ---------- right: actions + totals ----------
            Column(Modifier.weight(1f).fillMaxHeight()) {
                GlowButton("Play", Modifier.fillMaxWidth(), height = 58.dp, leading = Ico.PLAY, onClick = onPlay)
                VSpace(10.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton("Difficulty", Modifier.weight(1f), leading = Ico.SLIDERS, onClick = onDifficulty)
                    GhostButton("How to play", Modifier.weight(1f), leading = Ico.HELP, accent = MX.Violet, onClick = onHowTo)
                }
                VSpace(10.dp)
                GhostButton("Training run", Modifier.fillMaxWidth(), leading = Ico.TARGET, accent = MX.Lime, onClick = onTraining)
                VSpace(16.dp)
                Text("YOUR TOTALS", style = MaterialTheme.typography.labelMedium, color = MX.TextLow)
                VSpace(10.dp)
                Column(
                    Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp)).background(MX.Surface).padding(16.dp),
                    verticalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Row {
                        IconStat(Ico.RUN, MX.Lime, "${totals.runs}", Modifier.weight(1f), "runs")
                        IconStat(Ico.FLAG, MX.Violet, "%.1f".format(totals.distance / 1000f), Modifier.weight(1f), "km")
                        IconStat(Ico.CLOCK, MX.Cyan, "${totals.seconds / 60}", Modifier.weight(1f), "min")
                    }
                    Row {
                        IconStat(Ico.UP, MX.Amber, "${totals.jumps}", Modifier.weight(1f), "jumps")
                        IconStat(Ico.DOWN, MX.Sky, "${totals.ducks}", Modifier.weight(1f), "ducks")
                        IconStat(Ico.RIGHT, MX.Magenta, "${totals.hops}", Modifier.weight(1f), "hops")
                    }
                }
            }
        }
    }
}

// ============================================================ difficulty

@Composable
fun RunnerDifficultyScreen(initial: RunnerConfig, onBack: () -> Unit, onSave: (RunnerConfig) -> Unit) {
    var c by remember { mutableStateOf(initial) }
    AuroraBackground {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 10.dp)) {
            ScreenHeader("Difficulty", "Pick a level or tune it yourself", onBack) {
                Column(horizontalAlignment = Alignment.End) {
                    Stars(c.stars)
                    Text("Score ×${c.scoreMultiplier}", style = MaterialTheme.typography.labelMedium, color = MX.Lime)
                }
                Box(Modifier.size(14.dp))
                GlowButton("Save", Modifier.width(140.dp), height = 50.dp, onClick = { onSave(c) })
            }
            VSpace(12.dp)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                // ---------- left: level + speed ----------
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    Text("LEVEL", style = MaterialTheme.typography.labelMedium, color = MX.TextLow)
                    VSpace(8.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RunnerConfig.PRESETS.forEach { p ->
                            val sel = c.preset == p.preset
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                                    .background(MX.Surface)
                                    .border(1.5.dp, if (sel) MX.Cyan else Color.Transparent, RoundedCornerShape(16.dp))
                                    .clickable { c = p.copy(assist = c.assist) }
                                    .padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(p.icon(), if (sel) MX.Cyan else MX.TextMid, size = 22.dp)
                                VSpace(4.dp)
                                Text(p.preset, style = MaterialTheme.typography.titleMedium, color = if (sel) MX.Cyan else MX.TextHi, maxLines = 1)
                                Text("Speed ${p.startSpeed} to ${p.topSpeed}", style = MaterialTheme.typography.labelSmall, color = MX.TextLow)
                            }
                        }
                    }
                    VSpace(14.dp)
                    GlassCard(padding = 14.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SpeedDial(c.startSpeed, if (c.mode == SpeedMode.INCREASING) c.topSpeed else null, Modifier.size(120.dp))
                            Box(Modifier.size(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Start speed", style = MaterialTheme.typography.titleMedium, color = MX.TextMid)
                                Stepper(c.startSpeed, 1, 10) { c = c.copy(startSpeed = it, maxSpeed = maxOf(it, c.maxSpeed), preset = "Custom") }
                                if (c.mode == SpeedMode.INCREASING) {
                                    VSpace(6.dp)
                                    Text("Top speed", style = MaterialTheme.typography.titleMedium, color = MX.TextMid)
                                    Stepper(c.maxSpeed, c.startSpeed, 10) { c = c.copy(maxSpeed = it, preset = "Custom") }
                                }
                            }
                        }
                        VSpace(12.dp)
                        Segmented(
                            SpeedMode.entries.map { it.label }, c.mode.ordinal,
                            { c = c.copy(mode = SpeedMode.entries[it], preset = "Custom") }, Modifier.fillMaxWidth(),
                        )
                        VSpace(6.dp)
                        Text(
                            if (c.mode == SpeedMode.CONSTANT) "Same pace the whole run — great for practice and steady cardio."
                            else "Gets faster over about 3 minutes until it reaches top speed.",
                            style = MaterialTheme.typography.bodyMedium, color = MX.TextLow,
                        )
                    }
                    VSpace(12.dp)
                }
                // ---------- right: lives, moves, assist ----------
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    GlassCard(padding = 14.dp, corner = 16.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Ico.SOUND, MX.Lime, size = 22.dp)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text("Voice assist", style = MaterialTheme.typography.titleMedium, color = MX.TextHi)
                                Text(
                                    "Speaker calls out \"Jump\", \"Duck\", \"Move left/right\" before every obstacle.",
                                    style = MaterialTheme.typography.bodyMedium, color = MX.TextMid,
                                )
                            }
                            Segmented(listOf("On", "Off"), if (c.assist) 0 else 1, { c = c.copy(assist = it == 0) }, Modifier.width(130.dp), accent = MX.Lime)
                        }
                    }
                    VSpace(12.dp)
                    Text("LIVES", style = MaterialTheme.typography.labelMedium, color = MX.TextLow)
                    VSpace(8.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 2, 3, 5, 0).forEach { l ->
                            ChoiceChip(if (l == 0) "Any" else "$l", c.lives == l, MX.Magenta, Modifier.weight(1f), if (l == 0) Ico.INFINITY else Ico.HEART) {
                                c = c.copy(lives = l, preset = "Custom")
                            }
                        }
                    }
                    VSpace(12.dp)
                    Text("MOVES", style = MaterialTheme.typography.labelMedium, color = MX.TextLow)
                    VSpace(8.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Moves.entries.forEach { m ->
                            ChoiceChip(m.label, c.moves == m, MX.Lime, Modifier.fillMaxWidth()) {
                                c = c.copy(moves = m, preset = "Custom")
                            }
                        }
                    }
                    VSpace(12.dp)
                    Text(c.summary, style = MaterialTheme.typography.bodyMedium, color = MX.TextMid)
                    VSpace(6.dp)
                    Text(
                        "Wear shoes, clear the floor, and stop if you feel dizzy.",
                        style = MaterialTheme.typography.bodyMedium, color = MX.TextLow,
                    )
                    VSpace(12.dp)
                }
            }
        }
    }
}

@Composable
fun SpeedDial(value: Int, top: Int?, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = size.minDimension * 0.08f
            val inset = sw / 2 + 2f
            val arcSize = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
            val tl = Offset(inset, inset)
            drawArc(MX.Cyan.copy(alpha = 0.16f), 135f, 270f, false, tl, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            if (top != null && top > value) {
                drawArc(MX.Magenta, 135f, 270f * top / 10f, false, tl, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            }
            drawArc(MX.Cyan, 135f, 270f * value / 10f, false, tl, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$value", fontSize = 46.sp, fontWeight = FontWeight.Bold, color = MX.TextHi)
            Text(if (top != null && top > value) "TO $top" else "SPEED", style = MaterialTheme.typography.labelMedium, color = if (top != null && top > value) MX.Magenta else MX.Cyan)
        }
    }
}

@Composable
private fun Stepper(value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepBtn(Ico.MINUS, value > min) { onChange(value - 1) }
        Text("$value", Modifier.padding(horizontal = 14.dp), style = MaterialTheme.typography.headlineMedium, color = MX.TextHi)
        StepBtn(Ico.PLUS, value < max) { onChange(value + 1) }
    }
}

@Composable
private fun StepBtn(s: Ico, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape)
            .background(MX.SurfaceHi)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(s, if (enabled) MX.Cyan else MX.TextLow, size = 16.dp) }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, accent: Color, modifier: Modifier, icon: Ico? = null, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(14.dp))
            .background(MX.Surface)
            .border(1.5.dp, if (selected) accent else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, if (selected) accent else MX.TextLow, size = 13.dp)
            Box(Modifier.width(5.dp))
        }
        Text(label, style = MaterialTheme.typography.titleMedium, color = if (selected) MX.TextHi else MX.TextMid, maxLines = 1)
    }
}
