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
