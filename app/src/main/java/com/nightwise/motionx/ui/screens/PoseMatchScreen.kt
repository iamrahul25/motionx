package com.nightwise.motionx.ui.screens

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nightwise.motionx.audio.Sfx
import com.nightwise.motionx.audio.VoiceCoach
import com.nightwise.motionx.data.Prefs
import com.nightwise.motionx.games.pose.PoseCameraView
import com.nightwise.motionx.games.pose.PoseDef
import com.nightwise.motionx.games.pose.PoseLibrary
import com.nightwise.motionx.games.pose.PoseMannequin
import com.nightwise.motionx.games.pose.PoseMatcher
import com.nightwise.motionx.games.pose.PoseSession
import com.nightwise.motionx.games.pose.Tier
import com.nightwise.motionx.tracking.PoseTracker
import com.nightwise.motionx.ui.components.*
import com.nightwise.motionx.ui.theme.MX
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicReference

@Composable
fun PoseMatchScreen(
    tier: Tier,
    startIndex: Int,
    prefs: Prefs,
    sfx: Sfx,
    onExit: () -> Unit,
    onPlayTier: (Tier) -> Unit,
) {
    val activity = LocalContext.current as ComponentActivity
    ImmersiveGameMode(activity)
    val cam = rememberCameraTracking(activity)
    val tracker = cam.tracker
    val frame by tracker.frames.collectAsState()
    val status by tracker.status.collectAsState()

    val coach = remember { VoiceCoach(activity) }
    DisposableEffect(Unit) { onDispose { coach.shutdown() } }
    fun say(s: String) { if (prefs.sound) coach.say(s) }

    var runId by remember { mutableIntStateOf(0) }
    val session = remember(runId) { PoseSession(PoseLibrary.tier(tier).drop(startIndex.coerceAtLeast(0))) }

    // Live matching on the inference thread against the current pose.
    val currentPose = remember { AtomicReference<PoseDef?>(null) }
    val resultFlow = remember { MutableStateFlow<Pair<PoseMatcher.Result?, Long>>(null to 0L) }
    LaunchedEffect(cam.hasPermission) {
        tracker.onFrame = { f ->
            val p = currentPose.get()
            val lm = f.landmarks
            val r = if (p != null && lm != null) PoseMatcher.evaluate(p, lm, f.width.toFloat() / f.height) else null
            resultFlow.value = r to System.nanoTime()
        }
    }
    val result by resultFlow.collectAsState()

    var paused by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var done by remember(runId) { mutableStateOf(false) }
    var flash by remember { mutableStateOf<String?>(null) }

    // Session loop driven by display frames.
    LaunchedEffect(session) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.1f)
                last = now
                currentPose.set(session.current)
                if (!paused && !done) {
                    val (r, at) = resultFlow.value
                    val fresh = r != null && System.nanoTime() - at < 600_000_000L && r.variant.id == session.current?.id
                    session.update(dt, if (fresh) r else null)
                    for (e in session.events) when (e) {
                        is PoseSession.Event.NewPose -> { say(e.pose.name); flash = null }
                        PoseSession.Event.Matching -> sfx.play(Sfx.S.TICK)
                        is PoseSession.Event.Passed -> {
                            sfx.play(Sfx.S.STAR); sfx.buzz(40, 120)
                            prefs.recordPoseStars(e.result.pose.id, e.result.stars)
                            flash = when (e.result.stars) { 3 -> "PERFECT"; 2 -> "GREAT"; else -> "NICE" }
                            say(flash!!.lowercase() + "!")
                        }
                        is PoseSession.Event.Failed -> { sfx.play(Sfx.S.DUCK); flash = "TIME'S UP" }
                        PoseSession.Event.Finished -> {
                            done = true
                            sfx.play(Sfx.S.OVER)
                            val all = PoseLibrary.tier(tier)
                            val passedAll = all.count { prefs.poseStars(it.id) > 0 }
                            if (passedAll * 2 >= all.size) prefs.poseUnlockedTier = tier.ordinal + 1
                        }
                    }
                    session.events.clear()
                }
                tick++
            }
        }
    }

    BackHandler { if (done) onExit() else paused = !paused }

    val frameTick = tick // read every frame so timers recompose
    if (frameTick < 0) return
    val pose = session.current
    val r = result.first?.takeIf { pose != null && it.variant.id == pose.id }
    val accent = tierColor(tier)

    Row(Modifier.fillMaxSize().background(MX.Night)) {
        // ---------------- left: target ----------------
        Column(Modifier.weight(1f).fillMaxHeight().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BackButton({ paused = true })
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(tier.label.uppercase(), style = MaterialTheme.typography.labelMedium, color = accent)
                    Text(
                        "Pose ${(session.index + 1).coerceAtMost(session.poses.size)} / ${session.poses.size}",
                        style = MaterialTheme.typography.titleMedium, color = MX.TextHi,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("%,d".format(session.totalPoints), fontSize = 22.sp, color = MX.TextHi, fontWeight = FontWeight.Bold)
                    IconStat(Ico.STAR, MX.Yellow, "${session.totalStars}")
                }
            }
            VSpace(6.dp)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (pose != null) {
                    val target = r?.variant ?: pose
                    AnimatedContent(target, transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.85f)) togetherWith fadeOut() }, label = "pose") { p ->
                        PoseMannequin(p, Modifier.fillMaxSize().padding(8.dp), color = accent, progress = session.holdProgress)
                    }
                    HoldRing(session.holdProgress, pose.hold, session.matchedNow, Modifier.align(Alignment.BottomEnd).size(84.dp))
                    if (session.phase == PoseSession.Phase.INTRO) {
                        Text(
                            "GET READY", Modifier.align(Alignment.TopCenter).clip(CircleShape).background(accent)
                                .padding(horizontal = 14.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelLarge, color = Color.Black,
                        )
                    }
                }
            }
            if (pose != null) {
                Text(pose.name, style = MaterialTheme.typography.headlineMedium, color = MX.TextHi, maxLines = 1)
                Text(pose.cue, style = MaterialTheme.typography.bodyMedium, color = MX.TextMid, maxLines = 2)
                VSpace(8.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val frac = session.timeLeft / session.timeLimit
                    Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(MX.SurfaceHi)) {
                        Box(
                            Modifier.fillMaxHeight().fillMaxWidth(frac).clip(CircleShape)
                                .background(if (frac > 0.3f) accent else MX.Coral),
                        )
                    }
                    Text(" ${session.timeLeft.toInt()}s", style = MaterialTheme.typography.labelLarge, color = MX.TextMid)
                    Box(Modifier.width(10.dp))
                    Text(
                        "Skip", style = MaterialTheme.typography.labelLarge, color = MX.TextLow,
                        modifier = Modifier.clip(CircleShape).clickable { session.skip() }.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }

        // ---------------- right: live camera ----------------
        Box(Modifier.fillMaxHeight().aspectRatio(4f / 3f)) {
            PoseCameraView(frame, r, Modifier.fillMaxSize())
            Row(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                LiveDot(status == PoseTracker.Status.RUNNING)
                Text("  LIVE", style = MaterialTheme.typography.labelMedium, color = MX.TextHi, modifier = Modifier.weight(1f))
                if (r != null && session.phase == PoseSession.Phase.MATCHING) {
                    val pct = (r.score * 100).toInt()
                    Text(
                        "MATCH $pct%",
                        Modifier.clip(CircleShape).background(if (r.matched) MX.Lime else MX.GlassDark)
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelLarge, color = if (r.matched) MX.Night else MX.TextHi,
                    )
                }
            }
            val hint = when {
                !cam.hasPermission -> "Camera permission needed"
                status == PoseTracker.Status.ERROR -> tracker.errorMessage ?: "Camera error"
                frame?.landmarks == null -> "Step into view — whole body visible"
                r != null -> r.hint
                else -> "Tracking"
            }
            Text(
                hint,
                Modifier.align(Alignment.BottomCenter).padding(12.dp).clip(CircleShape).background(MX.GlassDark)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                style = MaterialTheme.typography.titleMedium, color = if (r?.matched == true) MX.Lime else MX.TextHi,
            )
            Legend(Modifier.align(Alignment.BottomStart).padding(12.dp, 12.dp, 12.dp, 56.dp))
            if (!cam.hasPermission) {
                GlowButton("Allow camera", Modifier.align(Alignment.Center).width(220.dp), onClick = cam.request)
            }
            val f = flash
            if (f != null && (session.phase == PoseSession.Phase.PASSED || session.phase == PoseSession.Phase.FAILED)) {
                FlashBanner(f, session.results.lastOrNull()?.stars ?: 0)
            }
        }
    }

    if (paused && !done) PauseCard(onResume = { paused = false }, onSkip = { session.skip(); paused = false }, onQuit = onExit)
    if (done) {
        val unlockedNext = prefs.poseUnlockedTier > tier.ordinal && tier.ordinal < Tier.entries.size - 1
        TierResults(
            session, tier,
            nextTier = if (unlockedNext) Tier.entries[tier.ordinal + 1] else null,
            onReplay = { runId++ },
            onNext = { t -> onPlayTier(t) },
            onMap = onExit,
        )
    }
}

