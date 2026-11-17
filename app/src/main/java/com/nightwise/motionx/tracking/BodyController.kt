package com.nightwise.motionx.tracking

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Turns raw pose landmarks into game input: lane (-1/0/+1), jump events and duck state.
 *
 * All geometry is computed in "height units": y is normalised by image height and x is
 * multiplied by the image aspect ratio, so horizontal and vertical distances are comparable.
 *
 * Flow: [Phase.SEARCHING] → [Phase.CALIBRATING] (stand still in the middle, full body visible)
 * → [Phase.READY]. While ready the baseline slowly adapts to the player drifting closer/farther.
 */
class BodyController(
    private val sensitivity: Sensitivity = Sensitivity(),
) {
    data class Sensitivity(
        /** Hip offset (in shoulder widths) needed to move into a side lane. */
        val laneEnter: Float = 0.75f,
        /** Hip offset (in shoulder widths) below which you are back in the centre lane. */
        val laneExit: Float = 0.45f,
        /** Head rise (fraction of torso length) that counts as a jump. */
        val jump: Float = 0.18f,
        /** Hip rise (fraction of torso length) that must accompany a jump. */
        val jumpHip: Float = 0.08f,
        /**
         * A jump only counts when both hands are raised above the shoulders. Sideways hops also
         * lift the head and hips, so without this they were mistaken for jumps.
         */
        val jumpNeedsHands: Boolean = true,
        /** Hands may go up this long before (or while) the body rises. */
        val handsWindow: Double = 0.45,
        /** Wrists must be this far (fraction of torso) above the shoulder line. */
        val handsAbove: Float = 0.05f,
        /** Head drop (fraction of torso length) that counts as a duck. */
        val duck: Float = 0.32f,
    )

    enum class Phase { SEARCHING, CALIBRATING, READY }
    enum class Quality { NONE, PARTIAL, GOOD }

    data class State(
        val phase: Phase = Phase.SEARCHING,
        val quality: Quality = Quality.NONE,
        val calibration: Float = 0f,
        val hint: String = "Step into view",
        val lane: Int = 0,
        val jumping: Boolean = false,
        val ducking: Boolean = false,
        /** Increments on every new jump (edge-triggered). */
        val jumpSeq: Int = 0,
        val laneSeq: Int = 0,
        val duckSeq: Int = 0,
        // Overlay geometry, normalised image coords (0..1). NaN when unknown.
        val centerX: Float = Float.NaN,
        val laneBoundLeft: Float = Float.NaN,
        val laneBoundRight: Float = Float.NaN,
        val jumpLineY: Float = Float.NaN,
        val duckLineY: Float = Float.NaN,
        val hipX: Float = Float.NaN,
        val headY: Float = Float.NaN,
        val lostSeconds: Float = 0f,
        /** Both wrists currently above the shoulders. */
        val handsUp: Boolean = false,
        /** Y of the "hands above" line (normalised), NaN when unknown. */
        val handLineY: Float = Float.NaN,
    )

    var state = State()
        private set

    private val fHipX = OneEuroFilter(1.2f, 1.2f)
    private val fHipY = OneEuroFilter(1.5f, 1.5f)
    private val fHeadY = OneEuroFilter(1.5f, 1.5f)
    private val fShoulderW = OneEuroFilter(0.5f, 0.0f)
    private val fTorso = OneEuroFilter(0.5f, 0.0f)

    // Baseline (height units)
    private var baseX = 0f
    private var baseHeadY = 0f
    private var baseHipY = 0f
    private var shoulderW = 0f
    private var torso = 0f

    // Calibration accumulators
    private var calStart = -1.0
    private var calN = 0
    private var calX = 0f
    private var calHead = 0f
    private var calHip = 0f
    private var calSw = 0f
    private var calTorso = 0f

    private var lastT = -1.0
    private var lastSeen = -1.0
    private var lastLandT = -10.0
    private var lastHandsUpT = -10.0
    private var lastLaneChangeT = -10.0
    private var lastRiseNoHandsT = -10.0
    private var jumpSeq = 0
    private var laneSeq = 0
    private var duckSeq = 0
    private var lane = 0
    private var jumping = false
    private var ducking = false

    fun recalibrate() {
        state = State(jumpSeq = jumpSeq, laneSeq = laneSeq, duckSeq = duckSeq)
        calStart = -1.0
        lane = 0; jumping = false; ducking = false
        listOf(fHipX, fHipY, fHeadY, fShoulderW, fTorso).forEach { it.reset() }
    }

    /**
     * @param lm packed landmarks (see [PoseFrame]) or null when no person is detected.
     * @param aspect image width / height.
     */
    fun update(lm: FloatArray?, aspect: Float, t: Double): State {
        val dt = if (lastT < 0) 0.033f else (t - lastT).toFloat().coerceIn(0.001f, 0.25f)
        lastT = t

        fun v(i: Int) = lm!![i * 4 + 3]
        fun x(i: Int) = lm!![i * 4] * aspect
        fun y(i: Int) = lm!![i * 4 + 1]

        val coreVisible = lm != null &&
            v(Lm.L_SHOULDER) > 0.5f && v(Lm.R_SHOULDER) > 0.5f &&
            v(Lm.L_HIP) > 0.5f && v(Lm.R_HIP) > 0.5f
        if (!coreVisible) {
            if (lastSeen < 0) lastSeen = t
            val lost = (t - lastSeen).toFloat()
            calStart = -1.0
            state = state.copy(
                quality = if (lm == null) Quality.NONE else Quality.PARTIAL,
                hint = if (lm == null) "Step into view" else "Show your whole upper body",
                lostSeconds = lost,
                calibration = if (state.phase == Phase.READY) state.calibration else 0f,
                phase = if (state.phase == Phase.READY) Phase.READY else Phase.SEARCHING,
                hipX = Float.NaN, headY = Float.NaN,
            )
            return state
        }
        lastSeen = t

        // --- raw measurements (height units) ---
        val hipXRaw = (x(Lm.L_HIP) + x(Lm.R_HIP)) / 2
        val hipYRaw = (y(Lm.L_HIP) + y(Lm.R_HIP)) / 2
        val shYRaw = (y(Lm.L_SHOULDER) + y(Lm.R_SHOULDER)) / 2
        val headRaw = headY(lm!!, shYRaw)
        val swRaw = abs(x(Lm.L_SHOULDER) - x(Lm.R_SHOULDER)).coerceAtLeast(0.02f)
        val torsoRaw = (hipYRaw - shYRaw).coerceAtLeast(0.05f)

        val hipX = fHipX.filter(hipXRaw, t)
        val hipY = fHipY.filter(hipYRaw, t)
        val head = fHeadY.filter(headRaw, t)
        val swF = fShoulderW.filter(swRaw, t)
        val torsoF = fTorso.filter(torsoRaw, t)

        val legsVisible = (v(Lm.L_KNEE) > 0.5f && v(Lm.R_KNEE) > 0.5f) &&
            (v(Lm.L_ANKLE) > 0.4f || v(Lm.R_ANKLE) > 0.4f)
        val headVisible = v(Lm.NOSE) > 0.5f || v(Lm.L_EAR) > 0.5f || v(Lm.R_EAR) > 0.5f
        val quality = if (legsVisible && headVisible) Quality.GOOD else Quality.PARTIAL

        if (state.phase != Phase.READY) {
            calibrate(t, hipX, head, hipY, swF, torsoF, quality, aspect)
            return state
        }

        // --- lane with hysteresis ---
        val off = (hipX - baseX) / shoulderW
        val newLane = when (lane) {
            0 -> if (off < -sensitivity.laneEnter) -1 else if (off > sensitivity.laneEnter) 1 else 0
            -1 -> if (off > sensitivity.laneEnter) 1 else if (off > -sensitivity.laneExit) 0 else -1
            else -> if (off < -sensitivity.laneEnter) -1 else if (off < sensitivity.laneExit) 0 else 1
        }
        if (newLane != lane) {
            lane = newLane; laneSeq++
            lastLaneChangeT = t
        }

        // --- hands: both wrists above the shoulder line ---
        val handLine = shYRaw - sensitivity.handsAbove * torso
        val handsUpNow = v(Lm.L_WRIST) > 0.5f && v(Lm.R_WRIST) > 0.5f &&
            y(Lm.L_WRIST) < handLine && y(Lm.R_WRIST) < handLine
        if (handsUpNow) lastHandsUpT = t
        val handsOk = !sensitivity.jumpNeedsHands || t - lastHandsUpT <= sensitivity.handsWindow

        // --- jump: body rises (head over the jump line, hips up) + hands raised ---
        // Extra guard: ignore rises during a fast sideways move / right after a lane change.
        val lateralSpeed = abs(fHipX.velocity) / shoulderW // shoulder widths per second
        val sideways = lateralSpeed > 3.5f || t - lastLaneChangeT < 0.25
        val jumpLine = baseHeadY - sensitivity.jump * torso
        val hipJumpLine = baseHipY - sensitivity.jumpHip * torso
        if (!jumping) {
            val risen = head < jumpLine && hipY < hipJumpLine && !ducking
            if (risen && handsOk && (!sideways || handsUpNow)) {
                jumping = true; jumpSeq++
            } else if (risen && !handsOk) {
                lastRiseNoHandsT = t
            }
        } else if (head > baseHeadY - 0.08f * torso) {
            jumping = false
            lastLandT = t
        }

        // --- duck: head below duck line (ignored right after landing) ---
        val duckLine = baseHeadY + sensitivity.duck * torso
        if (!ducking) {
            if (head > duckLine && !jumping && t - lastLandT > 0.3) {
                ducking = true; duckSeq++
            }
        } else if (head < baseHeadY + (sensitivity.duck - 0.1f) * torso) {
            ducking = false
        }

        // --- slow baseline adaptation while standing neutral ---
        val neutral = !jumping && !ducking && !handsUpNow && abs(head - baseHeadY) < 0.1f * torso &&
            abs(fHeadY.velocity) < 0.15f
        if (neutral) {
            val k = (dt / 3f).coerceAtMost(1f)
            baseHeadY += (head - baseHeadY) * k
            baseHipY += (hipY - baseHipY) * k
            shoulderW += (swF - shoulderW) * k
            torso += (torsoF - torso) * k
            if (lane == 0 && abs(off) < 0.3f) baseX += (hipX - baseX) * (dt / 8f).coerceAtMost(1f)
        }

        val boundHalf = (sensitivity.laneEnter + sensitivity.laneExit) / 2 * shoulderW
        state = State(
            phase = Phase.READY,
            quality = quality,
            calibration = 1f,
            hint = when {
                ducking -> "Ducking"
                jumping -> "Jump!"
                t - lastRiseNoHandsT < 1.2 -> "Raise both hands to jump"
                handsUpNow -> "Hands up — now jump!"
                else -> "Tracking"
            },
            lane = lane,
            jumping = jumping,
            ducking = ducking,
            jumpSeq = jumpSeq,
            laneSeq = laneSeq,
            duckSeq = duckSeq,
            centerX = baseX / aspect,
            laneBoundLeft = (baseX - boundHalf) / aspect,
            laneBoundRight = (baseX + boundHalf) / aspect,
            jumpLineY = jumpLine,
            duckLineY = duckLine,
            hipX = hipX / aspect,
            headY = head,
            lostSeconds = 0f,
            handsUp = handsUpNow,
            handLineY = handLine,
        )
        return state
    }

    /** Head height: nose, else average of ears/eyes, else estimated from shoulders. */
    private fun headY(lm: FloatArray, shoulderY: Float): Float {
        fun v(i: Int) = lm[i * 4 + 3]
        fun y(i: Int) = lm[i * 4 + 1]
        if (v(Lm.NOSE) > 0.5f) return y(Lm.NOSE)
        var s = 0f
        var n = 0
        for (i in intArrayOf(Lm.L_EYE, Lm.R_EYE, Lm.L_EAR, Lm.R_EAR)) if (v(i) > 0.4f) {
            s += y(i); n++
        }
        return if (n > 0) s / n else shoulderY - 0.1f
    }

    private fun calibrate(
        t: Double, hipX: Float, head: Float, hipY: Float, sw: Float, torsoLen: Float,
        quality: Quality, aspect: Float,
    ) {
        val xNorm = hipX / aspect
        val hint: String? = when {
            quality != Quality.GOOD -> "Step back so your whole body fits"
            // Selfie view is mirrored: appearing on the left of the screen means you are to your left.
            xNorm < 0.3f -> "Move a little to your right"
            xNorm > 0.7f -> "Move a little to your left"
            head < 0.04f -> "Step back a little"
            abs(fHipX.velocity) > 0.25f || abs(fHeadY.velocity) > 0.25f -> "Hold still…"
            else -> null
        }
        if (hint != null) {
            calStart = -1.0
            state = state.copy(phase = Phase.SEARCHING, quality = quality, hint = hint, calibration = 0f, hipX = xNorm, headY = head)
            return
        }
        if (calStart < 0) {
            calStart = t; calN = 0
            calX = 0f; calHead = 0f; calHip = 0f; calSw = 0f; calTorso = 0f
        }
        calN++
        calX += hipX; calHead += head; calHip += hipY; calSw += sw; calTorso += torsoLen
        val progress = min(1f, ((t - calStart) / CAL_SECONDS).toFloat())
        if (progress >= 1f && calN >= 8) {
            baseX = calX / calN
            baseHeadY = calHead / calN
            baseHipY = calHip / calN
            shoulderW = max(calSw / calN, 0.04f)
            torso = calTorso / calN
            lane = 0; jumping = false; ducking = false
            state = state.copy(phase = Phase.READY, quality = quality, calibration = 1f, hint = "Ready!", hipX = xNorm, headY = head)
        } else {
            state = state.copy(phase = Phase.CALIBRATING, quality = quality, calibration = progress, hint = "Hold still…", hipX = xNorm, headY = head)
        }
    }

    companion object {
        const val CAL_SECONDS = 1.4
    }
}
