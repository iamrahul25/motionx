package com.nightwise.motionx

import com.nightwise.motionx.games.runner.Moves
import com.nightwise.motionx.games.runner.RunnerConfig
import com.nightwise.motionx.games.runner.RunnerGame
import com.nightwise.motionx.games.runner.SpeedMode
import com.nightwise.motionx.tracking.BodyController
import com.nightwise.motionx.tracking.Lm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyControllerTest {
    private val aspect = 4f / 3f

    /** Synthetic standing person: hip centre at [cx], everything shifted down by [dy]. */
    private fun pose(cx: Float, dy: Float = 0f, headDrop: Float = 0f, handsUp: Boolean = false): FloatArray {
        val a = FloatArray(33 * 4)
        fun set(i: Int, x: Float, y: Float) {
            a[i * 4] = x; a[i * 4 + 1] = y + dy; a[i * 4 + 3] = 0.99f
        }
        val sw = 0.06f
        set(Lm.NOSE, cx, 0.20f + headDrop)
        set(Lm.L_EAR, cx - 0.02f, 0.20f + headDrop); set(Lm.R_EAR, cx + 0.02f, 0.20f + headDrop)
        set(Lm.L_SHOULDER, cx - sw, 0.30f + headDrop); set(Lm.R_SHOULDER, cx + sw, 0.30f + headDrop)
        set(Lm.L_HIP, cx - 0.04f, 0.55f); set(Lm.R_HIP, cx + 0.04f, 0.55f)
        val wy = if (handsUp) 0.10f + headDrop else 0.52f
        set(Lm.L_WRIST, cx - 0.09f, wy); set(Lm.R_WRIST, cx + 0.09f, wy)
        set(Lm.L_KNEE, cx - 0.04f, 0.72f); set(Lm.R_KNEE, cx + 0.04f, 0.72f)
        set(Lm.L_ANKLE, cx - 0.04f, 0.90f); set(Lm.R_ANKLE, cx + 0.04f, 0.90f)
        return a
    }

    private fun feed(c: BodyController, p: FloatArray, from: Double, seconds: Double): Double {
        var t = from
        while (t < from + seconds) {
            c.update(p, aspect, t); t += 1.0 / 30
        }
        return t
    }

    private fun calibrated(): Pair<BodyController, Double> {
        val c = BodyController()
        val t = feed(c, pose(0.5f), 0.0, 2.0)
        assertEquals(BodyController.Phase.READY, c.state.phase)
        return c to t
    }

    @Test
    fun calibratesWhenStandingStillInMiddle() {
        calibrated()
    }

    @Test
    fun hoppingSidewaysChangesLane() {
        val (c, t0) = calibrated()
        var t = feed(c, pose(0.38f), t0, 0.6) // moved left on the mirrored image
        assertEquals(-1, c.state.lane)
        t = feed(c, pose(0.5f), t, 0.6)
        assertEquals(0, c.state.lane)
        feed(c, pose(0.62f), t, 0.6)
        assertEquals(1, c.state.lane)
    }

    @Test
    fun jumpIsDetectedOnceAndDuckIsHeld() {
        val (c, t0) = calibrated()
        val before = c.state.jumpSeq
        var t = feed(c, pose(0.5f, handsUp = true), t0, 0.3) // hands up first
        t = feed(c, pose(0.5f, dy = -0.08f, handsUp = true), t, 0.2)
        assertTrue(c.state.jumping)
        t = feed(c, pose(0.5f), t, 0.6)
        assertEquals(before + 1, c.state.jumpSeq)
        t = feed(c, pose(0.5f, headDrop = 0.15f), t, 0.5)
        assertTrue(c.state.ducking)
        feed(c, pose(0.5f), t, 0.5)
        assertTrue(!c.state.ducking)
    }

    @Test
    fun jumpWithoutRaisedHandsIsIgnored() {
        val (c, t0) = calibrated()
        val before = c.state.jumpSeq
        val t = feed(c, pose(0.5f, dy = -0.08f), t0, 0.3)
        feed(c, pose(0.5f), t, 0.5)
        assertEquals(before, c.state.jumpSeq)
    }

    @Test
    fun sidewaysHopNeverRegistersAJump() {
        val (c, t0) = calibrated()
        val before = c.state.jumpSeq
        // hop left: body goes up while moving sideways, then lands in the left lane
        var t = t0
        for (i in 0 until 12) {
            val p = i / 11f
            val lift = -0.09f * kotlin.math.sin(p * Math.PI).toFloat()
            c.update(pose(0.5f - 0.12f * p, dy = lift), aspect, t); t += 1.0 / 30
        }
        feed(c, pose(0.38f), t, 0.6)
        assertEquals(-1, c.state.lane)
        assertEquals(before, c.state.jumpSeq)
    }

    @Test
    fun lostTrackingIsReported() {
        val (c, t0) = calibrated()
        feed(c, FloatArray(0).let { null } ?: FloatArray(33 * 4), t0, 1.0)
        assertTrue(c.state.lostSeconds > 0.8f)
    }
}