@Composable
private fun LiveDot(on: Boolean) {
    val t = rememberInfiniteTransition(label = "live")
    val a by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a")
    Box(Modifier.size(9.dp).clip(CircleShape).background(MX.Coral.copy(alpha = if (on) a else 0.3f)))
}

@Composable
private fun Legend(modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(MX.GlassDark).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        listOf(MX.Lime to "Matched", MX.Amber to "Close", MX.Coral to "Adjust").forEach { (c, s) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 14.dp, height = 4.dp).clip(CircleShape).background(c))
                Text("  $s", style = MaterialTheme.typography.labelSmall, color = MX.TextMid)
            }
        }
    }
}

@Composable
private fun HoldRing(progress: Float, holdSeconds: Float, matched: Boolean, modifier: Modifier) {
    val p by animateFloatAsState(progress, tween(90), label = "hold")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = size.minDimension * 0.1f
            drawCircle(Color.Black.copy(alpha = 0.7f))
            drawCircle((if (matched) MX.Lime else MX.Amber).copy(alpha = 0.18f), radius = size.minDimension / 2 - sw / 2, style = Stroke(sw))
            drawArc(
                if (matched) MX.Lime else MX.Amber, -90f, 360f * p, false,
                topLeft = androidx.compose.ui.geometry.Offset(sw / 2, sw / 2),
                size = androidx.compose.ui.geometry.Size(size.width - sw, size.height - sw),
                style = Stroke(sw, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("%.1f".format((holdSeconds * (1 - p)).coerceAtLeast(0f)), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MX.TextHi)
            Text("HOLD", style = MaterialTheme.typography.labelSmall, color = MX.TextLow)
        }
    }
}

