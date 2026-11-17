package com.nightwise.motionx.games.runner

enum class SpeedMode(val label: String) { CONSTANT("Constant"), INCREASING("Increasing") }

enum class Moves(val label: String, val short: String) {
    ALL("Hop, jump & duck", "All moves"),
    NO_DUCK("Hop & jump", "No ducking"),
    LANES_ONLY("Hop only", "Lanes only"),
}

data class RunnerConfig(
    val preset: String = "Medium",
    val emoji: String = "🏃",
    /** Speed dial 1..10 at the start of the run. */
    val startSpeed: Int = 3,
    /** Top speed for [SpeedMode.INCREASING]. */
    val maxSpeed: Int = 6,
    val mode: SpeedMode = SpeedMode.INCREASING,
    /** 0 = unlimited. */
    val lives: Int = 4,
    val moves: Moves = Moves.ALL,
    val training: Boolean = false,
    /** Voice + on-screen callouts ("Jump!", "Duck!", "Move left!") before every obstacle. */
    val assist: Boolean = true,
) {
    val topSpeed get() = if (mode == SpeedMode.CONSTANT) startSpeed else maxOf(startSpeed, maxSpeed)
    val scoreMultiplier: Float
        get() = ((0.6f + topSpeed * 0.07f + startSpeed * 0.05f) * when (moves) {
            Moves.ALL -> 1f
            Moves.NO_DUCK -> 0.9f
            Moves.LANES_ONLY -> 0.8f
        } * (if (lives == 0) 0.5f else 1f) * (if (assist) 0.9f else 1f)).let { (it * 10).toInt() / 10f }

    /** Difficulty rating: Easy 2 · Medium 3 · Hard 5. */
    val stars get() = ((topSpeed + 1) / 2).coerceIn(1, 5)

    val summary: String
        get() = buildString {
            append("Speed ").append(startSpeed)
            if (mode == SpeedMode.INCREASING && maxSpeed > startSpeed) append(" → ").append(maxSpeed)
            append(" · ").append(mode.label)
            append(" · ").append(if (lives == 0) "Unlimited lives" else "$lives lives")
            append(" · ").append(moves.short)
            if (assist) append(" · Assist")
        }

    companion object {
        val EASY = RunnerConfig("Easy", "🙂", 1, 3, SpeedMode.INCREASING, 5, Moves.ALL)
        val MEDIUM = RunnerConfig("Medium", "🏃", 3, 6, SpeedMode.INCREASING, 4, Moves.ALL)
        val HARD = RunnerConfig("Hard", "🔥", 6, 9, SpeedMode.INCREASING, 3, Moves.ALL, assist = false)
        val PRESETS = listOf(EASY, MEDIUM, HARD)
        val TRAINING = RunnerConfig("Training", "🎯", 1, 1, SpeedMode.CONSTANT, 0, Moves.ALL, training = true)

        /** World units per second for a speed-dial value (speed 1 ≈ a gentle jog). */
        fun worldSpeed(level: Float) = 5f + level * 1.6f
    }
}
