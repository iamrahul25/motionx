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
import com.nightwise.motionx.ui.components.AuroraBackground
import com.nightwise.motionx.ui.components.GhostButton
import com.nightwise.motionx.ui.components.GlassCard
import com.nightwise.motionx.ui.components.GlowButton
import com.nightwise.motionx.ui.components.Pill
import com.nightwise.motionx.ui.components.ScreenHeader
import com.nightwise.motionx.ui.components.Segmented
import com.nightwise.motionx.ui.components.StatTile
import com.nightwise.motionx.ui.components.Stars
import com.nightwise.motionx.ui.components.TrackArt
import com.nightwise.motionx.ui.components.VSpace
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
    AuroraBackground(accentA = MX.Magenta, accentB = MX.Cyan, accentC = MX.Violet) {
        Row(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // ---------- left: header + current setup ----------
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                ScreenHeader("Neon Dash", "Hop, jump and duck with your whole body", onBack)
                VSpace(14.dp)
                GlassCard(glow = MX.Magenta, padding = 0.dp, corner = 26.dp, onClick = onDifficulty) {
                    Box(Modifier.fillMaxWidth().height(110.dp)) {
                        TrackArt(Modifier.fillMaxSize())
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE0F1330)))))
                        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 18.dp, vertical = 8.dp)) {
                            Text("BEST RUN", style = MaterialTheme.typography.labelSmall, color = MX.TextMid)
                            Text(
                                if (totals.bestScore > 0) "%,d".format(totals.bestScore) else "Set your first record!",
                                style = if (totals.bestScore > 0) MaterialTheme.typography.displayMedium else MaterialTheme.typography.headlineMedium,
                                color = MX.Amber,
                            )
                        }
                    }
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${config.emoji}  ${config.preset}", style = MaterialTheme.typography.titleLarge, color = MX.TextHi, modifier = Modifier.weight(1f))
                            Stars(config.stars)
                        }
                        VSpace(4.dp)
                        Text(config.summary, style = MaterialTheme.typography.bodyMedium, color = MX.TextMid)
                        VSpace(8.dp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Pill("Score ×${config.scoreMultiplier}", MX.Lime)
                            Box(Modifier.weight(1f))
                            Text("Change ›", style = MaterialTheme.typography.titleMedium, color = MX.Cyan)
                        }
                    }
                }
            }
            // ---------- right: actions + totals ----------
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                VSpace(4.dp)
                GlowButton("Play", Modifier.fillMaxWidth(), height = 64.dp, leading = "▶", onClick = onPlay)
                VSpace(12.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GhostButton("Difficulty", Modifier.weight(1f), leading = "🎚", onClick = onDifficulty)
                    GhostButton("How to play", Modifier.weight(1f), leading = "❔", onClick = onHowTo)
                }
                VSpace(12.dp)
                GhostButton("Training run", Modifier.fillMaxWidth(), leading = "🎯", accent = MX.Lime, onClick = onTraining)
                VSpace(4.dp)
                Text(
                    "Slow constant speed, unlimited lives, no score — perfect to learn the moves.",
                    style = MaterialTheme.typography.bodyMedium, color = MX.TextLow,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
                VSpace(16.dp)
                Text("YOUR TOTALS", style = MaterialTheme.typography.labelMedium, color = MX.TextLow)
                VSpace(8.dp)
                if (totals.runs == 0) {
                    Text("Your totals will show up here after your first run.", color = MX.TextMid, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("${totals.runs}", "Runs", MX.Cyan, Modifier.weight(1f))
                        StatTile("%.1f km".format(totals.distance / 1000f), "Distance", MX.Violet, Modifier.weight(1f))
                        StatTile("${totals.seconds / 60} min", "Active", MX.Lime, Modifier.weight(1f))
                    }
                    VSpace(8.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("${totals.jumps}", "Jumps", MX.Amber, Modifier.weight(1f))
                        StatTile("${totals.ducks}", "Ducks", MX.Sky, Modifier.weight(1f))
                        StatTile("${totals.hops}", "Hops", MX.Magenta, Modifier.weight(1f))
                    }
                }
                VSpace(12.dp)
            }
        }
    }
}

// ============================================================ difficulty

