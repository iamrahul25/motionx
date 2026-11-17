package com.nightwise.motionx.tracking

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.WindowManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.image.BitmapExtractor
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Front camera → MediaPipe Pose Landmarker (LIVE_STREAM) → [frames].
 *
 * Frames are rotated upright and mirrored (selfie view) *before* inference, so landmark x grows
 * to the player's right as they see themselves on screen.
 */
class PoseTracker(private val context: Context) {

    enum class Status { IDLE, STARTING, RUNNING, ERROR }

    private val _frames = MutableStateFlow<PoseFrame?>(null)
    val frames: StateFlow<PoseFrame?> = _frames

    private val _status = MutableStateFlow(Status.IDLE)
    val status: StateFlow<Status> = _status

    var errorMessage: String? = null
        private set
    var usingGpu = false
        private set

    /** Listener called on the inference thread for every result (used by game controllers). */
    @Volatile
    var onFrame: ((PoseFrame) -> Unit)? = null

    private var executor: ExecutorService? = null
    private var landmarker: PoseLandmarker? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var lastTs = 0L
    private var fpsWindowStart = 0L
    private var fpsCount = 0
    private var fps = 0f
    private val sendTimes = HashMap<Long, Long>()

    fun start(owner: LifecycleOwner) {
        if (_status.value == Status.RUNNING || _status.value == Status.STARTING) return
        _status.value = Status.STARTING
        val exec = Executors.newSingleThreadExecutor()
        executor = exec
        exec.execute {
            try {
                landmarker = createLandmarker(Delegate.GPU).also { usingGpu = true }
            } catch (t: Throwable) {
                Log.w(TAG, "GPU delegate unavailable, falling back to CPU", t)
                try {
                    landmarker = createLandmarker(Delegate.CPU)
                    usingGpu = false
                } catch (t2: Throwable) {
                    fail("Could not load the pose model: ${t2.message}")
                    return@execute
                }
            }
            ContextCompat.getMainExecutor(context).execute { bindCamera(owner, exec) }
        }
    }

    fun stop() {
        try {
            cameraProvider?.unbindAll()
        } catch (_: Throwable) {
        }
        cameraProvider = null
        val exec = executor
        executor = null
        exec?.execute {
            try {
                landmarker?.close()
            } catch (_: Throwable) {
            }
            landmarker = null
        }
        exec?.shutdown()
        _status.value = Status.IDLE
        _frames.value = null
    }

    private fun fail(msg: String) {
        errorMessage = msg
        _status.value = Status.ERROR
        Log.e(TAG, msg)
    }

    private fun createLandmarker(delegate: Delegate): PoseLandmarker {
        val base = BaseOptions.builder()
            .setModelAssetPath(MODEL)
            .setDelegate(delegate)
            .build()
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumPoses(1)
            .setMinPoseDetectionConfidence(0.5f)
            .setMinPosePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setResultListener(this::onResult)
            .setErrorListener { e ->
                Log.e(TAG, "Landmarker error", e)
                if (delegate == Delegate.GPU) gpuFailed = true
            }
            .build()
        return PoseLandmarker.createFromOptions(context, options)
    }

    private fun displayRotation(): Int {
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            (context as? Activity)?.display ?: context.display
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        }
        return display?.rotation ?: Surface.ROTATION_0
    }

    private fun bindCamera(owner: LifecycleOwner, exec: ExecutorService) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider
                val selector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(
                        ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                    )
                    .build()
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(selector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setTargetRotation(displayRotation())
                    .build()
                analysis.setAnalyzer(exec, ::analyze)
                val camSelector = if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                provider.unbindAll()
                provider.bindToLifecycle(owner, camSelector, analysis)
                _status.value = Status.RUNNING
            } catch (t: Throwable) {
                fail("Camera could not start: ${t.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun analyze(proxy: ImageProxy) {
        if (gpuFailed && usingGpu) switchToCpu()
        val lm = landmarker
        if (lm == null) {
            proxy.close(); return
        }
        val upright: Bitmap
        try {
            val raw = proxy.toBitmap()
            val m = Matrix().apply {
                postRotate(proxy.imageInfo.rotationDegrees.toFloat())
                postScale(-1f, 1f) // selfie mirror
            }
            upright = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        } catch (t: Throwable) {
            return
        } finally {
            proxy.close()
        }
        var ts = SystemClock.uptimeMillis()
        if (ts <= lastTs) ts = lastTs + 1
        lastTs = ts
        synchronized(sendTimes) { sendTimes[ts] = SystemClock.elapsedRealtime() }
        try {
            lm.detectAsync(BitmapImageBuilder(upright).build(), ts)
        } catch (t: Throwable) {
            Log.w(TAG, "detectAsync failed", t)
            if (usingGpu) gpuFailed = true
        }
    }

    /** Some GPUs (and emulators) load the GPU delegate but fail at inference time. */
    @Volatile
    private var gpuFailed = false

    private fun switchToCpu() {
        Log.w(TAG, "GPU inference failed, switching to CPU delegate")
        try {
            landmarker?.close()
        } catch (_: Throwable) {
        }
        landmarker = try {
            createLandmarker(Delegate.CPU)
        } catch (t: Throwable) {
            fail("Could not load the pose model: ${t.message}")
            null
        }
        usingGpu = false
        gpuFailed = false
    }

    private fun onResult(result: PoseLandmarkerResult, input: MPImage) {
        val now = SystemClock.elapsedRealtime()
        val sent = synchronized(sendTimes) {
            val v = sendTimes.remove(result.timestampMs()) ?: now
            if (sendTimes.size > 30) sendTimes.clear()
            v
        }
        fpsCount++
        if (fpsWindowStart == 0L) fpsWindowStart = now
        if (now - fpsWindowStart >= 1000) {
            fps = fpsCount * 1000f / (now - fpsWindowStart)
            fpsCount = 0
            fpsWindowStart = now
        }
        val bmp = try {
            BitmapExtractor.extract(input)
        } catch (t: Throwable) {
            return
        }
        val poses = result.landmarks()
        val packed = if (poses.isNotEmpty()) {
            val p = poses[0]
            FloatArray(33 * 4).also { arr ->
                for (i in 0 until minOf(33, p.size)) {
                    val l = p[i]
                    arr[i * 4] = l.x()
                    arr[i * 4 + 1] = l.y()
                    arr[i * 4 + 2] = l.z()
                    arr[i * 4 + 3] = l.visibility().orElse(0f)
                }
            }
        } else {
            null
        }
        val frame = PoseFrame(bmp, packed, result.timestampMs(), now - sent, fps)
        onFrame?.invoke(frame)
        _frames.value = frame
    }

    companion object {
        private const val TAG = "PoseTracker"
        const val MODEL = "pose_landmarker_full.task"
    }
}