@Composable
private fun BoxScope.FlashBanner(text: String, stars: Int) {
    val failed = text.startsWith("TIME")
    val c = if (failed) MX.Coral else MX.Lime
    Box(Modifier.fillMaxSize().background(c.copy(alpha = 0.12f)))
    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, fontSize = 54.sp, fontWeight = FontWeight.Bold, color = c)
        if (!failed) Stars(stars, max = 3, size = 40.dp)
        else Text("Next pose…", style = MaterialTheme.typography.titleLarge, color = MX.TextHi)
    }
}

@Composable
private fun PauseCard(onResume: () -> Unit, onSkip: () -> Unit, onQuit: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 420.dp).clip(RoundedCornerShape(28.dp))
                .background(MX.Surface).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Paused", style = MaterialTheme.typography.displayMedium, color = MX.TextHi)
            VSpace(14.dp)
            GlowButton("Resume", Modifier.fillMaxWidth(), leading = Ico.PLAY, onClick = onResume)
            VSpace(10.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton("Skip pose", Modifier.weight(1f), height = 46.dp, onClick = onSkip)
                GhostButton("Quit", Modifier.weight(1f), height = 46.dp, accent = MX.Coral, onClick = onQuit)
            }
        }
    }
}

@Composable
private fun TierResults(
    session: PoseSession,
    tier: Tier,
    nextTier: Tier?,
    onReplay: () -> Unit,
    onNext: (Tier) -> Unit,
    onMap: () -> Unit,
) {
    val accent = tierColor(tier)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 640.dp).padding(14.dp).clip(RoundedCornerShape(28.dp))
                .background(MX.Surface)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("${tier.label.uppercase()} COMPLETE", style = MaterialTheme.typography.labelLarge, color = accent)
            Text("%,d".format(session.totalPoints), style = MaterialTheme.typography.displayLarge, color = MX.TextHi)
            Text(
                "${session.passedCount}/${session.poses.size} poses · ${session.totalStars}/${session.poses.size * 3} stars",
                style = MaterialTheme.typography.titleMedium, color = MX.TextMid,
            )
            VSpace(12.dp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                session.results.forEach { res ->
                    Column(
                        Modifier.width(78.dp).clip(RoundedCornerShape(14.dp))
                            .background(MX.SurfaceHi)
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PoseMannequin(res.pose, Modifier.size(56.dp), color = if (res.passed) accent else MX.Coral, animate = false)
                        Text(res.pose.name, style = MaterialTheme.typography.labelSmall, color = MX.TextHi, maxLines = 1, textAlign = TextAlign.Center)
                        if (res.passed) Stars(res.stars, max = 3, size = 10.dp) else Icon(Ico.CLOSE, MX.Coral, size = 12.dp)
                    }
                }
            }
            if (nextTier != null) {
                VSpace(10.dp)
                Pill("${nextTier.label} unlocked", tierColor(nextTier), icon = Ico.UNLOCK)
            }
            VSpace(14.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton("Map", Modifier.weight(1f), onClick = onMap)
                GhostButton("Replay", Modifier.weight(1f), leading = Ico.REPLAY, onClick = onReplay)
                if (nextTier != null) {
                    GlowButton("Next tier", Modifier.weight(1.4f), height = 50.dp, leading = Ico.PLAY, color = accent) { onNext(nextTier) }
                }
            }
        }
    }
}
