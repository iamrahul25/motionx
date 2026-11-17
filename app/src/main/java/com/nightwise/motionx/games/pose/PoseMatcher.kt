package com.nightwise.motionx.games.pose

import com.nightwise.motionx.tracking.Lm
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** 2D stick figure built from a [PoseDef] (units: torso lengths, y down). Used for drawing + tests. */
class PoseFigure(val joints: Map<Joint, FloatArray>, val headCenter: FloatArray, val headR: Float, val free: Set<Bone>) {
    companion object {
        private const val UPPER_ARM = 0.55f
        private const val FOREARM = 0.5f
        private const val THIGH = 0.78f
        private const val SHIN = 0.74f

        /** Neutral angles for bones a pose doesn't care about. */
        private val DEFAULTS = mapOf(
            Bone.TORSO to -90f,
            Bone.SL_UPPER_ARM to 100f, Bone.SL_FOREARM to 95f, Bone.SR_UPPER_ARM to 80f, Bone.SR_FOREARM to 85f,
            Bone.SL_THIGH to 93f, Bone.SL_SHIN to 90f, Bone.SR_THIGH to 87f, Bone.SR_SHIN to 90f,
        )

        fun build(def: PoseDef): PoseFigure {
            fun ang(b: Bone) = def.angles[b] ?: DEFAULTS.getValue(b)
            fun dir(deg: Float): FloatArray {
                val r = Math.toRadians(deg.toDouble())
                return floatArrayOf(cos(r).toFloat(), sin(r).toFloat())
            }
            fun add(p: FloatArray, d: FloatArray, len: Float) = floatArrayOf(p[0] + d[0] * len, p[1] + d[1] * len)

            val j = HashMap<Joint, FloatArray>()
            val hip = floatArrayOf(0f, 0f)
            val t = dir(ang(Bone.TORSO))
            val sm = add(hip, t, 1f)
            // "screen-left" perpendicular of the torso; hips stay level.
            val perp = floatArrayOf(t[1], -t[0])
            val shW = if (def.sideView) 0.05f else 0.24f
            val hipW = if (def.sideView) 0.04f else 0.13f
            j[Joint.SL_SHOULDER] = add(sm, perp, shW)
            j[Joint.SR_SHOULDER] = add(sm, perp, -shW)
            j[Joint.SL_HIP] = floatArrayOf(-hipW, 0f)
            j[Joint.SR_HIP] = floatArrayOf(hipW, 0f)
            j[Joint.SL_ELBOW] = add(j[Joint.SL_SHOULDER]!!, dir(ang(Bone.SL_UPPER_ARM)), UPPER_ARM)
            j[Joint.SL_WRIST] = add(j[Joint.SL_ELBOW]!!, dir(ang(Bone.SL_FOREARM)), FOREARM)
            j[Joint.SR_ELBOW] = add(j[Joint.SR_SHOULDER]!!, dir(ang(Bone.SR_UPPER_ARM)), UPPER_ARM)
            j[Joint.SR_WRIST] = add(j[Joint.SR_ELBOW]!!, dir(ang(Bone.SR_FOREARM)), FOREARM)
            j[Joint.SL_KNEE] = add(j[Joint.SL_HIP]!!, dir(ang(Bone.SL_THIGH)), THIGH * def.legScaleSL)
            j[Joint.SL_ANKLE] = add(j[Joint.SL_KNEE]!!, dir(ang(Bone.SL_SHIN)), SHIN)
            j[Joint.SR_KNEE] = add(j[Joint.SR_HIP]!!, dir(ang(Bone.SR_THIGH)), THIGH * def.legScaleSR)
            j[Joint.SR_ANKLE] = add(j[Joint.SR_KNEE]!!, dir(ang(Bone.SR_SHIN)), SHIN)
            val head = add(sm, t, 0.36f)
            j[Joint.NOSE] = head
            val free = Bone.entries.filter { it !in def.angles }.toSet()
            return PoseFigure(j, head, 0.17f, free)
        }

        /** Converts a figure into packed MediaPipe-style landmarks (for tests / previews). */
        fun toLandmarks(fig: PoseFigure, aspect: Float = 4f / 3f, scale: Float = 0.22f): FloatArray {
            val out = FloatArray(33 * 4)
            val idx = mapOf(
                Joint.NOSE to Lm.NOSE,
                Joint.SL_SHOULDER to Lm.L_SHOULDER, Joint.SR_SHOULDER to Lm.R_SHOULDER,
                Joint.SL_ELBOW to Lm.L_ELBOW, Joint.SR_ELBOW to Lm.R_ELBOW,
                Joint.SL_WRIST to Lm.L_WRIST, Joint.SR_WRIST to Lm.R_WRIST,
                Joint.SL_HIP to Lm.L_HIP, Joint.SR_HIP to Lm.R_HIP,
                Joint.SL_KNEE to Lm.L_KNEE, Joint.SR_KNEE to Lm.R_KNEE,
                Joint.SL_ANKLE to Lm.L_ANKLE, Joint.SR_ANKLE to Lm.R_ANKLE,
            )
            for ((jt, i) in idx) {
                val p = fig.joints.getValue(jt)
                out[i * 4] = (0.5f * aspect + p[0] * scale) / aspect
                out[i * 4 + 1] = 0.55f + p[1] * scale
                out[i * 4 + 3] = 0.99f
            }
            return out
        }
    }
}

