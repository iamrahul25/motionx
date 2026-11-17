package com.nightwise.motionx.games.gate

import com.nightwise.motionx.games.pose.PoseDef
import com.nightwise.motionx.games.pose.PoseLibrary
import kotlin.math.max
import kotlin.random.Random

enum class GateDifficulty(
    val label: String,
    val emoji: String,
    val lives: Int,
    /** Seconds for a wall to travel from the horizon to you (first wall). */
    val travel: Float,
    /** Judge window: the last N seconds before the wall arrives. */
    val window: Float,
    /** Seconds you must be in the pose inside the window. */
    val needHold: Float,
    /** Extra angle tolerance (degrees) on top of each pose's own tolerance. */
    val tolBonus: Float,
    /** Hole outline thickness (fraction of torso length). */
    val holeWidth: Float,
    /** Constraint forgiveness (hands-near-hips distance, squat depth…). */
    val slack: Float,
    val poseIds: List<String>,
    val desc: String,
) {
    EASY(
        "Easy", "🌱", 5, 7.5f, 2.2f, 0.6f, 8f, 0.46f, 1.25f,
        listOf("stand", "reach", "tpose", "onehand", "hips", "ypose", "goal", "head"),
        "Slow walls · simple shapes · 5 lives",
    ),
    MEDIUM(
        "Medium", "🔥", 3, 5.8f, 1.7f, 0.7f, 4f, 0.40f, 1.12f,
        listOf("reach", "tpose", "onehand", "hips", "ypose", "goal", "head", "chair", "warrior2", "star", "sidebend", "tree", "flamingo", "goddess", "squatT"),
        "Faster walls · yoga shapes · 3 lives",
    ),
    HARD(
        "Hard", "⚡", 3, 4.4f, 1.3f, 0.7f, 0f, 0.34f, 1f,
        listOf("chair", "warrior2", "star", "sidebend", "tree", "flamingo", "goddess", "squatT", "triangle", "legraise", "sideangle", "deepsquat", "halfmoon", "dancer"),
        "Fast walls · balance poses · 3 lives",
    ),
}

/**
 * Pose Gate: walls with a pose-shaped hole approach the player. Match the pose inside the judge window
 * to pass through. A failed wall costs a life and the same shape comes back as a retry.
 * Pure logic — the screen feeds frame time and whether the player currently matches.
 */
class GateGame(val difficulty: GateDifficulty, seed: Long = System.nanoTime()) {

    enum class Phase { WAITING, COUNTDOWN, RUNNING, PAUSED, OVER }
    enum class Outcome { NONE, PASS, PERFECT, FAIL }

    sealed interface Event {
        data class NewWall(val pose: PoseDef, val retry: Boolean) : Event
        data object WindowOpen : Event
        data class Passed(val perfect: Boolean, val points: Int) : Event
        data object Failed : Event
        data object Over : Event
    }

    private val rnd = Random(seed)
    private val pool = difficulty.poseIds.mapNotNull { PoseLibrary.byId(it) }

    var phase = Phase.WAITING
        private set
    var countdown = 0f
        private set

    /** Pose drawn as the hole (possibly the mirrored variant). */
    var pose: PoseDef = pool.first()
        private set
    /** Pose used for matching: tolerance widened per difficulty, either side accepted. */
    var matchPose: PoseDef = pool.first()
        private set
    var retry = false
        private set

    /** 0 = far away, 1 = at the player. */
    var progress = 0f
        private set
    var travel = difficulty.travel
        private set
    var outcome = Outcome.NONE
        private set
    /** Seconds since the current outcome started (drives pass/crash animations). */
    var outcomeT = 0f
        private set
    var held = 0f
        private set
    var matchedNow = false
        private set

    var lives = difficulty.lives
        private set
    var score = 0
        private set
    var combo = 0
        private set
    var bestCombo = 0
        private set
    var walls = 0
        private set
    var passed = 0
        private set
    var perfects = 0
        private set
    var time = 0f
        private set

    val events = ArrayList<Event>()

    val inWindow get() = phase == Phase.RUNNING && outcome == Outcome.NONE && timeToArrive <= difficulty.window
    val timeToArrive get() = (1f - progress) * travel
    val holdProgress get() = (held / difficulty.needHold).coerceIn(0f, 1f)

    private var windowAnnounced = false
    private var lastPoseId: String? = null

    init {
        nextWall(retrySame = false)
    }

    fun startCountdown() {
        if (phase == Phase.WAITING || phase == Phase.PAUSED) {
            phase = Phase.COUNTDOWN; countdown = 3f
        }
    }

    fun pause() {
        if (phase == Phase.RUNNING || phase == Phase.COUNTDOWN) phase = Phase.PAUSED
    }

    fun update(dtIn: Float, matched: Boolean) {
        val dt = dtIn.coerceIn(0f, 0.1f)
        when (phase) {
            Phase.COUNTDOWN -> {
                countdown -= dt
                if (countdown <= 0f) phase = Phase.RUNNING
                return
            }
            Phase.RUNNING -> Unit
            else -> return
        }
        time += dt
        matchedNow = matched

        if (outcome != Outcome.NONE) {
            outcomeT += dt
            if (outcomeT >= OUTCOME_SECONDS) {
                if (lives <= 0) {
                    phase = Phase.OVER; events += Event.Over
                } else {
                    nextWall(retrySame = outcome == Outcome.FAIL)
                }
            }
            return
        }

        progress += dt / travel
        if (inWindow) {
            if (!windowAnnounced) { windowAnnounced = true; events += Event.WindowOpen }
            if (matched) held += dt
        }
        if (progress >= 1f) {
            progress = 1f
            judge()
        }
    }

    private fun judge() {
        walls++
        if (held >= difficulty.needHold) {
            val perfect = held >= difficulty.window * 0.8f
            combo++
            bestCombo = max(bestCombo, combo)
            passed++
            if (perfect) perfects++
            val pts = (100 * (1f + (combo - 1) * 0.1f)).toInt() + if (perfect) 50 else 0
            score += pts
            outcome = if (perfect) Outcome.PERFECT else Outcome.PASS
            events += Event.Passed(perfect, pts)
        } else {
            combo = 0
            lives--
            outcome = Outcome.FAIL
            events += Event.Failed
        }
        outcomeT = 0f
    }

    private fun nextWall(retrySame: Boolean) {
        retry = retrySame
        if (!retrySame) {
            var p: PoseDef
            do { p = pool[rnd.nextInt(pool.size)] } while (pool.size > 1 && p.id == lastPoseId)
            lastPoseId = p.id
            pose = if (p.mirrorOk && rnd.nextBoolean()) p.mirrored() else p
            matchPose = p.copy(tolerance = p.tolerance + difficulty.tolBonus, slack = difficulty.slack)
        }
        // Walls speed up as you pass more of them (down to 65% of the starting travel time).
        travel = difficulty.travel * max(0.65f, 1f - passed * 0.03f)
        progress = 0f
        held = 0f
        outcome = Outcome.NONE
        outcomeT = 0f
        windowAnnounced = false
        events += Event.NewWall(pose, retrySame)
    }

    companion object {
        const val OUTCOME_SECONDS = 1.3f
    }
}
