package com.nightwise.motionx.ui.screens

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nightwise.motionx.audio.Sfx
import com.nightwise.motionx.audio.VoiceCoach
import com.nightwise.motionx.data.Prefs
import com.nightwise.motionx.games.gate.GateDifficulty
import com.nightwise.motionx.games.gate.GateGame
import com.nightwise.motionx.games.gate.GateScene
import com.nightwise.motionx.games.pose.PoseCameraView
import com.nightwise.motionx.games.pose.PoseDef
import com.nightwise.motionx.games.pose.PoseMatcher
import com.nightwise.motionx.tracking.Lm
import com.nightwise.motionx.tracking.PoseTracker
import com.nightwise.motionx.ui.components.*
import com.nightwise.motionx.ui.theme.MX
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicReference

@Composable
fun GateGameScreen(difficulty: GateDifficulty, prefs: Prefs, sfx: Sfx, onExit: () -> Unit) {
    val activity = LocalContext.current as ComponentActivity
    ImmersiveGameMode(activity)
    val cam = rememberCameraTracking(activity)
    val tracker = cam.tracker
    val frame by tracker.frames.collectAsState()
    val status by tracker.status.collectAsState()
    val accent = gateColor(difficulty)

    val coach = remember { VoiceCoach(activity) }
    DisposableEffect(Unit) { onDispose { coach.shutdown() } }
    fun say(s: String) { if (prefs.sound) coach.say(s) }

    var runId by remember { mutableIntStateOf(0) }
    val game = remember(runId) { GateGame(difficulty) }

    val matchPose = remember { AtomicReference<PoseDef?>(null) }
    val resultFlow = remember { MutableStateFlow<Pair<PoseMatcher.Result?, Long>>(null to 0L) }
    LaunchedEffect(cam.hasPermission) {
        tracker.onFrame = { f ->
            val p = matchPose.get()
            val lm = f.landmarks
            resultFlow.value = (if (p != null && lm != null) PoseMatcher.evaluate(p, lm, f.width.toFloat() / f.height) else null) to System.nanoTime()
        }
    }
    val result by resultFlow.collectAsState()

    var userPaused by remember(runId) { mutableStateOf(false) }
    var autoPaused by remember(runId) { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var over by remember(runId) { mutableStateOf(false) }
    var newBest by remember(runId) { mutableStateOf(false) }
    var lastPoints by remember(runId) { mutableIntStateOf(0) }
    var showPose by remember { mutableStateOf(prefs.gateShowPlayer) }

    LaunchedEffect(game) {
        var last = 0L
        var visibleFor = 0f
        var lostFor = 0f
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.1f)
                last = now
                matchPose.set(game.matchPose)
                val lm = frame?.landmarks
                val bodyVisible = lm != null && lm[Lm.L_HIP * 4 + 3] > 0.5f && lm[Lm.R_HIP * 4 + 3] > 0.5f &&
                    lm[Lm.L_SHOULDER * 4 + 3] > 0.5f && lm[Lm.R_SHOULDER * 4 + 3] > 0.5f
                if (bodyVisible) { visibleFor += dt; lostFor = 0f } else { lostFor += dt; visibleFor = 0f }

                when (game.phase) {
                    GateGame.Phase.WAITING -> if (visibleFor > 0.8f && !userPaused) game.startCountdown()
                    GateGame.Phase.PAUSED -> if (autoPaused && !userPaused && visibleFor > 0.6f) { autoPaused = false; game.startCountdown() }
                    GateGame.Phase.RUNNING, GateGame.Phase.COUNTDOWN -> if (lostFor > 1.2f) { autoPaused = true; game.pause() }
                    else -> Unit
                }
                if (!userPaused) {
                    val (r, at) = resultFlow.value
                    val fresh = r != null && System.nanoTime() - at < 500_000_000L && r.variant.id == game.matchPose.id
                    val prevCd = kotlin.math.ceil(game.countdown).toInt()
                    game.update(dt, fresh && r!!.matched)
                    if (game.phase == GateGame.Phase.COUNTDOWN && kotlin.math.ceil(game.countdown).toInt() != prevCd) sfx.play(Sfx.S.TICK)
                }
                for (e in game.events) when (e) {
                    is GateGame.Event.NewWall -> say(if (e.retry) "Try again. ${e.pose.name}" else e.pose.name)
                    GateGame.Event.WindowOpen -> sfx.play(Sfx.S.TICK)
                    is GateGame.Event.Passed -> {
                        lastPoints = e.points
                        sfx.play(if (e.perfect) Sfx.S.STAR else Sfx.S.COIN); sfx.buzz(30, 100)
                        say(if (e.perfect) "Perfect!" else "Pass!")
                    }
                    GateGame.Event.Failed -> { sfx.play(Sfx.S.HIT); sfx.buzz(250, 255) }
                    GateGame.Event.Over -> {
                        sfx.play(Sfx.S.OVER)
                        newBest = prefs.recordGate(difficulty.name, game.score)
                        over = true
                    }
                }
                game.events.clear()
                tick++
            }
        }
    }

    BackHandler {
        if (over) onExit() else { userPaused = true; game.pause() }
    }

    val frameTick = tick
    if (frameTick < 0) return
    val r = result.first?.takeIf { it.variant.id == game.matchPose.id }
    val lm = frame?.landmarks
    val aspect = frame?.let { it.width.toFloat() / it.height } ?: (4f / 3f)

    Row(Modifier.fillMaxSize().background(MX.Night)) {
        // ---------------- stage ----------------
        Box(Modifier.weight(1f).fillMaxHeight()) {
            GateScene(
                game.pose, game.progress, game.outcome, game.outcomeT, game.time, difficulty.holeWidth, accent,
                game.inWindow, lm, aspect, r, Modifier.fillMaxSize(), showPlayer = showPose,
            )
            GateHud(
                game, accent, showPose,
                onTogglePose = { showPose = !showPose; prefs.gateShowPlayer = showPose },
                onPause = { userPaused = true; game.pause() },
            )
            OutcomeText(game, lastPoints)
            if (game.phase == GateGame.Phase.COUNTDOWN && !userPaused) {
                val n = kotlin.math.ceil(game.countdown).toInt().coerceAtLeast(1)
                Text(
                    "$n", Modifier.align(Alignment.Center).scale(0.8f + (game.countdown % 1f) * 0.5f),
                    fontSize = 110.sp, fontWeight = FontWeight.Black, color = accent,
                )
            }
            when {
                over -> GateOver(game, newBest, prefs.gateBest(difficulty.name), accent, onAgain = { runId++ }, onExit = onExit)
                userPaused -> GatePause(
                    onResume = { userPaused = false; game.startCountdown() },
                    onQuit = onExit,
                )
                game.phase == GateGame.Phase.WAITING || (game.phase == GateGame.Phase.PAUSED && autoPaused) -> GetInView(
                    cam.hasPermission, status, tracker.errorMessage, cam.request,
                    resuming = game.phase == GateGame.Phase.PAUSED, onBack = onExit,
                )
            }
        }
        // ---------------- camera ----------------
        Box(Modifier.fillMaxHeight().aspectRatio(0.75f)) {
            PoseCameraView(frame, r, Modifier.fillMaxSize(), fill = true)
            Box(Modifier.fillMaxHeight().width(2.dp).align(Alignment.CenterStart).background(accent.copy(alpha = 0.5f)))
            Text(
                if (r?.matched == true) "MATCH" else (r?.hint ?: "Step into view"),
                Modifier.align(Alignment.BottomCenter).padding(10.dp).clip(CircleShape)
                    .background(if (r?.matched == true) MX.Lime else MX.GlassDark).padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge, color = if (r?.matched == true) MX.Night else MX.TextHi, maxLines = 1,
            )
            Row(Modifier.align(Alignment.TopStart).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                val t = rememberInfiniteTransition(label = "live")
                val a by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a")
                Box(Modifier.size(9.dp).clip(CircleShape).background(MX.Coral.copy(alpha = if (status == PoseTracker.Status.RUNNING) a else 0.3f)))
                Text("  LIVE", style = MaterialTheme.typography.labelMedium, color = MX.TextHi)
            }
        }
    }
}

