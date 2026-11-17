package com.nightwise.motionx.data

import android.content.Context
import com.nightwise.motionx.games.runner.Moves
import com.nightwise.motionx.games.runner.RunnerConfig
import com.nightwise.motionx.games.runner.SpeedMode

data class RunnerTotals(
    val runs: Int = 0,
    val bestScore: Int = 0,
    val bestDistance: Int = 0,
    val coins: Int = 0,
    val distance: Int = 0,
    val seconds: Int = 0,
    val jumps: Int = 0,
    val ducks: Int = 0,
    val hops: Int = 0,
)

data class RunResult(
    val score: Int,
    val distance: Int,
    val seconds: Int,
    val coins: Int,
    val stars: Int,
    val jumps: Int,
    val ducks: Int,
    val hops: Int,
    val cleared: Int,
    val hits: Int,
    val newBest: Boolean = false,
)

/** Tiny SharedPreferences store: settings + lifetime stats. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("motionx", Context.MODE_PRIVATE)

    var sound: Boolean
        get() = sp.getBoolean("sound", true)
        set(v) = sp.edit().putBoolean("sound", v).apply()

    var haptics: Boolean
        get() = sp.getBoolean("haptics", true)
        set(v) = sp.edit().putBoolean("haptics", v).apply()

    var tutorialSeen: Boolean
        get() = sp.getBoolean("tutorial_seen", false)
        set(v) = sp.edit().putBoolean("tutorial_seen", v).apply()

    var runnerConfig: RunnerConfig
        get() {
            // v2 introduced Easy/Medium/Hard with a slower speed scale: reset older saved setups.
            if (sp.getInt("rc_version", 1) < 2) return RunnerConfig.MEDIUM
            return RunnerConfig(
                preset = sp.getString("rc_preset", "Medium")!!,
                emoji = sp.getString("rc_emoji", "🏃")!!,
                startSpeed = sp.getInt("rc_start", 3),
                maxSpeed = sp.getInt("rc_max", 6),
                mode = runCatching { SpeedMode.valueOf(sp.getString("rc_mode", "INCREASING")!!) }.getOrDefault(SpeedMode.INCREASING),
                lives = sp.getInt("rc_lives", 4),
                moves = runCatching { Moves.valueOf(sp.getString("rc_moves", "ALL")!!) }.getOrDefault(Moves.ALL),
                assist = sp.getBoolean("rc_assist", true),
            )
        }
        set(c) = sp.edit()
            .putInt("rc_version", 2)
            .putString("rc_preset", c.preset).putString("rc_emoji", c.emoji)
            .putInt("rc_start", c.startSpeed).putInt("rc_max", c.maxSpeed)
            .putString("rc_mode", c.mode.name).putInt("rc_lives", c.lives)
            .putString("rc_moves", c.moves.name).putBoolean("rc_assist", c.assist).apply()

    val runnerTotals: RunnerTotals
        get() = RunnerTotals(
            sp.getInt("t_runs", 0), sp.getInt("t_best", 0), sp.getInt("t_best_dist", 0),
            sp.getInt("t_coins", 0), sp.getInt("t_dist", 0), sp.getInt("t_sec", 0),
            sp.getInt("t_jumps", 0), sp.getInt("t_ducks", 0), sp.getInt("t_hops", 0),
        )

    /** Records a finished run and returns it with [RunResult.newBest] filled in. */
    fun record(r: RunResult): RunResult {
        val t = runnerTotals
        val best = r.score > t.bestScore
        sp.edit()
            .putInt("t_runs", t.runs + 1)
            .putInt("t_best", maxOf(t.bestScore, r.score))
            .putInt("t_best_dist", maxOf(t.bestDistance, r.distance))
            .putInt("t_coins", t.coins + r.coins)
            .putInt("t_dist", t.distance + r.distance)
            .putInt("t_sec", t.seconds + r.seconds)
            .putInt("t_jumps", t.jumps + r.jumps)
            .putInt("t_ducks", t.ducks + r.ducks)
            .putInt("t_hops", t.hops + r.hops)
            .apply()
        return r.copy(newBest = best && r.score > 0)
    }
}
