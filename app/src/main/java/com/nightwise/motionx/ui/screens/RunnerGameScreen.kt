package com.nightwise.motionx.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nightwise.motionx.audio.Sfx
import com.nightwise.motionx.audio.VoiceCoach
import com.nightwise.motionx.ui.components.Segmented
import com.nightwise.motionx.data.Prefs
import com.nightwise.motionx.data.RunResult
import com.nightwise.motionx.games.runner.RunnerConfig
import com.nightwise.motionx.games.runner.RunnerGame
import com.nightwise.motionx.games.runner.RunnerGameView
import com.nightwise.motionx.games.runner.RunnerHud
import com.nightwise.motionx.games.runner.RunnerRenderer
import com.nightwise.motionx.tracking.BodyController
import com.nightwise.motionx.tracking.PoseFrame
import com.nightwise.motionx.tracking.PoseTracker
import com.nightwise.motionx.tracking.TrackingView
import com.nightwise.motionx.ui.components.GhostButton
import com.nightwise.motionx.ui.components.GlowButton
import com.nightwise.motionx.ui.components.StatTile
import com.nightwise.motionx.ui.components.VSpace
import com.nightwise.motionx.ui.theme.MX
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

@Composable
fun RunnerGameScreen(config: RunnerConfig, prefs: Prefs, sfx: Sfx, onExit: () -> Unit) {
    val activity = LocalContext.current as ComponentActivity
    LandscapeImmersive(activity)
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    var hasCam by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var camDenied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasCam = it; camDenied = !it
    }
    LaunchedEffect(Unit) { if (!hasCam) launcher.launch(Manifest.permission.CAMERA) }

    // ---- tracking (lives across runs) ----
    val tracker = remember { PoseTracker(activity) }
    val body = remember { BodyController() }
    val bodyFlow = remember { MutableStateFlow(BodyController.State()) }
    val recal = remember { AtomicBoolean(false) }
    val rendererRef = remember { AtomicReference<RunnerRenderer?>(null) }
    var touchMode by remember { mutableStateOf(false) }
    val touchModeRef = rememberUpdatedState(touchMode)

    val frame by tracker.frames.collectAsState()
    val bodyState by bodyFlow.collectAsState()
    val trackerStatus by tracker.status.collectAsState()

    LaunchedEffect(hasCam, landscape) {
        if (hasCam && landscape) {
            var lastJump = -1
            var lastDuck = -1
            tracker.onFrame = { f: PoseFrame ->
                if (recal.getAndSet(false)) body.recalibrate()
                val s = body.update(f.landmarks, f.width.toFloat() / f.height, f.timestampMs / 1000.0)
                bodyFlow.value = s
                if (s.phase == BodyController.Phase.READY) {
                    if (lastJump < 0) { lastJump = s.jumpSeq; lastDuck = s.duckSeq }
                    val jumpNow = s.jumpSeq != lastJump
                    lastJump = s.jumpSeq
                    lastDuck = s.duckSeq
                    if (!touchModeRef.value && s.lostSeconds == 0f) {
                        rendererRef.get()?.post { g ->
                            g.setLane(s.lane)
                            if (jumpNow) g.jump()
                            g.setDucking(s.ducking)
                        }
                    }
                }
            }
            tracker.start(activity)
        }
    }
    DisposableEffect(Unit) { onDispose { tracker.onFrame = null; tracker.stop() } }

    // ---- game (recreated per run) ----
    var runId by remember { mutableIntStateOf(0) }
    val game = remember(runId) { RunnerGame(config) }
    val hudFlow = remember(runId) { MutableStateFlow(RunnerHud(lives = config.lives, speedLevel = config.startSpeed.toFloat())) }
    val hud by hudFlow.collectAsState()
    var result by remember(runId) { mutableStateOf<RunResult?>(null) }
    var userPaused by remember(runId) { mutableStateOf(false) }
    var autoPaused by remember(runId) { mutableStateOf(false) }
    val main = remember { Handler(Looper.getMainLooper()) }
    val coach = remember { VoiceCoach(activity) }
    DisposableEffect(Unit) { onDispose { coach.shutdown() } }
    var assistOn by remember { mutableStateOf(config.assist) }

    val renderer = remember(runId) {
        RunnerRenderer(
            game,
            onHud = { hudFlow.value = it },
            onEvent = { e ->
                main.post {
                    when (e) {
                        is RunnerGame.Event.Coin -> sfx.play(Sfx.S.COIN)
                        is RunnerGame.Event.Star -> { sfx.play(Sfx.S.STAR); sfx.buzz(30, 120) }
                        is RunnerGame.Event.Hit -> { sfx.play(Sfx.S.HIT); sfx.buzz(220, 255) }
                        RunnerGame.Event.Jump -> sfx.play(Sfx.S.JUMP)
                        is RunnerGame.Event.SpeedUp -> sfx.play(Sfx.S.SPEED)
                        is RunnerGame.Event.Cue -> if (assistOn) coach.say(e.action.say)
                        RunnerGame.Event.Over -> {
                            sfx.play(Sfx.S.OVER)
                            val r = RunResult(
                                game.score, game.distance.toInt(), game.time.toInt(), game.coins, game.stars,
                                game.jumps, game.ducks, game.laneChanges, game.cleared, game.hits,
                            )
                            result = if (config.training) r else prefs.record(r)
                        }
                    }
                }
            },
        )
    }
    LaunchedEffect(renderer) { rendererRef.set(renderer) }
    LaunchedEffect(renderer, assistOn) { renderer.post { it.assistOn = assistOn } }

    // Auto start / auto pause / resume driven by tracking.
    val ready = bodyState.phase == BodyController.Phase.READY && bodyState.lostSeconds == 0f
    LaunchedEffect(ready, touchMode, hud.phase, userPaused) {
        val canGo = (ready || touchMode) && !userPaused && result == null
        if (canGo && (hud.phase == RunnerGame.Phase.WAITING || (hud.phase == RunnerGame.Phase.PAUSED && autoPaused))) {
            delay(if (hud.phase == RunnerGame.Phase.WAITING) 700 else 400)
            autoPaused = false
            renderer.post { it.startCountdown() }
        }
    }
    LaunchedEffect(bodyState.lostSeconds > 0.8f, hud.phase, touchMode) {
        if (!touchMode && bodyState.lostSeconds > 0.8f &&
            (hud.phase == RunnerGame.Phase.RUNNING || hud.phase == RunnerGame.Phase.COUNTDOWN)
        ) {
            autoPaused = true
            renderer.post { it.pause() }
        }
    }
    // Countdown beeps
    val cdInt = if (hud.phase == RunnerGame.Phase.COUNTDOWN) kotlin.math.ceil(hud.countdown).toInt() else 0
    LaunchedEffect(cdInt, hud.phase) {
        if (cdInt in 1..3) sfx.play(Sfx.S.TICK)
    }
    var wasCountdown by remember(runId) { mutableStateOf(false) }
    LaunchedEffect(hud.phase) {
        if (hud.phase == RunnerGame.Phase.RUNNING && wasCountdown) sfx.play(Sfx.S.GO)
        wasCountdown = hud.phase == RunnerGame.Phase.COUNTDOWN
    }

    BackHandler {
        when {
            result != null -> onExit()
            hud.phase == RunnerGame.Phase.RUNNING || hud.phase == RunnerGame.Phase.COUNTDOWN -> {
                userPaused = true; renderer.post { it.pause() }
            }
            else -> onExit()
        }
    }

    Row(Modifier.fillMaxSize().background(MX.Night)) {
        // ------------------- 3D game -------------------
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .pointerInput(runId) {
                    var dx = 0f
                    var dy = 0f
                    detectDragGestures(
                        onDragStart = { dx = 0f; dy = 0f },
                        onDrag = { _, d -> dx += d.x; dy += d.y },
                        onDragEnd = {
                            val r = renderer
                            if (abs(dx) > abs(dy) && abs(dx) > 40f) r.post { it.nudgeLane(if (dx > 0) 1 else -1) }
                            else if (dy < -40f) r.post { it.jump() }
                            else if (dy > 40f) r.post { it.duck() }
                        },
                    )
                },
        ) {
            key(runId) {
                val viewRef = remember { AtomicReference<RunnerGameView?>(null) }
                DisposableEffect(Unit) {
                    val obs = LifecycleEventObserver { _, ev ->
                        val view = viewRef.get() ?: return@LifecycleEventObserver
                        if (ev == Lifecycle.Event.ON_PAUSE) {
                            view.onPause()
                            if (game.phase == RunnerGame.Phase.RUNNING || game.phase == RunnerGame.Phase.COUNTDOWN) {
                                renderer.post { g -> g.pause() }
                                userPaused = true
                            }
                        }
                        if (ev == Lifecycle.Event.ON_RESUME) view.onResume()
                    }
                    activity.lifecycle.addObserver(obs)
                    onDispose { activity.lifecycle.removeObserver(obs) }
                }
                AndroidView(
                    factory = { ctx -> RunnerGameView(ctx, renderer).also { viewRef.set(it) } },
                    modifier = Modifier.fillMaxSize(),
                    onRelease = { it.onPause() },
                )
            }
            HitFlash(hud.hitFlash)
            Hud(hud, config, bodyState, touchMode, assistOn, onPause = { userPaused = true; renderer.post { it.pause() } })

            when {
                result != null -> ResultsOverlay(
                    result!!, config,
                    onAgain = { runId++ },
                    onHome = onExit,
                )
                !hasCam && !touchMode -> CenterCard {
                    Text("📷", fontSize = 40.sp)
                    VSpace(8.dp)
                    Text("Camera needed", style = MaterialTheme.typography.headlineMedium, color = MX.TextHi)
                    Text(
                        if (camDenied) "MotionX tracks your body with the front camera. Nothing is recorded or uploaded."
                        else "Allow camera access to play with your body.",
                        color = MX.TextMid, textAlign = TextAlign.Center,
                    )
                    VSpace(16.dp)
                    GlowButton("Allow camera", Modifier.fillMaxWidth()) { launcher.launch(Manifest.permission.CAMERA) }
                    VSpace(10.dp)
                    GhostButton("Play with touch", Modifier.fillMaxWidth(), leading = "👆") { touchMode = true }
                }
                userPaused && hud.phase != RunnerGame.Phase.OVER -> PauseOverlay(
                    training = config.training,
                    assistOn = assistOn,
                    onAssist = { assistOn = it },
                    onResume = { userPaused = false; renderer.post { it.startCountdown() } },
                    onRecalibrate = { recal.set(true); userPaused = false; autoPaused = true },
                    onFinish = { userPaused = false; renderer.post { it.finish() } },
                    onQuit = onExit,
                )
                hud.phase == RunnerGame.Phase.WAITING || (hud.phase == RunnerGame.Phase.PAUSED && autoPaused) ->
                    GetReadyOverlay(bodyState, trackerStatus, tracker.errorMessage, touchMode, resuming = hud.phase == RunnerGame.Phase.PAUSED, onTouch = { touchMode = true }, onBack = onExit)
            }
            if (hud.phase == RunnerGame.Phase.COUNTDOWN && !userPaused) Countdown(hud.countdown)
        }

        // ------------------- live tracking panel -------------------
        TrackingPanel(
            frame, bodyState, trackerStatus, touchMode, tracker.usingGpu,
            Modifier.fillMaxHeight().aspectRatio(0.75f),
            onRecenter = { recal.set(true) },
        )
    }
}

