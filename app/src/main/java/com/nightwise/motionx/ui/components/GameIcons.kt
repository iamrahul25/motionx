package com.nightwise.motionx.ui.components

import com.nightwise.motionx.games.gate.GateDifficulty
import com.nightwise.motionx.games.pose.Tier
import com.nightwise.motionx.games.runner.RunnerConfig

/** Drawn icons that stand in for the emoji stored on the game data models. */
fun Tier.icon(): Ico = when (this) {
    Tier.EASY -> Ico.LEAF
    Tier.MEDIUM -> Ico.FLAME
    Tier.HARD -> Ico.BOLT
    Tier.VERY_HARD -> Ico.DIAMOND
    Tier.EXTREME -> Ico.CROWN
}

fun GateDifficulty.icon(): Ico = when (this) {
    GateDifficulty.EASY -> Ico.LEAF
    GateDifficulty.MEDIUM -> Ico.FLAME
    GateDifficulty.HARD -> Ico.BOLT
}

fun RunnerConfig.icon(): Ico = when (preset) {
    "Easy" -> Ico.LEAF
    "Medium" -> Ico.RUN
    "Hard" -> Ico.FLAME
    "Training" -> Ico.TARGET
    else -> Ico.SLIDERS
}