/**
 * Compares live landmarks against a [PoseDef] using bone *directions* (independent of body size and
 * distance), plus optional joint-distance constraints. Tries both left/right label assignments and,
 * for asymmetric poses, the mirrored pose — whichever fits best wins.
 */
object PoseMatcher {

    class Result(
        val matched: Boolean,
        /** 0..1 overall similarity for the UI. */
        val score: Float,
        /** Per bone (by [Bone.ordinal]): angular error in degrees, NaN = not visible / free. */
        val errors: FloatArray,
        val tolerance: Float,
        /** Per bone: landmark index pair used (torso uses -1 = mid points). */
        val boneLandmarks: Array<IntArray>,
        val required: Set<Bone>,
        val constraintsOk: Boolean,
        val hint: String,
        /** The pose variant that matched best (mirrored or not) — draw this one as the target. */
        val variant: PoseDef,
    ) {
        fun status(b: Bone): Int {
            if (b !in required) return -1
            val e = errors[b.ordinal]
            return when {
                e.isNaN() -> -1
                e <= tolerance -> 2
                e <= tolerance * 1.7f -> 1
                else -> 0
            }
        }
    }

    private val SL = intArrayOf(Lm.L_SHOULDER, Lm.L_ELBOW, Lm.L_WRIST, Lm.L_HIP, Lm.L_KNEE, Lm.L_ANKLE)
    private val SR = intArrayOf(Lm.R_SHOULDER, Lm.R_ELBOW, Lm.R_WRIST, Lm.R_HIP, Lm.R_KNEE, Lm.R_ANKLE)

    private fun pairs(swap: Boolean): Array<IntArray> {
        val a = if (swap) SR else SL
        val b = if (swap) SL else SR
        return arrayOf(
            intArrayOf(-1, -1),
            intArrayOf(a[0], a[1]), intArrayOf(a[1], a[2]),
            intArrayOf(b[0], b[1]), intArrayOf(b[1], b[2]),
            intArrayOf(a[3], a[4]), intArrayOf(a[4], a[5]),
            intArrayOf(b[3], b[4]), intArrayOf(b[4], b[5]),
        )
    }

    private fun jointIndex(j: Joint, swap: Boolean): Int {
        val a = if (swap) SR else SL
        val b = if (swap) SL else SR
        return when (j) {
            Joint.NOSE -> Lm.NOSE
            Joint.SL_SHOULDER -> a[0]; Joint.SL_ELBOW -> a[1]; Joint.SL_WRIST -> a[2]
            Joint.SL_HIP -> a[3]; Joint.SL_KNEE -> a[4]; Joint.SL_ANKLE -> a[5]
            Joint.SR_SHOULDER -> b[0]; Joint.SR_ELBOW -> b[1]; Joint.SR_WRIST -> b[2]
            Joint.SR_HIP -> b[3]; Joint.SR_KNEE -> b[4]; Joint.SR_ANKLE -> b[5]
        }
    }

    fun angDiff(a: Float, b: Float): Float {
        var d = abs(a - b) % 360f
        if (d > 180f) d = 360f - d
        return d
    }