@Composable
private fun LandscapeImmersive(activity: ComponentActivity) {
    DisposableEffect(Unit) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val ctl = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctl.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            ctl.show(WindowInsetsCompat.Type.systemBars())
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

// ============================================================ HUD

@Composable
private fun BoxScope.Hud(
    hud: RunnerHud,
    config: RunnerConfig,
    body: BodyController.State,
    touchMode: Boolean,
    assistOn: Boolean,
    onPause: () -> Unit,
) {
    if (assistOn && hud.phase == RunnerGame.Phase.RUNNING) CueBanner(hud.cue, hud.cueAge)
    MoveStrip(
        lane = if (touchMode || body.phase != BodyController.Phase.READY) hud.lane else body.lane,
        jumping = if (touchMode) hud.airborne else body.jumping,
        ducking = if (touchMode) hud.sliding else body.ducking,
        modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp),
    )
    Row(
        Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // lives
        HudChip {
            if (config.lives == 0) {
                Text("♥ ∞", color = MX.Magenta, fontWeight = FontWeight.Black, fontSize = 18.sp)
            } else {
                Row {
                    repeat(config.lives) { i ->
                        val alive = i < hud.lives
                        val s by animateFloatAsState(if (alive) 1f else 0.75f, label = "heart")
                        Text("♥", color = if (alive) MX.Magenta else Color.White.copy(alpha = 0.18f), fontSize = 20.sp, modifier = Modifier.scale(s).padding(horizontal = 1.dp))
                    }
                }
            }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "%,d".format(hud.score),
                    style = MaterialTheme.typography.displayMedium.copy(fontSize = 34.sp),
                    color = MX.TextHi,
                )
                Text("SCORE ×${config.scoreMultiplier}", style = MaterialTheme.typography.labelSmall, color = MX.Cyan)
                AnimatedVisibility(hud.combo >= 5, enter = scaleIn() + fadeIn(), exit = fadeOut()) {
                    Text("COMBO ×${hud.combo}", style = MaterialTheme.typography.labelLarge, color = MX.Lime, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        HudChip {
            Box(Modifier.size(14.dp).clip(CircleShape).background(MX.Amber))
            Text("  ${hud.coins}", color = MX.TextHi, fontWeight = FontWeight.Black, fontSize = 18.sp)
            if (hud.stars > 0) Text("   ★ ${hud.stars}", color = MX.Amber, fontWeight = FontWeight.Black, fontSize = 18.sp)
        }
        Box(Modifier.width(10.dp))
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(MX.GlassDark).border(1.dp, MX.Stroke, CircleShape).clickable(onClick = onPause),
            contentAlignment = Alignment.Center,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(width = 5.dp, height = 16.dp).clip(RoundedCornerShape(2.dp)).background(MX.TextHi))
                Box(Modifier.size(width = 5.dp, height = 16.dp).clip(RoundedCornerShape(2.dp)).background(MX.TextHi))
            }
        }
    }
    // speed + distance
    Row(Modifier.align(Alignment.BottomStart).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        HudChip {
            Column {
                Text("SPEED", style = MaterialTheme.typography.labelSmall, color = MX.TextLow)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("%.1f".format(hud.speedLevel), color = MX.TextHi, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Box(Modifier.width(8.dp))
                    Box(Modifier.size(70.dp, 6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(hud.speedLevel / 10f).clip(CircleShape).background(MX.PrimaryGradient))
                    }
                }
            }
            Box(Modifier.width(16.dp))
            Column {
                Text("DISTANCE", style = MaterialTheme.typography.labelSmall, color = MX.TextLow)
                Text("${hud.distance.toInt()} m", color = MX.TextHi, fontWeight = FontWeight.Black, fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun HudChip(content: @Composable () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(16.dp)).background(MX.GlassDark).border(1.dp, MX.StrokeSoft, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun HitFlash(v: Float) {
    if (v <= 0f) return
    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(Color.Transparent, MX.Coral.copy(alpha = (v * 1.1f).coerceAtMost(0.55f))), radius = 1400f),
        ),
    )
}

@Composable
private fun BoxScope.Countdown(t: Float) {
    val n = kotlin.math.ceil(t).toInt().coerceAtLeast(1)
    val frac = t - kotlin.math.floor(t)
    Box(Modifier.align(Alignment.Center), contentAlignment = Alignment.Center) {
        Text(
            if (t > 0.05f) "$n" else "GO!",
            fontSize = 120.sp, fontWeight = FontWeight.Black,
            color = when (n) { 3 -> MX.Magenta; 2 -> MX.Amber; else -> MX.Lime },
            modifier = Modifier.scale(0.8f + frac * 0.5f),
        )
    }
}

// ============================================================ overlays

@Composable
private fun BoxScope.CenterCard(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
    Column(
        Modifier.align(Alignment.Center).widthIn(max = 420.dp).padding(16.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(Color(0xF01A2050), Color(0xF00C0F26))))
            .border(1.dp, Brush.linearGradient(listOf(MX.Cyan.copy(alpha = 0.6f), MX.Violet.copy(alpha = 0.2f))), RoundedCornerShape(28.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
private fun BoxScope.GetReadyOverlay(
    body: BodyController.State,
    status: PoseTracker.Status,
    error: String?,
    touchMode: Boolean,
    resuming: Boolean,
    onTouch: () -> Unit,
    onBack: () -> Unit,
) {
    if (touchMode) return
    CenterCard {
        val calibrating = body.phase == BodyController.Phase.CALIBRATING
        val ringColor = if (calibrating) MX.Lime else MX.Cyan
        val pulse = rememberInfiniteTransition(label = "ring")
        val spin by pulse.animateFloat(0f, 360f, infiniteRepeatable(tween(2400)), label = "spin")
        Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Color.White.copy(alpha = 0.08f), style = Stroke(8.dp.toPx()))
                if (calibrating) {
                    drawArc(ringColor, -90f, 360f * body.calibration, false, style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
                } else {
                    drawArc(ringColor, spin, 80f, false, style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
                }
            }
            Text(if (calibrating) "${(body.calibration * 100).toInt()}%" else "🧍", fontSize = if (calibrating) 20.sp else 34.sp, color = MX.TextHi, fontWeight = FontWeight.Black)
        }
        VSpace(14.dp)
        Text(
            when {
                status == PoseTracker.Status.ERROR -> "Tracking unavailable"
                resuming -> "Step back into view"
                calibrating -> "Hold still…"
                else -> "Get in position"
            },
            style = MaterialTheme.typography.headlineMedium, color = MX.TextHi, textAlign = TextAlign.Center,
        )
        VSpace(6.dp)
        Text(
            when {
                status == PoseTracker.Status.ERROR -> error ?: "Camera error"
                status != PoseTracker.Status.RUNNING -> "Starting camera…"
                else -> body.hint
            },
            style = MaterialTheme.typography.bodyLarge, color = ringColor, textAlign = TextAlign.Center,
        )
        VSpace(6.dp)
        Text(
            "Phone at waist height, landscape · stand 2–3 m back in the middle box · whole body visible",
            style = MaterialTheme.typography.bodyMedium, color = MX.TextLow, textAlign = TextAlign.Center,
        )
        VSpace(16.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("Back", Modifier.weight(1f), height = 46.dp, onClick = onBack)
            GhostButton("Touch mode", Modifier.weight(1f), leading = "👆", height = 46.dp, onClick = onTouch)
        }
    }
}

@Composable
private fun BoxScope.PauseOverlay(
    training: Boolean,
    assistOn: Boolean,
    onAssist: (Boolean) -> Unit,
    onResume: () -> Unit,
    onRecalibrate: () -> Unit,
    onFinish: () -> Unit,
    onQuit: () -> Unit,
) {
    CenterCard {
        Text("Paused", style = MaterialTheme.typography.displayMedium, color = MX.TextHi)
        Text("Take a breather 💨", color = MX.TextMid)
        VSpace(18.dp)
        GlowButton("Resume", Modifier.fillMaxWidth(), leading = "▶", onClick = onResume)
        VSpace(12.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🔊  Voice assist", style = MaterialTheme.typography.titleMedium, color = MX.TextHi, modifier = Modifier.weight(1f))
            Segmented(listOf("On", "Off"), if (assistOn) 0 else 1, { onAssist(it == 0) }, Modifier.width(150.dp), accent = MX.Lime)
        }
        VSpace(12.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("Recenter", Modifier.weight(1f), leading = "🎯", height = 46.dp, onClick = onRecalibrate)
            if (training) GhostButton("Finish", Modifier.weight(1f), leading = "🏁", height = 46.dp, onClick = onFinish)
            GhostButton("Quit", Modifier.weight(1f), height = 46.dp, accent = MX.Coral, onClick = onQuit)
        }
    }
}

@Composable
private fun BoxScope.ResultsOverlay(r: RunResult, config: RunnerConfig, onAgain: () -> Unit, onHome: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
    Column(
        Modifier.align(Alignment.Center).widthIn(max = 560.dp).padding(14.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(Color(0xF51A2050), Color(0xF50C0F26))))
            .border(1.dp, Brush.linearGradient(listOf(MX.Magenta.copy(alpha = 0.7f), MX.Cyan.copy(alpha = 0.3f))), RoundedCornerShape(28.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (config.training) "TRAINING DONE" else "RUN COMPLETE", style = MaterialTheme.typography.labelLarge, color = MX.Cyan)
                Text("%,d".format(r.score), style = MaterialTheme.typography.displayLarge, color = MX.TextHi)
                Text("${config.emoji} ${config.preset} · ×${config.scoreMultiplier}", color = MX.TextMid)
            }
            if (r.newBest) {
                Box(
                    Modifier.clip(RoundedCornerShape(14.dp)).background(MX.HotGradient).padding(horizontal = 14.dp, vertical = 8.dp),
                ) { Text("🏆 NEW BEST", fontWeight = FontWeight.Black, color = Color.White) }
            }
        }
        VSpace(14.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("${r.distance} m", "Distance", MX.Cyan, Modifier.weight(1f))
            StatTile("%d:%02d".format(r.seconds / 60, r.seconds % 60), "Time", MX.Violet, Modifier.weight(1f))
            StatTile("${r.coins}", "Coins", MX.Amber, Modifier.weight(1f))
            StatTile("${r.cleared}", "Dodged", MX.Lime, Modifier.weight(1f))
        }
        VSpace(8.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("${r.jumps}", "Jumps", MX.Amber, Modifier.weight(1f))
            StatTile("${r.ducks}", "Ducks", MX.Sky, Modifier.weight(1f))
            StatTile("${r.hops}", "Hops", MX.Magenta, Modifier.weight(1f))
            StatTile("${r.stars}", "Stars", MX.Amber, Modifier.weight(1f))
        }
        VSpace(16.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("Home", Modifier.weight(1f), onClick = onHome)
            GlowButton("Play again", Modifier.weight(1.6f), height = 52.dp, leading = "↻", onClick = onAgain)
        }
    }
}

// ============================================================ tracking panel

@Composable
private fun TrackingPanel(
    frame: PoseFrame?,
    body: BodyController.State,
    status: PoseTracker.Status,
    touchMode: Boolean,
    gpu: Boolean,
    modifier: Modifier,
    onRecenter: () -> Unit,
) {
    val live = rememberInfiniteTransition(label = "live")
    val dot by live.animateFloat(0.3f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "dot")
    Box(modifier.background(MX.Night)) {
        // Full-height camera: head to feet, sides cropped around the centre lane.
        TrackingView(frame, body, Modifier.fillMaxSize(), fill = true)
        Box(
            Modifier.fillMaxWidth().height(70.dp).align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent))),
        )
        Box(
            Modifier.fillMaxWidth().height(70.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))),
        )
        Box(Modifier.fillMaxHeight().width(2.dp).align(Alignment.CenterStart).background(MX.Cyan.copy(alpha = 0.5f)))
        Row(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(MX.Coral.copy(alpha = if (status == PoseTracker.Status.RUNNING) dot else 0.3f)))
            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                Text("LIVE", style = MaterialTheme.typography.labelMedium, color = MX.TextHi)
                if (frame != null) Text("${frame.fps.toInt()} fps · ${if (gpu) "GPU" else "CPU"}", style = MaterialTheme.typography.labelSmall, color = MX.TextLow)
            }
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(MX.GlassDark).border(1.dp, MX.Stroke, CircleShape).clickable(onClick = onRecenter),
                contentAlignment = Alignment.Center,
            ) { Text("⟲", color = MX.Cyan, fontSize = 18.sp, fontWeight = FontWeight.Black) }
        }
        if (touchMode) {
            Text(
                "Touch mode · swipe",
                Modifier.align(Alignment.Center).clip(CircleShape).background(MX.GlassDark).padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall, color = MX.Amber,
            )
        }
        val qColor = when (body.quality) {
            BodyController.Quality.GOOD -> MX.Lime
            BodyController.Quality.PARTIAL -> MX.Amber
            BodyController.Quality.NONE -> MX.Coral
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(10.dp).clip(CircleShape).background(MX.GlassDark)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(qColor))
            Text("  " + body.hint, style = MaterialTheme.typography.labelMedium, color = MX.TextHi, maxLines = 1)
        }
    }
}

