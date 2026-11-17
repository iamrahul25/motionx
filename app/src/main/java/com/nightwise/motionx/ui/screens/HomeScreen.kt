package com.nightwise.motionx.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nightwise.motionx.audio.Sfx
import com.nightwise.motionx.data.Prefs
import com.nightwise.motionx.games.pose.PoseLibrary
import com.nightwise.motionx.ui.components.Ico
import com.nightwise.motionx.ui.components.Icon
import com.nightwise.motionx.ui.components.RoundIconButton
import com.nightwise.motionx.ui.components.Segmented
import com.nightwise.motionx.ui.components.rememberSweep
import com.nightwise.motionx.ui.theme.MX
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(prefs: Prefs, sfx: Sfx, onOpenRunner: () -> Unit, onOpenPose: () -> Unit, onOpenGate: () -> Unit) {
    val totals = remember { prefs.runnerTotals }
    var settings by remember { mutableStateOf(false) }
    val moves = totals.jumps + totals.ducks + totals.hops

    Box(Modifier.fillMaxSize().background(MX.Night)) {
        Row(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            // ---------- left: brand + stats ----------
            Column(Modifier.width(230.dp).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                        Text("Motion", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = MX.TextHi, letterSpacing = (-0.8).sp)
                        Text("X", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = MX.Cyan, letterSpacing = (-0.8).sp)
                    }
                    RoundIconButton(Ico.GEAR, { settings = true })
                }
                Text("Your body is the controller", fontSize = 13.sp, color = MX.TextMid)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    val count = rememberCount(moves)
                    StatLine(0, Ico.BOLT, MX.Cyan, "$count", "moves")
                    StatLine(1, Ico.RUN, MX.Lime, "${totals.runs}", "runs")
                    StatLine(2, Ico.COIN, MX.Yellow, "${totals.coins}", "coins")
                    StatLine(3, Ico.HEART, MX.Magenta, if (totals.bestScore > 0) "%,d".format(totals.bestScore) else "0", "best score")
                }
                Text("Phone sideways at waist height, stand 2–3 m back.", fontSize = 12.sp, lineHeight = 16.sp, color = MX.TextLow)
            }

            // ---------- right: games ----------
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GameRow(0, Ico.RUN, MX.Cyan, "Neon Dash", "Endless runner. Hop lanes, jump and duck.",
                        if (totals.bestScore > 0) "Best %,d".format(totals.bestScore) else "3 levels", Modifier.weight(1f), onOpenRunner)
                    GameRow(1, Ico.POSE, MX.Lime, "Pose Match", "Copy the pose and hold it steady.",
                        "${PoseLibrary.ALL.size} poses", Modifier.weight(1f), onOpenPose)
                    GameRow(2, Ico.WALL, MX.Amber, "Pose Gate", "Fit your body through the hole in the wall.",
                        "New", Modifier.weight(1f), onOpenGate)
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Ico.BUBBLES, MX.Violet, size = 14.dp)
                    Text("  Bubble Pop is coming soon", fontSize = 12.sp, color = MX.TextLow)
                }
            }
        }

        if (settings) SettingsOverlay(prefs, sfx) { settings = false }
    }
}

// ---------------------------------------------------------------- stats

@Composable
private fun StatLine(index: Int, ico: Ico, color: Color, value: String, label: String) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(80L * index)
        enter.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
    }
    Row(
        Modifier.padding(vertical = 7.dp).alpha(enter.value).offset(x = (12 * (1f - enter.value)).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(ico, color, size = 20.dp)
        Spacer(Modifier.width(12.dp))
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MX.TextHi)
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 15.sp, color = MX.TextMid)
    }
}

/** Counts up from 0 to [target] when first shown. */
@Composable
private fun rememberCount(target: Int): Int {
    val a = remember { Animatable(0f) }
    LaunchedEffect(target) { a.animateTo(target.toFloat(), tween(1100, 200, FastOutSlowInEasing)) }
    return a.value.toInt()
}

// ---------------------------------------------------------------- game rows

@Composable
private fun GameRow(
    index: Int,
    ico: Ico,
    color: Color,
    title: String,
    sub: String,
    meta: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    // staggered entrance
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(120L * index)
        enter.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
    }
    val ring = rememberSweep(1f, 900, 250 + 120 * index)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier.fillMaxWidth()
            .alpha(enter.value)
            .scale(if (pressed) 0.98f else 0.96f + 0.04f * enter.value)
            .clip(shape).background(MX.Surface)
            .clickable(interaction, null, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // complication-style ring with icon
        Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val sw = 3.dp.toPx()
                drawCircle(color.copy(alpha = 0.18f), radius = size.minDimension / 2 - sw / 2, style = Stroke(sw))
                drawArc(color, -90f, 360f * ring, false, Offset(sw / 2, sw / 2), Size(size.width - sw, size.height - sw), style = Stroke(sw, cap = StrokeCap.Round))
            }
            Icon(ico, color, size = 20.dp)
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MX.TextHi)
            Text(sub, fontSize = 13.sp, color = MX.TextMid, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(meta, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color, modifier = Modifier.padding(end = 12.dp))
        Box(Modifier.size(40.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Icon(Ico.PLAY, Color.Black, Modifier.padding(start = 2.dp), size = 15.dp)
        }
    }
}

// ---------------------------------------------------------------- settings

@Composable
private fun SettingsOverlay(prefs: Prefs, sfx: Sfx, onClose: () -> Unit) {
    var sound by remember { mutableStateOf(prefs.sound) }
    var haptics by remember { mutableStateOf(prefs.haptics) }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 420.dp).padding(16.dp).clip(RoundedCornerShape(22.dp)).background(MX.Surface)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(20.dp),
        ) {
            Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MX.TextHi)
            Spacer(Modifier.height(16.dp))
            SettingRow(Ico.SOUND, MX.Cyan, "Sound effects") {
                Segmented(listOf("On", "Off"), if (sound) 0 else 1, {
                    sound = it == 0; prefs.sound = sound; sfx.soundOn = sound
                    if (sound) sfx.play(Sfx.S.COIN)
                }, Modifier.width(150.dp), accent = MX.Cyan)
            }
            Spacer(Modifier.height(12.dp))
            SettingRow(Ico.BOLT, MX.Amber, "Vibration") {
                Segmented(listOf("On", "Off"), if (haptics) 0 else 1, {
                    haptics = it == 0; prefs.haptics = haptics; sfx.hapticsOn = haptics
                    if (haptics) sfx.buzz(40)
                }, Modifier.width(150.dp), accent = MX.Amber)
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Ico.CAMERA, MX.TextLow, size = 14.dp)
                Text(
                    "  Camera frames stay on your device. Nothing is recorded or uploaded.",
                    fontSize = 12.sp, lineHeight = 17.sp, color = MX.TextLow,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Done", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MX.Cyan,
                modifier = Modifier.align(Alignment.End).clip(CircleShape).clickable(onClick = onClose)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun SettingRow(ico: Ico, color: Color, label: String, control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(ico, color, size = 18.dp)
        Text("  $label", fontSize = 15.sp, color = MX.TextHi, modifier = Modifier.weight(1f))
        control()
    }
}
