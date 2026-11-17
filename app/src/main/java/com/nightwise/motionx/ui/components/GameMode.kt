package com.nightwise.motionx.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.nightwise.motionx.tracking.PoseTracker

/** Hides system bars and keeps the screen on while a game screen is visible. */
@Composable
fun ImmersiveGameMode(activity: ComponentActivity) {
    DisposableEffect(Unit) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val ctl = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctl.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            ctl.show(WindowInsetsCompat.Type.systemBars())
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

class CameraTracking(val tracker: PoseTracker, val hasPermission: Boolean, val request: () -> Unit)

/**
 * Asks for the camera permission, starts a [PoseTracker] bound to the activity lifecycle and stops it
 * when the composable leaves. Set [PoseTracker.onFrame] to receive results on the inference thread.
 */
@Composable
fun rememberCameraTracking(activity: ComponentActivity): CameraTracking {
    var has by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { has = it }
    val tracker = remember { PoseTracker(activity) }
    LaunchedEffect(Unit) { if (!has) launcher.launch(Manifest.permission.CAMERA) }
    LaunchedEffect(has) { if (has) tracker.start(activity) }
    DisposableEffect(Unit) { onDispose { tracker.onFrame = null; tracker.stop() } }
    return CameraTracking(tracker, has) { launcher.launch(Manifest.permission.CAMERA) }
}
