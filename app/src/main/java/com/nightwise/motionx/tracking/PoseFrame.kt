package com.nightwise.motionx.tracking

import android.graphics.Bitmap

/**
 * One analysed camera frame. [bitmap] is the exact (rotated + mirrored) image the landmarks
 * were computed on, so drawing them together always lines up.
 *
 * Landmarks are packed as [x, y, z, visibility] * 33 in normalised image coordinates
 * (x: 0..1 left→right of the mirrored image, y: 0..1 top→bottom).
 */
class PoseFrame(
    val bitmap: Bitmap,
    val landmarks: FloatArray?,
    val timestampMs: Long,
    val inferenceMs: Long,
    val fps: Float,
) {
    val width get() = bitmap.width
    val height get() = bitmap.height
    val hasPose get() = landmarks != null

    fun x(i: Int) = landmarks!![i * 4]
    fun y(i: Int) = landmarks!![i * 4 + 1]
    fun z(i: Int) = landmarks!![i * 4 + 2]
    fun vis(i: Int) = landmarks!![i * 4 + 3]
}

/** BlazePose landmark indices used by the games. */
object Lm {
    const val NOSE = 0
    const val L_EYE = 2
    const val R_EYE = 5
    const val L_EAR = 7
    const val R_EAR = 8
    const val L_SHOULDER = 11
    const val R_SHOULDER = 12
    const val L_ELBOW = 13
    const val R_ELBOW = 14
    const val L_WRIST = 15
    const val R_WRIST = 16
    const val L_HIP = 23
    const val R_HIP = 24
    const val L_KNEE = 25
    const val R_KNEE = 26
    const val L_ANKLE = 27
    const val R_ANKLE = 28
    const val L_HEEL = 29
    const val R_HEEL = 30
    const val L_FOOT = 31
    const val R_FOOT = 32

    /** Skeleton bones drawn on the tracking view. */
    val BONES = arrayOf(
        intArrayOf(L_SHOULDER, R_SHOULDER),
        intArrayOf(L_SHOULDER, L_ELBOW), intArrayOf(L_ELBOW, L_WRIST),
        intArrayOf(R_SHOULDER, R_ELBOW), intArrayOf(R_ELBOW, R_WRIST),
        intArrayOf(L_SHOULDER, L_HIP), intArrayOf(R_SHOULDER, R_HIP),
        intArrayOf(L_HIP, R_HIP),
        intArrayOf(L_HIP, L_KNEE), intArrayOf(L_KNEE, L_ANKLE),
        intArrayOf(R_HIP, R_KNEE), intArrayOf(R_KNEE, R_ANKLE),
        intArrayOf(L_ANKLE, L_HEEL), intArrayOf(L_HEEL, L_FOOT), intArrayOf(L_ANKLE, L_FOOT),
        intArrayOf(R_ANKLE, R_HEEL), intArrayOf(R_HEEL, R_FOOT), intArrayOf(R_ANKLE, R_FOOT),
    )
    val JOINTS = intArrayOf(
        L_SHOULDER, R_SHOULDER, L_ELBOW, R_ELBOW, L_WRIST, R_WRIST,
        L_HIP, R_HIP, L_KNEE, R_KNEE, L_ANKLE, R_ANKLE,
    )
}