@Composable
private fun BoxScope.GateHud(game: GateGame, accent: Color, showPose: Boolean, onTogglePose: () -> Unit, onPause: () -> Unit) {
    Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top) {
        Chip {
            Hearts(game.lives, game.difficulty.lives, size = 18.dp)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("%,d".format(game.score), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = MX.TextHi)
            if (game.combo >= 2) Text("COMBO x${game.combo}", style = MaterialTheme.typography.labelLarge, color = MX.Lime)
        }
        // show / hide the player's figure on the stage
        Row(
            Modifier.height(44.dp).clip(CircleShape)
                .background(MX.GlassDark)
                .clickable(onClick = onTogglePose).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (showPose) Ico.EYE else Ico.EYE_OFF, if (showPose) MX.Lime else MX.TextMid, size = 16.dp)
            Text(
                if (showPose) "  Pose on" else "  Pose off",
                style = MaterialTheme.typography.labelLarge, color = if (showPose) MX.Lime else MX.TextMid,
            )
        }
        Box(Modifier.width(8.dp))
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(MX.GlassDark).clickable(onClick = onPause),
            contentAlignment = Alignment.Center,
        ) { Icon(Ico.PAUSE, MX.TextHi, size = 16.dp) }
    }
    // approach bar + hold meter
    if (game.phase == GateGame.Phase.RUNNING) {
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth(0.62f).padding(bottom = 14.dp)) {
            if (game.inWindow) {
                Text(
                    if (game.matchedNow) "HOLD IT!" else "MATCH THE HOLE!",
                    Modifier.align(Alignment.CenterHorizontally),
                    style = MaterialTheme.typography.titleLarge, color = if (game.matchedNow) MX.Lime else MX.Amber, fontWeight = FontWeight.Bold,
                )
            }
            Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(MX.SurfaceHi)) {
                val windowStart = 1f - game.difficulty.window / game.travel
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(windowStart.coerceIn(0.01f, 0.99f)))
                    Box(Modifier.weight((1f - windowStart).coerceIn(0.01f, 0.99f)).fillMaxHeight().background(MX.Lime.copy(alpha = 0.3f)))
                }
                Box(Modifier.fillMaxHeight().fillMaxWidth(game.progress).clip(CircleShape).background(accent))
            }
            VSpace(4.dp)
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(MX.SurfaceHi)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(game.holdProgress).clip(CircleShape).background(MX.Lime))
            }
        }
    }
}

