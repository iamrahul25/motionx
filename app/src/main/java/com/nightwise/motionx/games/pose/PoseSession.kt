package com.nightwise.motionx.games.pose

/**
 * Runs a list of poses one after another: intro → match & hold → passed/failed → next.
 * Pure logic; the screen feeds it [PoseMatcher.Result]s and frame time.
 */
class PoseSession(val poses: List<PoseDef>, val timeLimit: Float = 20f) {

    enum class Phase { INTRO, MATCHING, PASSED, FAILED, DONE }

    data class PoseResult(val pose: PoseDef, val passed: Boolean, val seconds: Float, val stars: Int, val points: Int)

    sealed interface Event {
        data class NewPose(val pose: PoseDef) : Event
        data class Passed(val result: PoseResult) : Event
        data class Failed(val pose: PoseDef) : Event
        data object Finished : Event
        data object Matching : Event
    }

    var index = 0
        private set
    var phase = Phase.INTRO
        private set
    var phaseTime = 0f
        private set
    /** Seconds spent trying the current pose. */
    var elapsed = 0f
        private set
    /** Seconds the pose has been held (with a small grace when tracking flickers). */
    var hold = 0f
        private set
    var lastScore = 0f
        private set
    var matchedNow = false
        private set
    private var lostFor = 0f
    private var bestScore = 0f

    val results = ArrayList<PoseResult>()
    val events = ArrayList<Event>()

    val current: PoseDef? get() = poses.getOrNull(index)
    val holdProgress get() = current?.let { (hold / it.hold).coerceIn(0f, 1f) } ?: 0f
    val timeLeft get() = (timeLimit - elapsed).coerceAtLeast(0f)
    val totalPoints get() = results.sumOf { it.points }
    val totalStars get() = results.sumOf { it.stars }
    val passedCount get() = results.count { it.passed }

    init {
        current?.let { events += Event.NewPose(it) }
    }

    fun update(dt: Float, result: PoseMatcher.Result?) {
        val pose = current ?: return
        phaseTime += dt
        when (phase) {
            Phase.INTRO -> if (phaseTime >= INTRO_SECONDS) {
                phase = Phase.MATCHING; phaseTime = 0f; events += Event.Matching
            }
            Phase.MATCHING -> {
                elapsed += dt
                matchedNow = result?.matched == true
                lastScore = result?.score ?: 0f
                if (lastScore > bestScore) bestScore = lastScore
                if (matchedNow) {
                    lostFor = 0f
                    hold += dt
                } else {
                    lostFor += dt
                    if (lostFor > GRACE) hold = (hold - dt * 1.5f).coerceAtLeast(0f)
                }
                if (hold >= pose.hold) {
                    val stars = when {
                        elapsed <= pose.hold + 5f -> 3
                        elapsed <= pose.hold + 11f -> 2
                        else -> 1
                    }
                    val r = PoseResult(pose, true, elapsed, stars, pose.tier.points * stars)
                    results += r
                    events += Event.Passed(r)
                    phase = Phase.PASSED; phaseTime = 0f
                } else if (elapsed >= timeLimit) {
                    results += PoseResult(pose, false, elapsed, 0, 0)
                    events += Event.Failed(pose)
                    phase = Phase.FAILED; phaseTime = 0f
                }
            }
            Phase.PASSED, Phase.FAILED -> if (phaseTime >= OUTRO_SECONDS) next()
            Phase.DONE -> Unit
        }
    }

    /** Skip the current pose (counts as not passed). */
    fun skip() {
        val pose = current ?: return
        if (phase == Phase.MATCHING || phase == Phase.INTRO) {
            results += PoseResult(pose, false, elapsed, 0, 0)
            events += Event.Failed(pose)
            phase = Phase.FAILED; phaseTime = OUTRO_SECONDS * 0.6f
        }
    }

    private fun next() {
        index++
        elapsed = 0f; hold = 0f; lostFor = 0f; bestScore = 0f; matchedNow = false
        phaseTime = 0f
        val p = current
        if (p == null) {
            phase = Phase.DONE
            events += Event.Finished
        } else {
            phase = Phase.INTRO
            events += Event.NewPose(p)
        }
    }

    companion object {
        const val INTRO_SECONDS = 1.6f
        const val OUTRO_SECONDS = 1.5f
        const val GRACE = 0.3f
    }
}
