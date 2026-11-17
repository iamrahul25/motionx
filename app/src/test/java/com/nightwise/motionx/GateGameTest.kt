package com.nightwise.motionx

import com.nightwise.motionx.games.gate.GateDifficulty
import com.nightwise.motionx.games.gate.GateGame
import com.nightwise.motionx.games.pose.PoseFigure
import com.nightwise.motionx.games.pose.PoseLibrary
import com.nightwise.motionx.games.pose.PoseMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GateGameTest {
    private fun run(g: GateGame, seconds: Float, matched: (GateGame) -> Boolean) {
        var t = 0f
        while (t < seconds && g.phase != GateGame.Phase.OVER) {
            g.update(1 / 60f, matched(g)); t += 1 / 60f
        }
    }

    @Test
    fun allDifficultyPosesExist() {
        GateDifficulty.entries.forEach { d -> d.poseIds.forEach { assertTrue("$d/$it", PoseLibrary.byId(it) != null) } }
    }

    @Test
    fun matchingEveryWallPassesAndScores() {
        val g = GateGame(GateDifficulty.MEDIUM, seed = 1)
        g.startCountdown()
        run(g, 60f) { true }
        assertTrue(g.passed >= 8)
        assertEquals(g.difficulty.lives, g.lives)
        assertTrue(g.score > 0)
    }

    @Test
    fun failingRetriesSameShapeThenGameOver() {
        val g = GateGame(GateDifficulty.EASY, seed = 2)
        g.startCountdown()
        val first = g.pose.id
        val seen = ArrayList<GateGame.Event>()
        var t = 0f
        while (g.phase != GateGame.Phase.OVER && t < 200f) {
            g.update(1 / 60f, false); t += 1 / 60f
            seen += g.events; g.events.clear()
        }
        assertEquals(GateGame.Phase.OVER, g.phase)
        assertEquals(0, g.lives)
        assertEquals(first, g.pose.id) // the same shape kept coming back
        assertTrue(seen.filterIsInstance<GateGame.Event.NewWall>().drop(1).all { it.retry })
    }

    @Test
    fun widenedToleranceStillRejectsWrongPose() {
        val g = GateGame(GateDifficulty.EASY, seed = 3)
        val target = g.matchPose
        val other = PoseLibrary.ALL.first { it.id != target.id && it.id in GateDifficulty.EASY.poseIds && it.id != "stand" }
        val lm = PoseFigure.toLandmarks(PoseFigure.build(if (target.id == "tpose") PoseLibrary.byId("reach")!! else PoseLibrary.byId("tpose")!!))
        val selfLm = PoseFigure.toLandmarks(PoseFigure.build(g.pose))
        assertTrue(PoseMatcher.evaluate(target, selfLm, 4f / 3f).matched)
        if (target.id != other.id) assertTrue(!PoseMatcher.evaluate(target, lm, 4f / 3f).matched || target.id == "stand")
    }
}