    fun evaluate(def: PoseDef, lm: FloatArray, aspect: Float): Result {
        fun x(i: Int) = lm[i * 4] * aspect
        fun y(i: Int) = lm[i * 4 + 1]
        fun v(i: Int) = lm[i * 4 + 3]

        val hipX = (x(Lm.L_HIP) + x(Lm.R_HIP)) / 2
        val hipY = (y(Lm.L_HIP) + y(Lm.R_HIP)) / 2
        val shX = (x(Lm.L_SHOULDER) + x(Lm.R_SHOULDER)) / 2
        val shY = (y(Lm.L_SHOULDER) + y(Lm.R_SHOULDER)) / 2
        val torso = hypot(shX - hipX, shY - hipY).coerceAtLeast(0.02f)
        val torsoVisible = v(Lm.L_HIP) > 0.3f && v(Lm.R_HIP) > 0.3f && v(Lm.L_SHOULDER) > 0.3f && v(Lm.R_SHOULDER) > 0.3f

        val variants = if (def.mirrorOk) listOf(def, def.mirrored()) else listOf(def)
        var best: Result? = null
        var bestCost = Float.MAX_VALUE
        for (variant in variants) for (swap in booleanArrayOf(false, true)) {
            val pr = pairs(swap)
            val errors = FloatArray(Bone.entries.size) { Float.NaN }
            var cost = 0f
            var allOk = true
            var anyHidden = false
            var worst: Bone? = null
            var worstErr = 0f
            for (b in Bone.entries) {
                val target = variant.angles[b] ?: continue
                val measured: Float? = if (b == Bone.TORSO) {
                    if (torsoVisible) Math.toDegrees(atan2((shY - hipY).toDouble(), (shX - hipX).toDouble())).toFloat() else null
                } else {
                    val (i0, i1) = pr[b.ordinal].let { it[0] to it[1] }
                    if (v(i0) > 0.3f && v(i1) > 0.3f) {
                        Math.toDegrees(atan2((y(i1) - y(i0)).toDouble(), (x(i1) - x(i0)).toDouble())).toFloat()
                    } else null
                }
                if (measured == null) {
                    anyHidden = true; allOk = false; cost += 90f
                    continue
                }
                val e = angDiff(measured, target)
                errors[b.ordinal] = e
                cost += e
                if (e > def.tolerance) {
                    allOk = false
                    if (e > worstErr) { worstErr = e; worst = b }
                }
            }
            // constraints
            var consOk = true
            var consHint: String? = null
            for (c in variant.constraints) {
                val ok = when (c) {
                    is Constraint.Near -> {
                        val a = jointIndex(c.a, swap)
                        val bb = jointIndex(c.b, swap)
                        v(a) > 0.3f && v(bb) > 0.3f && hypot(x(a) - x(bb), y(a) - y(bb)) / torso <= c.max * def.slack
                    }
                    is Constraint.LegBent -> {
                        val h = jointIndex(if (c.sl) Joint.SL_HIP else Joint.SR_HIP, swap)
                        val k = jointIndex(if (c.sl) Joint.SL_KNEE else Joint.SR_KNEE, swap)
                        val an = jointIndex(if (c.sl) Joint.SL_ANKLE else Joint.SR_ANKLE, swap)
                        val shin = hypot(x(an) - x(k), y(an) - y(k)).coerceAtLeast(1e-3f)
                        v(h) > 0.3f && v(k) > 0.3f && v(an) > 0.3f && hypot(x(k) - x(h), y(k) - y(h)) / shin <= c.maxRatio * def.slack
                    }
                }
                if (!ok) {
                    consOk = false
                    if (consHint == null) consHint = when (c) {
                        is Constraint.Near -> c.hint
                        is Constraint.LegBent -> c.hint
                    }
                    cost += 25f
                }
            }
            if (cost < bestCost) {
                bestCost = cost
                val required = variant.angles.keys
                var s = 0f
                for (b in required) {
                    val e = errors[b.ordinal]
                    s += if (e.isNaN()) 0f else (1f - (e - def.tolerance * 0.4f).coerceAtLeast(0f) / (def.tolerance * 2.2f)).coerceIn(0f, 1f)
                }
                var score = if (required.isEmpty()) 1f else s / required.size
                if (!consOk) score *= 0.85f
                val matched = allOk && consOk
                val hint = when {
                    matched -> "Hold it!"
                    anyHidden -> "Show your whole body"
                    worst != null -> "Adjust your ${worst.label.lowercase()}"
                    consHint != null -> consHint
                    else -> "Almost there"
                }
                best = Result(matched, score, errors, def.tolerance, pr, required, consOk, hint, variant)
            }
        }
        return best!!
    }
}