@Composable
fun RunnerDifficultyScreen(initial: RunnerConfig, onBack: () -> Unit, onSave: (RunnerConfig) -> Unit) {
    var c by remember { mutableStateOf(initial) }
    AuroraBackground(accentA = MX.Violet, accentB = MX.Lime, accentC = MX.Cyan) {
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
                                Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                                    .background(if (sel) MX.Cyan.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f))
                                    .border(if (sel) 2.dp else 1.dp, if (sel) MX.Cyan else MX.StrokeSoft, RoundedCornerShape(18.dp))
                                    .clickable { c = p.copy(assist = c.assist) }
                                    .padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(p.emoji, fontSize = 24.sp)
                                Text(p.preset, style = MaterialTheme.typography.titleMedium, color = if (sel) MX.Cyan else MX.TextHi, maxLines = 1)
                                Text("Speed ${p.startSpeed}→${p.topSpeed}", style = MaterialTheme.typography.labelSmall, color = MX.TextLow)
                            }
                        }
                    }
                    VSpace(14.dp)
                    GlassCard(glow = MX.Cyan, padding = 14.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SpeedDial(c.startSpeed, if (c.mode == SpeedMode.INCREASING) c.topSpeed else null, Modifier.size(120.dp))
                            Box(Modifier.size(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Start speed", style = MaterialTheme.typography.titleMedium, color = MX.TextMid)
                                Stepper(c.startSpeed, 1, 10) { c = c.copy(startSpeed = it, maxSpeed = maxOf(it, c.maxSpeed), preset = "Custom", emoji = "🎛") }
                                if (c.mode == SpeedMode.INCREASING) {
                                    VSpace(6.dp)
                                    Text("Top speed", style = MaterialTheme.typography.titleMedium, color = MX.TextMid)
                                    Stepper(c.maxSpeed, c.startSpeed, 10) { c = c.copy(maxSpeed = it, preset = "Custom", emoji = "🎛") }
                                }
                            }
                        }
                        VSpace(12.dp)
                        Segmented(
                            SpeedMode.entries.map { it.label }, c.mode.ordinal,
                            { c = c.copy(mode = SpeedMode.entries[it], preset = "Custom", emoji = "🎛") }, Modifier.fillMaxWidth(),
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
                    GlassCard(glow = if (c.assist) MX.Lime else null, padding = 14.dp, corner = 18.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🔊", fontSize = 24.sp)
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
                            ChoiceChip(if (l == 0) "∞" else "♥ $l", c.lives == l, MX.Magenta, Modifier.weight(1f)) {
                                c = c.copy(lives = l, preset = "Custom", emoji = "🎛")
                            }
                        }
                    }
                    VSpace(12.dp)
                    Text("MOVES", style = MaterialTheme.typography.labelMedium, color = MX.TextLow)
                    VSpace(8.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Moves.entries.forEach { m ->
                            ChoiceChip(m.label, c.moves == m, MX.Lime, Modifier.fillMaxWidth()) {
                                c = c.copy(moves = m, preset = "Custom", emoji = "🎛")
                            }
                        }
                    }
                    VSpace(12.dp)
                    Text(c.summary, style = MaterialTheme.typography.bodyMedium, color = MX.TextMid)
                    VSpace(6.dp)
                    Text(
                        "👟 Shoes on · 🧹 Floor clear · 🛑 Stop if dizzy",
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
            drawArc(Color.White.copy(alpha = 0.08f), 135f, 270f, false, tl, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            if (top != null && top > value) {
                drawArc(MX.Magenta.copy(alpha = 0.45f), 135f, 270f * top / 10f, false, tl, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            }
            drawArc(
                Brush.sweepGradient(listOf(MX.Cyan, MX.Violet, MX.Cyan)), 135f, 270f * value / 10f, false, tl, arcSize,
                style = Stroke(sw, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$value", fontSize = 46.sp, fontWeight = FontWeight.Black, color = MX.TextHi)
            Text(if (top != null && top > value) "→ $top" else "SPEED", style = MaterialTheme.typography.labelMedium, color = if (top != null && top > value) MX.Magenta else MX.Cyan)
        }
    }
}

@Composable
private fun Stepper(value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepBtn("−", value > min) { onChange(value - 1) }
        Text("$value", Modifier.padding(horizontal = 14.dp), style = MaterialTheme.typography.headlineMedium, color = MX.TextHi)
        StepBtn("+", value < max) { onChange(value + 1) }
    }
}

@Composable
private fun StepBtn(s: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape)
            .background(if (enabled) MX.Cyan.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.04f))
            .border(1.dp, if (enabled) MX.Cyan.copy(alpha = 0.5f) else MX.StrokeSoft, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(s, fontSize = 22.sp, color = if (enabled) MX.Cyan else MX.TextLow, fontWeight = FontWeight.Bold) }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(14.dp))
            .background(if (selected) accent.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f))
            .border(if (selected) 2.dp else 1.dp, if (selected) accent else MX.StrokeSoft, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = MaterialTheme.typography.titleMedium, color = if (selected) accent else MX.TextMid) }
}
