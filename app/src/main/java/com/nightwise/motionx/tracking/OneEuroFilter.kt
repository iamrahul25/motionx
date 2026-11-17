package com.nightwise.motionx.tracking

import kotlin.math.PI
import kotlin.math.abs

/**
 * One Euro filter (Casiez et al. 2012): heavy smoothing when the signal is still,
 * little lag when it moves fast. Ideal for pose landmarks.
 */
class OneEuroFilter(
    private val minCutoff: Float = 1.5f,
    private val beta: Float = 0.8f,
    private val dCutoff: Float = 1.0f,
) {
    private var x = 0f
    private var dx = 0f
    private var lastT = -1.0
    var initialised = false
        private set

    private fun alpha(cutoff: Float, dt: Float): Float {
        val tau = 1f / (2f * PI.toFloat() * cutoff)
        return 1f / (1f + tau / dt)
    }

    fun filter(value: Float, tSeconds: Double): Float {
        if (!initialised || lastT < 0) {
            x = value; dx = 0f; lastT = tSeconds; initialised = true
            return value
        }
        val dt = (tSeconds - lastT).toFloat().coerceAtLeast(1e-3f)
        lastT = tSeconds
        val rawDx = (value - x) / dt
        dx += alpha(dCutoff, dt) * (rawDx - dx)
        val cutoff = minCutoff + beta * abs(dx)
        x += alpha(cutoff, dt) * (value - x)
        return x
    }

    /** Current derivative estimate (units / second). */
    val velocity get() = dx

    fun reset() {
        initialised = false
        lastT = -1.0
    }
}
