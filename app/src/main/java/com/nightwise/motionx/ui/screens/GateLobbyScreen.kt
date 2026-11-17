package com.nightwise.motionx.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nightwise.motionx.data.Prefs
import com.nightwise.motionx.games.gate.GateDifficulty
import com.nightwise.motionx.games.gate.GateGame
import com.nightwise.motionx.games.gate.GateScene
import com.nightwise.motionx.games.pose.PoseLibrary
import com.nightwise.motionx.ui.components.AuroraBackground
import com.nightwise.motionx.ui.components.GlowButton
import com.nightwise.motionx.ui.components.ScreenHeader
import com.nightwise.motionx.ui.components.VSpace
import com.nightwise.motionx.ui.theme.MX

fun gateColor(d: GateDifficulty): Color = when (d) {
    GateDifficulty.EASY -> MX.Lime
    GateDifficulty.MEDIUM -> MX.Cyan
    GateDifficulty.HARD -> MX.Magenta
}

@Composable
fun GateLobbyScreen(prefs: Prefs, onBack: () -> Unit, onPlay: (GateDifficulty) -> Unit) {
    var diff by remember { mutableStateOf(GateDifficulty.entries[prefs.gateLastDifficulty.coerceIn(0, 2)]) }
    val accent = gateColor(diff)

    // Attract-mode preview: walls with different holes keep flying in.
    var t by remember { mutableFloatStateOf(0f) }
    var idx by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) withFrameNanos { now ->
            val dt = if (last == 0L) 0f else (now - last) / 1e9f
            last = now
            t += dt
            if (t > 3.2f) { t = 0f; idx++ }
        }
    }
    val previewPoses = remember(diff) { diff.poseIds.mapNotNull { PoseLibrary.byId(it) } }
    val previewPose = previewPoses[idx % previewPoses.size]
    val prog = (t / 2.6f).coerceAtMost(1f)
    val passed = t > 2.6f

    AuroraBackground(accentA = MX.Violet, accentB = accent, accentC = MX.Magenta) {
        Row(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(Modifier.weight(1.1f).fillMaxHeight()) {
                ScreenHeader("Pose Gate", "Match the hole, pass through the wall", onBack)
                VSpace(10.dp)
                Box(
                    Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(24.dp))
                        .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
                ) {
                    GateScene(
                        previewPose, prog,
                        if (passed) GateGame.Outcome.PASS else GateGame.Outcome.NONE, (t - 2.6f).coerceAtLeast(0f),
                        t + idx * 3.2f, diff.holeWidth, accent, prog > 0.75f, null, 1f, null,
                        Modifier.fillMaxSize(), showPlayer = false,
                    )
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                Text("HOW IT WORKS", style = MaterialTheme.typography.labelMedium, color = MX.TextLow)
                VSpace(6.dp)
                Text(
                    "A wall with a body-shaped hole flies at you. Copy the shape before it arrives and hold it while the rim glows green to pass through. Miss and the wall smashes — you lose a heart and the same shape comes back to retry.",
                    style = MaterialTheme.typography.bodyMedium, color = MX.TextMid,
                )
                VSpace(12.dp)
                GateDifficulty.entries.forEach { d ->
                    val sel = d == diff
                    val c = gateColor(d)
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (sel) c.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.04f))
                            .border(if (sel) 2.dp else 1.dp, if (sel) c else MX.StrokeSoft, RoundedCornerShape(16.dp))
                            .clickable { diff = d; idx = 0; t = 0f }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(d.emoji, fontSize = 22.sp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(d.label, style = MaterialTheme.typography.titleMedium, color = MX.TextHi)
                            Text(d.desc, style = MaterialTheme.typography.labelSmall, color = MX.TextLow)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("♥".repeat(d.lives), color = MX.Magenta, fontSize = 12.sp)
                            val best = prefs.gateBest(d.name)
                            Text(if (best > 0) "Best %,d".format(best) else "—", style = MaterialTheme.typography.labelMedium, color = MX.Amber)
                        }
                    }
                }
                VSpace(12.dp)
                GlowButton(
                    "Play ${diff.label}", Modifier.fillMaxWidth(), height = 60.dp, leading = "▶",
                    brush = Brush.linearGradient(listOf(accent, MX.Violet)), glow = accent,
                ) {
                    prefs.gateLastDifficulty = diff.ordinal
                    onPlay(diff)
                }
                VSpace(10.dp)
            }
        }
    }
}