@Composable
private fun Chip(content: @Composable () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(16.dp)).background(MX.GlassDark)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) { content() }
}

@Composable
private fun BoxScope.OutcomeText(game: GateGame, points: Int) {
    if (game.outcome == GateGame.Outcome.NONE || game.outcomeT > 1.1f) return
    val (txt, col) = when (game.outcome) {
        GateGame.Outcome.PERFECT -> "PERFECT" to MX.Yellow
        GateGame.Outcome.PASS -> "PASS" to MX.Cyan
        else -> "CRASH" to MX.Coral
    }
    val sub = when {
        game.outcome != GateGame.Outcome.FAIL -> "+$points"
        game.lives > 0 -> "Lost a heart, try again"
        else -> "out of hearts"
    }
    Row(
        Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 64.dp)
            .scale(1f + (0.2f - game.outcomeT).coerceAtLeast(0f))
            .clip(RoundedCornerShape(18.dp)).background(MX.GlassDark)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(txt, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = col)
        Text("   $sub", style = MaterialTheme.typography.titleMedium, color = MX.TextHi)
    }
}

@Composable
private fun BoxScope.Card(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)))
    Column(
        Modifier.align(Alignment.Center).widthIn(max = 520.dp).padding(14.dp).clip(RoundedCornerShape(28.dp))
            .background(MX.Surface).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
private fun BoxScope.GetInView(
    hasCam: Boolean, status: PoseTracker.Status, error: String?, request: () -> Unit, resuming: Boolean, onBack: () -> Unit,
) = Card {
    Icon(Ico.PERSON, MX.Cyan, size = 40.dp)
    VSpace(8.dp)
    Text(if (resuming) "Step back into view" else "Get in position", style = MaterialTheme.typography.headlineMedium, color = MX.TextHi)
    VSpace(6.dp)
    Text(
        when {
            !hasCam -> "Allow the camera to play."
            status == PoseTracker.Status.ERROR -> error ?: "Camera error"
            status != PoseTracker.Status.RUNNING -> "Starting camera…"
            else -> "Phone sideways at waist height, step back 2–3 m so your whole body is visible. The game starts when it sees you."
        },
        style = MaterialTheme.typography.bodyMedium, color = MX.TextMid,
    )
    VSpace(14.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GhostButton("Back", Modifier.weight(1f), height = 46.dp, onClick = onBack)
        if (!hasCam) GlowButton("Allow camera", Modifier.weight(1.4f), height = 46.dp, onClick = request)
    }
}

@Composable
private fun BoxScope.GatePause(onResume: () -> Unit, onQuit: () -> Unit) = Card {
    Text("Paused", style = MaterialTheme.typography.displayMedium, color = MX.TextHi)
    VSpace(14.dp)
    GlowButton("Resume", Modifier.fillMaxWidth(), leading = Ico.PLAY, onClick = onResume)
    VSpace(10.dp)
    GhostButton("Quit", Modifier.fillMaxWidth(), height = 46.dp, accent = MX.Coral, onClick = onQuit)
}

@Composable
private fun BoxScope.GateOver(game: GateGame, newBest: Boolean, best: Int, accent: Color, onAgain: () -> Unit, onExit: () -> Unit) = Card {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("${game.difficulty.label.uppercase()} · GAME OVER", style = MaterialTheme.typography.labelLarge, color = accent)
            Text("%,d".format(game.score), fontSize = 52.sp, fontWeight = FontWeight.Bold, color = MX.TextHi)
            Text("Best %,d".format(best), style = MaterialTheme.typography.titleMedium, color = MX.TextMid)
        }
        if (newBest) {
            Pill("NEW BEST", MX.Yellow, icon = Ico.TROPHY)
        }
    }
    VSpace(12.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("${game.passed}/${game.walls}", "Walls", accent, Modifier.weight(1f))
        StatTile("${game.perfects}", "Perfect", MX.Lime, Modifier.weight(1f))
        StatTile("x${game.bestCombo}", "Combo", MX.Amber, Modifier.weight(1f))
        StatTile("%d:%02d".format(game.time.toInt() / 60, game.time.toInt() % 60), "Time", MX.Violet, Modifier.weight(1f))
    }
    VSpace(14.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GhostButton("Change level", Modifier.weight(1f), onClick = onExit)
        GlowButton("Play again", Modifier.weight(1.4f), height = 50.dp, leading = Ico.REPLAY, color = accent, onClick = onAgain)
    }
}
