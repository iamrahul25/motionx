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
import com.nightwise.motionx.ui.components.*
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

    AuroraBackground {
        Row(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // ---------- left: tiers ----------
            Column(Modifier.width(280.dp).fillMaxHeight()) {
                ScreenHeader("Pose Match", "Copy the pose and hold it", onBack)
                VSpace(12.dp)
                Tier.entries.forEach { t ->
                    val tp = PoseLibrary.tier(t)
                    val stars = tp.sumOf { prefs.poseStars(it.id) }
                    val isLocked = t.ordinal > unlocked
                    val sel = t == selected
                    val c = tierColor(t)
                    Row(
                        Modifier.fillMaxWidth().weight(1f).padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp))
                            .background(MX.Surface)
                            .border(1.5.dp, if (sel) c else Color.Transparent, RoundedCornerShape(14.dp))
                            .clickable { selected = t }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background((if (isLocked) MX.TextLow else c).copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(if (isLocked) Ico.LOCK else t.icon(), if (isLocked) MX.TextLow else c, size = 15.dp) }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(t.label, style = MaterialTheme.typography.titleMedium, color = if (isLocked) MX.TextLow else MX.TextHi)
                            Text("${tp.size} poses · hold ${t.hold.toInt()}s", style = MaterialTheme.typography.labelSmall, color = MX.TextLow)
                        }
                        Icon(Ico.STAR, if (stars > 0) MX.Yellow else MX.TextLow, size = 12.dp)
                        Text(" $stars/${tp.size * 3}", style = MaterialTheme.typography.labelMedium, color = if (stars > 0) MX.TextHi else MX.TextLow)
                    }
                }
            }
            // ---------- right: poses of the selected tier ----------
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(selected.icon(), accent, size = 22.dp)
                            Box(Modifier.width(10.dp))
                            Text(selected.label, style = MaterialTheme.typography.headlineMedium, color = MX.TextHi)
                        }
                        Text(
                            if (locked) "Pass at least half of ${Tier.entries[selected.ordinal - 1].label} to unlock"
                            else "Hold each pose for ${selected.hold.toInt()} s. You get 20 s per pose.",
                            style = MaterialTheme.typography.bodyMedium, color = MX.TextMid,
                        )
                    }
                    GlowButton(
                        if (locked) "Locked" else "Play",
                        Modifier.width(160.dp), height = 52.dp, leading = if (locked) Ico.LOCK else Ico.PLAY,
                        color = accent, enabled = !locked,
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
                            Modifier.clip(RoundedCornerShape(16.dp))
                                .background(MX.Surface)
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
                            VSpace(4.dp)
                            Stars(stars, max = 3, size = 12.dp)
                        }
                    }
                }
            }
        }
    }
}