/** Compact L/C/R + JUMP/DUCK indicator shown on the game view. */
@Composable
private fun MoveStrip(lane: Int, jumping: Boolean, ducking: Boolean, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(16.dp)).background(MX.GlassDark).border(1.dp, MX.StrokeSoft, RoundedCornerShape(16.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(-1 to "L", 0 to "C", 1 to "R").forEach { (id, s) ->
            val on = lane == id
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(if (on) MX.Cyan else Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) { Text(s, fontWeight = FontWeight.Black, color = if (on) MX.Night else MX.TextLow) }
        }
        Box(Modifier.width(4.dp))
        ActionChip("▲", jumping, MX.Amber, Modifier.width(40.dp))
        ActionChip("▼", ducking, MX.Sky, Modifier.width(40.dp))
    }
}

/** Big pulsing callout ("▲ JUMP") while assist is on. */
@Composable
private fun BoxScope.CueBanner(cue: RunnerGame.Action?, age: Float) {
    if (cue == null || age > 1.6f) return
    val color = when (cue) {
        RunnerGame.Action.JUMP -> MX.Amber
        RunnerGame.Action.DUCK -> MX.Sky
        else -> MX.Coral
    }
    val pop = 1f + 0.25f * (1f - (age / 0.25f).coerceAtMost(1f))
    val alpha = if (age > 1.2f) ((1.6f - age) / 0.4f).coerceIn(0f, 1f) else 1f
    Text(
        cue.label,
        Modifier.align(Alignment.Center).padding(bottom = 90.dp).scale(pop)
            .clip(RoundedCornerShape(20.dp)).background(color.copy(alpha = 0.22f * alpha))
            .border(2.dp, color.copy(alpha = alpha), RoundedCornerShape(20.dp))
            .padding(horizontal = 22.dp, vertical = 10.dp),
        fontSize = 34.sp, fontWeight = FontWeight.Black, color = color.copy(alpha = alpha),
    )
}

@Composable
private fun ActionChip(label: String, on: Boolean, color: Color, modifier: Modifier) {
    val a by animateFloatAsState(if (on) 1f else 0f, tween(90), label = "chip")
    Box(
        modifier.height(34.dp).clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.08f + 0.85f * a))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontWeight = FontWeight.Black, fontSize = 13.sp, color = if (a > 0.5f) MX.Night else color)
    }
}

@Suppress("unused")
private fun Offset.len() = kotlin.math.sqrt(x * x + y * y)
