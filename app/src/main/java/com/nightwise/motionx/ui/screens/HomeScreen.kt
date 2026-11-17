package com.nightwise.motionx.ui.screens

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nightwise.motionx.audio.Sfx
import com.nightwise.motionx.data.Prefs
import com.nightwise.motionx.games.pose.PoseLibrary
import com.nightwise.motionx.ui.components.AuroraBackground
import com.nightwise.motionx.ui.components.GlassCard
import com.nightwise.motionx.ui.components.Pill
import com.nightwise.motionx.ui.components.Segmented
import com.nightwise.motionx.ui.components.TrackArt
import com.nightwise.motionx.ui.components.VSpace
import com.nightwise.motionx.ui.theme.MX

@Composable
fun HomeScreen(prefs: Prefs, sfx: Sfx, onOpenRunner: () -> Unit, onOpenPose: () -> Unit) {
    val totals = remember { prefs.runnerTotals }
    var settings by remember { mutableStateOf(false) }
    AuroraBackground {
        Row(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // ---------- left: brand + featured game ----------
            Column(Modifier.weight(1.15f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                Text(
                    "MotionX",
                    style = MaterialTheme.typography.displayLarge.merge(
                        TextStyle(brush = Brush.linearGradient(listOf(MX.Cyan, MX.Violet, MX.Magenta))),
                    ),
                )
                Text("Your body is the controller", style = MaterialTheme.typography.bodyLarge, color = MX.TextMid)
                VSpace(14.dp)
                GlassCard(glow = MX.Cyan, padding = 0.dp, corner = 26.dp, onClick = onOpenRunner) {
                    Row(Modifier.fillMaxWidth().height(200.dp)) {
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            TrackArt(Modifier.fillMaxSize())
                            Row(Modifier.padding(12.dp)) {
                                Pill("NEW", MX.Lime)
                                Box(Modifier.size(6.dp))
                                Pill("3D", MX.Cyan)
                            }
                        }
                        Column(
                            Modifier.weight(1.05f).fillMaxHeight().padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text("Neon Dash", style = MaterialTheme.typography.displayMedium.copy(fontSize = 30.sp), color = MX.TextHi)
                                Text("Hop, jump & duck past obstacles", style = MaterialTheme.typography.titleMedium, color = MX.Cyan)
                                VSpace(6.dp)
                                Text(
                                    "3-lane endless runner steered by your whole body, with live skeleton tracking.",
                                    style = MaterialTheme.typography.bodyMedium, color = MX.TextMid, maxLines = 3,
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    if (totals.bestScore > 0) Pill("Best %,d".format(totals.bestScore), MX.Amber)
                                    else Pill("Easy → Hard", MX.Magenta)
                                }
                                Box(
                                    Modifier.size(52.dp).clip(CircleShape).background(MX.PrimaryGradient),
                                    contentAlignment = Alignment.Center,
                                ) { Text("▶", fontSize = 20.sp, color = MX.Night, fontWeight = FontWeight.Black) }
                            }
                        }
                    }
                }
            }
            // ---------- right: activity, coming soon, tip ----------
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.05f))
                            .border(1.dp, MX.StrokeSoft, RoundedCornerShape(20.dp)).padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Metric("${totals.runs}", "Runs", MX.Cyan)
                        Metric("${totals.seconds / 60}", "Min", MX.Lime)
                        Metric("${totals.jumps + totals.ducks + totals.hops}", "Moves", MX.Magenta)
                        Metric("${totals.coins}", "Coins", MX.Amber)
                    }
                    Box(Modifier.size(12.dp))
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).background(MX.Glass.copy(alpha = 0.08f))
                            .border(1.dp, MX.Stroke, CircleShape).clickable { settings = true },
                        contentAlignment = Alignment.Center,
                    ) { Text("⚙", fontSize = 22.sp, color = MX.TextHi) }
                }
                VSpace(16.dp)
                Text("MORE GAMES", style = MaterialTheme.typography.labelMedium, color = MX.TextLow)
                VSpace(8.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SoonCard("🧘", "Pose Match", "Copy the pose, hold it, level up", MX.Violet, Modifier.weight(1f), pill = "▶ ${PoseLibrary.ALL.size} poses", onClick = onOpenPose)
                    SoonCard("🫧", "Bubble Pop", "Pop bubbles with hands & feet", MX.Magenta, Modifier.weight(1f))
                }
                VSpace(12.dp)
                GlassCard(padding = 12.dp, corner = 18.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("💡", fontSize = 20.sp)
                        Box(Modifier.size(10.dp))
                        Text(
                            "Prop your phone sideways at waist height, step back 2–3 m and keep the floor clear.",
                            style = MaterialTheme.typography.bodyMedium, color = MX.TextMid,
                        )
                    }
                }
            }
        }
        if (settings) SettingsOverlay(prefs, sfx) { settings = false }
    }
}

@Composable
private fun Metric(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Black)
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MX.TextLow)
    }
}

@Composable
private fun SoonCard(
    emoji: String,
    title: String,
    sub: String,
    accent: Color,
    modifier: Modifier,
    pill: String = "🔒 Soon",
    onClick: (() -> Unit)? = null,
) {
    GlassCard(modifier, padding = 16.dp, corner = 22.dp, glow = if (onClick != null) accent else null, onClick = onClick) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.45f), accent.copy(alpha = 0.12f)))),
            contentAlignment = Alignment.Center,
        ) { Text(emoji, fontSize = 26.sp) }
        VSpace(12.dp)
        Text(title, style = MaterialTheme.typography.titleLarge, color = MX.TextHi)
        Text(sub, style = MaterialTheme.typography.bodyMedium, color = MX.TextMid, minLines = 2, maxLines = 2)
        VSpace(10.dp)
        Pill(pill, accent)
    }
}

@Composable
private fun SettingsOverlay(prefs: Prefs, sfx: Sfx, onClose: () -> Unit) {
    var sound by remember { mutableStateOf(prefs.sound) }
    var haptics by remember { mutableStateOf(prefs.haptics) }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        GlassCard(Modifier.widthIn(max = 460.dp).padding(12.dp).verticalScroll(rememberScrollState()).clickable(enabled = false) {}, glow = MX.Violet, padding = 18.dp) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, color = MX.TextHi)
            VSpace(16.dp)
            Text("Sound effects", style = MaterialTheme.typography.titleMedium, color = MX.TextMid)
            VSpace(8.dp)
            Segmented(listOf("On", "Off"), if (sound) 0 else 1, {
                sound = it == 0; prefs.sound = sound; sfx.soundOn = sound
                if (sound) sfx.play(Sfx.S.COIN)
            }, Modifier.fillMaxWidth())
            VSpace(14.dp)
            Text("Vibration", style = MaterialTheme.typography.titleMedium, color = MX.TextMid)
            VSpace(8.dp)
            Segmented(listOf("On", "Off"), if (haptics) 0 else 1, {
                haptics = it == 0; prefs.haptics = haptics; sfx.hapticsOn = haptics
                if (haptics) sfx.buzz(40)
            }, Modifier.fillMaxWidth())
            VSpace(16.dp)
            Text(
                "Camera frames are processed on-device only. Nothing is recorded or uploaded.",
                style = MaterialTheme.typography.bodyMedium, color = MX.TextLow,
            )
            VSpace(14.dp)
            Text(
                "Done", style = MaterialTheme.typography.titleMedium, color = MX.Cyan,
                modifier = Modifier.align(Alignment.End).clip(CircleShape).clickable(onClick = onClose).padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}
