package com.nightwise.motionx

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import com.nightwise.motionx.audio.Sfx
import com.nightwise.motionx.data.Prefs
import com.nightwise.motionx.games.pose.Tier
import com.nightwise.motionx.games.runner.RunnerConfig
import com.nightwise.motionx.ui.screens.PoseMapScreen
import com.nightwise.motionx.ui.screens.PoseMatchScreen
import com.nightwise.motionx.ui.screens.HomeScreen
import com.nightwise.motionx.ui.screens.HowToPlayScreen
import com.nightwise.motionx.ui.screens.RunnerDifficultyScreen
import com.nightwise.motionx.ui.screens.RunnerGameScreen
import com.nightwise.motionx.ui.screens.RunnerLobbyScreen
import com.nightwise.motionx.ui.theme.MX
import com.nightwise.motionx.ui.theme.MotionXTheme

/** Simple back-stack navigation; enough for a handful of screens and no extra dependency. */
sealed interface Screen {
    data object Home : Screen
    data object RunnerLobby : Screen
    data object RunnerDifficulty : Screen
    data object RunnerHowTo : Screen
    data class RunnerPlay(val config: RunnerConfig) : Screen
    data object PoseMap : Screen
    data class PosePlay(val tier: Tier, val start: Int) : Screen
}

class MainActivity : ComponentActivity() {
    private lateinit var sfx: Sfx

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(MX.Night.copy(alpha = 0f).toArgb()),
            navigationBarStyle = SystemBarStyle.dark(MX.Night.copy(alpha = 0f).toArgb()),
        )
        val prefs = Prefs(this)
        sfx = Sfx(this).apply { soundOn = prefs.sound; hapticsOn = prefs.haptics }
        setContent {
            MotionXTheme {
                App(prefs, sfx)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sfx.release()
    }
}

@Composable
private fun App(prefs: Prefs, sfx: Sfx) {
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    var config by remember { mutableStateOf(prefs.runnerConfig) }
    var homeKey by remember { mutableStateOf(0) }
    val current = stack.last()

    fun push(s: Screen) = stack.add(s)
    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.size - 1)
        homeKey++ // refresh stats on the way back
    }

    BackHandler(enabled = stack.size > 1 && current !is Screen.RunnerPlay && current !is Screen.PosePlay) { pop() }

    Box(Modifier.fillMaxSize().background(MX.Night)) {
        AnimatedContent(
            targetState = current,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "nav",
        ) { screen ->
            when (screen) {
                Screen.Home -> androidx.compose.runtime.key(homeKey) {
                    HomeScreen(prefs, sfx, onOpenRunner = { push(Screen.RunnerLobby) }, onOpenPose = { push(Screen.PoseMap) })
                }
                Screen.RunnerLobby -> androidx.compose.runtime.key(homeKey) {
                    RunnerLobbyScreen(
                        prefs, config,
                        onBack = ::pop,
                        onPlay = {
                            if (!prefs.tutorialSeen) push(Screen.RunnerHowTo) else push(Screen.RunnerPlay(config))
                        },
                        onTraining = { push(Screen.RunnerPlay(RunnerConfig.TRAINING)) },
                        onDifficulty = { push(Screen.RunnerDifficulty) },
                        onHowTo = { push(Screen.RunnerHowTo) },
                    )
                }
                Screen.RunnerDifficulty -> RunnerDifficultyScreen(config, onBack = ::pop) {
                    config = it
                    prefs.runnerConfig = it
                    pop()
                }
                Screen.RunnerHowTo -> HowToPlayScreen(
                    onBack = { prefs.tutorialSeen = true; pop() },
                    onTraining = {
                        prefs.tutorialSeen = true
                        stack.removeAt(stack.size - 1)
                        push(Screen.RunnerPlay(RunnerConfig.TRAINING))
                    },
                )
                is Screen.RunnerPlay -> RunnerGameScreen(screen.config, prefs, sfx, onExit = ::pop)
                Screen.PoseMap -> androidx.compose.runtime.key(homeKey) {
                    PoseMapScreen(prefs, onBack = ::pop) { tier, start -> push(Screen.PosePlay(tier, start)) }
                }
                is Screen.PosePlay -> androidx.compose.runtime.key(screen) {
                    PoseMatchScreen(
                        screen.tier, screen.start, prefs, sfx,
                        onExit = ::pop,
                        onPlayTier = { t ->
                            stack.removeAt(stack.size - 1)
                            push(Screen.PosePlay(t, 0))
                        },
                    )
                }
            }
        }
    }
}
