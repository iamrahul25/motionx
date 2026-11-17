package com.nightwise.motionx.gl

import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay

/** Picks an RGB888 + depth config with 4× MSAA when the device supports it. */
class MsaaConfigChooser : GLSurfaceView.EGLConfigChooser {
    override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {
        val candidates = listOf(
            attribs(ES3_BIT, 4), attribs(ES2_BIT, 4), attribs(ES3_BIT, 0), attribs(ES2_BIT, 0),
        )
        for (a in candidates) {
            val num = IntArray(1)
            val configs = arrayOfNulls<EGLConfig>(1)
            if (egl.eglChooseConfig(display, a, configs, 1, num) && num[0] > 0 && configs[0] != null) {
                return configs[0]!!
            }
        }
        throw IllegalStateException("No suitable EGL config")
    }

    private fun attribs(renderable: Int, samples: Int): IntArray {
        val base = mutableListOf(
            EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8,
            EGL10.EGL_DEPTH_SIZE, 16, EGL10.EGL_RENDERABLE_TYPE, renderable,
        )
        if (samples > 0) base += listOf(EGL10.EGL_SAMPLE_BUFFERS, 1, EGL10.EGL_SAMPLES, samples)
        base += EGL10.EGL_NONE
        return base.toIntArray()
    }

    private companion object {
        const val ES2_BIT = 4
        const val ES3_BIT = 0x40
    }
}
