package com.nightwise.motionx.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nightwise.motionx.data.Prefs
import com.nightwise.motionx.games.pose.PoseLibrary
import com.nightwise.motionx.games.pose.PoseMannequin
import com.nightwise.motionx.games.pose.Tier
import com.nightwise.motionx.ui.components.AuroraBackground
import com.nightwise.motionx.ui.components.GlowButton
import com.nightwise.motionx.ui.components.ScreenHeader
import com.nightwise.motionx.ui.components.VSpace
import com.nightwise.motionx.ui.theme.MX

fun tierColor(t: Tier): Color = when (t) {
    Tier.EASY -> MX.Lime
    Tier.MEDIUM -> MX.Cyan
    Tier.HARD -> MX.Amber
    Tier.VERY_HARD -> MX.Magenta
    Tier.EXTREME -> MX.Violet
}

@Composable
fun PoseMapScreen(prefs: Prefs, onBack: () -> Unit, onPlay: (tier: Tier, start: Int) -> Unit) {
    val unlocked = remember { prefs.poseUnlockedTier }
    var selected by remember { mutableStateOf(Tier.entries[unlocked.coerceIn(0, Tier.entries.size - 1)]) }
    val poses = PoseLibrary.tier(selected)
    val locked = selected.ordinal > unlocked
    val accent = tierColor(selected)

    AuroraBackground(accentA = MX.Violet, accentB = accent, accentC = MX.Magenta) {
        Row(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // ---------- left: tiers ----------
            Column(Modifier.width(280.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                ScreenHeader("Pose Match", "Copy · hold · level up", onBack)
                VSpace(12.dp)
                Tier.entries.forEach { t ->
                    val tp = PoseLibrary.tier(t)
                    val stars = tp.sumOf { prefs.poseStars(it.id) }
                    val isLocked = t.ordinal > unlocked
                    val sel = t == selected
                    val c = tierColor(t)
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (sel) c.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.04f))
                            .border(if (sel) 2.dp else 1.dp, if (sel) c else MX.StrokeSoft, RoundedCornerShape(16.dp))
                            .clickable { selected = t }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (isLocked) "🔒" else t.emoji, fontSize = 22.sp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(t.label, style = MaterialTheme.typography.titleMedium, color = if (isLocked) MX.TextLow else MX.TextHi)
                            Text("${tp.size} poses · hold ${t.hold.toInt()}s", style = MaterialTheme.typography.labelSmall, color = MX.TextLow)
                        }
                        Text("★ $stars/${tp.size * 3}", style = MaterialTheme.typography.labelMedium, color = if (stars > 0) MX.Amber else MX.TextLow)
                    }
                }
            }
            // ---------- right: poses of the selected tier ----------
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${selected.emoji}  ${selected.label}", style = MaterialTheme.typography.headlineMedium, color = accent)
                        Text(
                            if (locked) "Pass at least half of ${Tier.entries[selected.ordinal - 1].label} to unlock"
                            else "Poses play one after another · hold each for ${selected.hold.toInt()} s · 20 s per pose",
                            style = MaterialTheme.typography.bodyMedium, color = MX.TextMid,
                        )
                    }
                    GlowButton(
                        if (locked) "Locked" else "Play",
                        Modifier.width(170.dp), height = 54.dp, leading = if (locked) "🔒" else "▶",
                        brush = Brush.linearGradient(listOf(accent, MX.Violet)), glow = accent, enabled = !locked,
                    ) { onPlay(selected, 0) }
                }
                VSpace(12.dp)
                LazyVerticalGrid(
                    GridCells.Adaptive(120.dp),
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(poses, key = { _, p -> p.id }) { i, p ->
                        val stars = prefs.poseStars(p.id)
                        Column(
                            Modifier.clip(RoundedCornerShape(18.dp))
                                .background(Brush.verticalGradient(listOf(Color(0xFF1A2050).copy(alpha = 0.8f), Color(0xFF0F1330).copy(alpha = 0.9f))))
                                .border(1.dp, if (stars > 0) accent.copy(alpha = 0.5f) else MX.StrokeSoft, RoundedCornerShape(18.dp))
                                .clickable(enabled = !locked) { onPlay(selected, i) }
                                .alpha(if (locked) 0.45f else 1f)
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                                PoseMannequin(p, Modifier.fillMaxSize(), color = accent, animate = false)
                                Text("${i + 1}", Modifier.align(Alignment.TopStart), style = MaterialTheme.typography.labelMedium, color = MX.TextLow)
                            }
                            Text(p.name, style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp), color = MX.TextHi, maxLines = 1)
                            Row {
                                repeat(3) { k ->
                                    Text(if (k < stars) "★" else "☆", color = if (k < stars) MX.Amber else MX.TextLow, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

