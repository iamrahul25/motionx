package com.nightwise.motionx

import com.nightwise.motionx.games.pose.PoseDef
import com.nightwise.motionx.games.pose.PoseFigure
import com.nightwise.motionx.games.pose.PoseLibrary
import com.nightwise.motionx.games.pose.PoseMatcher
import com.nightwise.motionx.games.pose.PoseSession
import com.nightwise.motionx.games.pose.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PoseMatchTest {
    private val aspect = 4f / 3f
    private fun lm(def: PoseDef) = PoseFigure.toLandmarks(PoseFigure.build(def), aspect)

    @Test
    fun everyTierHasPoses() {
        Tier.entries.forEach { assertTrue("$it", PoseLibrary.tier(it).size >= 4) }
        assertEquals(PoseLibrary.ALL.size, PoseLibrary.ALL.map { it.id }.toSet().size)
    }

    @Test
    fun everyPoseMatchesItsOwnFigure() {
        for (p in PoseLibrary.ALL) {
            val r = PoseMatcher.evaluate(p, lm(p), aspect)
            assertTrue("${p.id} should match itself: ${r.hint} errors=${r.errors.toList()}", r.matched)
        }
    }

    @Test
    fun asymmetricPosesAcceptTheOtherSide() {
        for (p in PoseLibrary.ALL.filter { it.mirrorOk }) {
            val r = PoseMatcher.evaluate(p, lm(p.mirrored()), aspect)
            assertTrue("${p.id} mirrored should match: ${r.hint}", r.matched)
        }
    }

    @Test
    fun standingDoesNotPassOtherPoses() {
        val stand = lm(PoseLibrary.byId("stand")!!)
        for (p in PoseLibrary.ALL.filter { it.id != "stand" }) {
            assertFalse("standing should not pass ${p.id}", PoseMatcher.evaluate(p, stand, aspect).matched)
        }
    }

    @Test
    fun distinctPosesDoNotCrossMatch() {
        val pairs = listOf("reach" to "tpose", "tpose" to "goal", "hips" to "stand", "chair" to "reach", "squatT" to "tpose", "handstand" to "reach")
        for ((a, b) in pairs) {
            val r = PoseMatcher.evaluate(PoseLibrary.byId(a)!!, lm(PoseLibrary.byId(b)!!), aspect)
            assertFalse("$b should not pass $a", r.matched)
        }
    }

    /** Joint positions measured from a real phone screenshot of a player doing "hands on hips". */
    private fun realHandsOnHips(): FloatArray {
        val pts = mapOf(
            com.nightwise.motionx.tracking.Lm.NOSE to (1162f to 170f),
            com.nightwise.motionx.tracking.Lm.L_SHOULDER to (1113f to 220f), com.nightwise.motionx.tracking.Lm.R_SHOULDER to (1207f to 221f),
            com.nightwise.motionx.tracking.Lm.L_ELBOW to (1072f to 275f), com.nightwise.motionx.tracking.Lm.R_ELBOW to (1240f to 270f),
            com.nightwise.motionx.tracking.Lm.L_WRIST to (1097f to 332f), com.nightwise.motionx.tracking.Lm.R_WRIST to (1226f to 322f),
            com.nightwise.motionx.tracking.Lm.L_HIP to (1137f to 380f), com.nightwise.motionx.tracking.Lm.R_HIP to (1190f to 380f),
            com.nightwise.motionx.tracking.Lm.L_KNEE to (1141f to 497f), com.nightwise.motionx.tracking.Lm.R_KNEE to (1190f to 497f),
            com.nightwise.motionx.tracking.Lm.L_ANKLE to (1141f to 600f), com.nightwise.motionx.tracking.Lm.R_ANKLE to (1190f to 600f),
        )
        val a = FloatArray(33 * 4)
        for ((i, p) in pts) { a[i * 4] = p.first / 1400f; a[i * 4 + 1] = p.second / 1400f; a[i * 4 + 3] = 0.99f }
        return a
    }

    @Test
    fun realHandsOnHipsPasses() {
        val r = PoseMatcher.evaluate(PoseLibrary.byId("hips")!!, realHandsOnHips(), 1f)
        assertTrue("hint=${r.hint} errors=${r.errors.toList()}", r.matched)
    }

    @Test
    fun realStandingLegsAreNotASquat() {
        for (id in listOf("chair", "squatT", "deepsquat", "flamingo")) {
            val p = PoseLibrary.byId(id)!!
            val r = PoseMatcher.evaluate(p.copy(tolerance = 90f), realHandsOnHips(), 1f)
            assertFalse("$id should need bent knees", r.matched)
        }
    }

    @Test
    fun sessionHoldsThenAutoAdvancesAndTimesOut() {
        val poses = PoseLibrary.tier(Tier.EASY).take(2)
        val s = PoseSession(poses, timeLimit = 10f)
        val good = PoseMatcher.evaluate(poses[0], lm(poses[0]), aspect)
        repeat(60 * 7) { s.update(1 / 60f, good) }
        assertEquals(1, s.passedCount)
        assertEquals(3, s.results[0].stars)
        assertEquals(1, s.index)
        // second pose: never matched → fails after the time limit, session done
        val bad = PoseMatcher.evaluate(poses[1], lm(poses[0]), aspect)
        repeat(60 * 15) { s.update(1 / 60f, bad) }
        assertEquals(PoseSession.Phase.DONE, s.phase)
        assertEquals(2, s.results.size)
        assertFalse(s.results[1].passed)
    }
}
