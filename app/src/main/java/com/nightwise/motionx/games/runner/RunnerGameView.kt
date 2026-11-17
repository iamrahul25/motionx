package com.nightwise.motionx.games.runner

import android.content.Context
import android.opengl.GLSurfaceView
import com.nightwise.motionx.gl.MsaaConfigChooser

/** GLSurfaceView configured for ES 3.0 + MSAA, hosting a [RunnerRenderer]. */
class RunnerGameView(context: Context, renderer: RunnerRenderer) : GLSurfaceView(context) {
    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(MsaaConfigChooser())
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }
}