class RunnerGameTest {
    @Test
    fun standingStillEventuallyEndsTheRun() {
        val g = RunnerGame(RunnerConfig(lives = 3, startSpeed = 5, maxSpeed = 5, mode = SpeedMode.CONSTANT), seed = 1)
        g.startCountdown()
        var t = 0f
        while (g.phase != RunnerGame.Phase.OVER && t < 300f) { g.update(1 / 60f); t += 1 / 60f }
        assertEquals(RunnerGame.Phase.OVER, g.phase)
        assertEquals(3, g.hits)
    }

    @Test
    fun assistCallsOutMovesAndFollowingThemAvoidsWalls() {
        val g = RunnerGame(RunnerConfig(lives = 0, moves = Moves.LANES_ONLY, assist = true), seed = 4)
        g.startCountdown()
        var cues = 0
        repeat(60 * 90) {
            g.update(1 / 60f)
            for (e in g.events) if (e is RunnerGame.Event.Cue) {
                cues++
                // obey the callout exactly as a player would
                g.nudgeLane(if (e.action == RunnerGame.Action.LEFT) -1 else 1)
            }
            g.events.clear()
        }
        assertTrue("cues=$cues", cues > 5)
        assertEquals(0, g.hits)
    }

    @Test
    fun easyPresetLeavesLotsOfReactionTime() {
        val g = RunnerGame(RunnerConfig.EASY, seed = 5)
        g.startCountdown()
        repeat(60 * 5) { g.update(1 / 60f) }
        val zs = g.entities.filter { it.isObstacle }.map { it.z }.distinct().sortedDescending()
        val minGapSeconds = zs.zipWithNext { a, b -> (a - b) / g.speed }.minOrNull() ?: 99f
        assertTrue("gap=$minGapSeconds", minGapSeconds > 2.5f)
    }

    @Test
    fun lanesOnlyNeverSpawnsJumpOrDuckObstacles() {
        val g = RunnerGame(RunnerConfig(lives = 0, moves = Moves.LANES_ONLY), seed = 2)
        g.startCountdown()
        repeat(60 * 60) { g.update(1 / 60f) }
        assertTrue(g.entities.none { it.kind == RunnerGame.Kind.HURDLE || it.kind == RunnerGame.Kind.BAR })
    }

    @Test
    fun perfectPlayerDodgesEverything() {
        val g = RunnerGame(RunnerConfig(lives = 0, startSpeed = 5, maxSpeed = 5, mode = SpeedMode.CONSTANT), seed = 3)
        g.startCountdown()
        repeat(60 * 90) {
            // simple bot: look at the nearest obstacle in front and react
            val next = g.entities.filter { it.isObstacle && !it.done && it.z < 0 && it.z > -9f }.maxByOrNull { it.z }
            if (next != null) {
                val blocked = g.entities.filter { it.isObstacle && !it.done && kotlin.math.abs(it.z - next.z) < 1f }
                when (next.kind) {
                    RunnerGame.Kind.WALL -> {
                        val free = (-1..1).firstOrNull { l -> blocked.none { it.kind == RunnerGame.Kind.WALL && it.lane == l } }
                        if (free != null) g.setLane(free)
                    }
                    RunnerGame.Kind.HURDLE -> if (next.wide || next.lane == g.targetLane) { if (next.z > -g.speed * g.jumpDuration * 0.5f) g.jump() }
                    RunnerGame.Kind.BAR -> if (next.wide || next.lane == g.targetLane) g.duck(0.5f)
                    else -> Unit
                }
            }
            g.update(1 / 60f)
        }
        assertTrue("cleared=${g.cleared} hits=${g.hits}", g.cleared > 30 && g.hits <= 2)
    }
}
