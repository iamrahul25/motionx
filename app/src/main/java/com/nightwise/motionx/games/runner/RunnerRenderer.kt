package com.nightwise.motionx.games.runner

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.SystemClock
import com.nightwise.motionx.gl.Mesh
import com.nightwise.motionx.gl.MeshBuilder
import com.nightwise.motionx.gl.ShaderProgram
import java.util.concurrent.ConcurrentLinkedQueue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.sin
import kotlin.math.tan
import kotlin.random.Random

/** Snapshot of game state for the Compose HUD (published ~20×/s). */
data class RunnerHud(
    val phase: RunnerGame.Phase = RunnerGame.Phase.WAITING,
    val countdown: Float = 0f,
    val score: Int = 0,
    val coins: Int = 0,
    val stars: Int = 0,
    val lives: Int = 0,
    val speedLevel: Float = 0f,
    val distance: Float = 0f,
    val time: Float = 0f,
    val hitFlash: Float = 0f,
    val combo: Int = 0,
    val lane: Int = 0,
    val sliding: Boolean = false,
    val airborne: Boolean = false,
    val cue: RunnerGame.Action? = null,
    val cueAge: Float = 99f,
)

/**
 * OpenGL ES 3.0 renderer + game loop host for Neon Dash. The [RunnerGame] is only touched on the
 * GL thread; other threads post inputs through [post].
 */
class RunnerRenderer(
    private val game: RunnerGame,
    private val onHud: (RunnerHud) -> Unit,
    private val onEvent: (RunnerGame.Event) -> Unit,
) : GLSurfaceView.Renderer {

    private val inputs = ConcurrentLinkedQueue<(RunnerGame) -> Unit>()
    fun post(action: (RunnerGame) -> Unit) = inputs.add(action)

    private lateinit var lit: ShaderProgram
    private lateinit var ground: ShaderProgram
    private lateinit var sky: ShaderProgram
    private lateinit var cube: Mesh
    private lateinit var sphere: Mesh
    private lateinit var cyl: Mesh
    private lateinit var star: Mesh
    private lateinit var groundMesh: Mesh
    private var skyVao = 0

    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val vp = FloatArray(16)
    private val m = FloatArray(16)
    private val tmp = FloatArray(16)
    private val stack = ArrayList<FloatArray>()

    private var width = 1
    private var height = 1
    private var lastNs = 0L
    private var clock = 0f
    private var lastHudMs = 0L
    private var shake = 0f
    private var camX = 0f
    private val rnd = Random(7)
    private var horizon = 0.7f

    private val fog = floatArrayOf(0.30f, 0.07f, 0.32f)
    private val fogDensity = 0.0115f
    private val eye = FloatArray(3)

    // Scenery
    private val bHeights = FloatArray(BUILDINGS) { 4f + rnd.nextFloat() * 22f }
    private val bWidths = FloatArray(BUILDINGS) { 2.5f + rnd.nextFloat() * 4f }
    private val bOffsets = FloatArray(BUILDINGS) { 9f + rnd.nextFloat() * 14f }
    private val bAccent = IntArray(BUILDINGS) { rnd.nextInt(3) }

    // Particles
    private val pPos = FloatArray(MAX_P * 3)
    private val pVel = FloatArray(MAX_P * 3)
    private val pLife = FloatArray(MAX_P)
    private val pCol = FloatArray(MAX_P * 3)
    private var pNext = 0

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        lit = ShaderProgram(RunnerShaders.LIT_VS, RunnerShaders.LIT_FS)
        ground = ShaderProgram(RunnerShaders.GROUND_VS, RunnerShaders.GROUND_FS)
        sky = ShaderProgram(RunnerShaders.SKY_VS, RunnerShaders.SKY_FS)
        cube = MeshBuilder().box().build()
        sphere = MeshBuilder().sphere(0.5f).build()
        cyl = MeshBuilder().cylinder(0.5f, 1f, 28).build()
        star = MeshBuilder().star().build()
        groundMesh = MeshBuilder().plane(-90f, -230f, 90f, 12f).build()
        val ids = IntArray(1)
        GLES30.glGenVertexArrays(1, ids, 0)
        skyVao = ids[0]
        GLES30.glClearColor(fog[0], fog[1], fog[2], 1f)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        lastNs = 0L
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = w; height = h
        GLES30.glViewport(0, 0, w, h)
        val aspect = w.toFloat() / h
        val fovY = if (aspect > 1.2f) 52f else 66f
        Matrix.perspectiveM(proj, 0, fovY, aspect, 0.1f, 260f)
        // horizon position on screen for the sky shader (camera pitch is fixed)
        val pitch = atan((CAM_Y - LOOK_Y) / (CAM_Z - LOOK_Z))
        horizon = 0.5f + 0.5f * (tan(pitch) / tan(Math.toRadians(fovY / 2.0).toFloat()))
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = SystemClock.elapsedRealtimeNanos()
        val dt = if (lastNs == 0L) 0.016f else ((now - lastNs) / 1e9f).coerceIn(0f, 0.05f)
        lastNs = now
        clock += dt

        while (true) {
            val a = inputs.poll() ?: break
            a(game)
        }
        game.update(dt)
        if (game.events.isNotEmpty()) {
            for (e in game.events) {
                handleEvent(e)
                onEvent(e)
            }
            game.events.clear()
        }
        updateParticles(dt)
        shake = (shake - dt * 2.2f).coerceAtLeast(0f)

        val nowMs = SystemClock.elapsedRealtime()
        if (nowMs - lastHudMs > 50) {
            lastHudMs = nowMs
            onHud(
                RunnerHud(
                    game.phase, game.countdown, game.score, game.coins, game.stars, game.lives,
                    game.speedLevel, game.distance, game.time, game.hitFlash, game.combo,
                    game.targetLane, game.sliding, game.jumpT >= 0f, game.cue, game.cueAge,
                ),
            )
        }

        renderScene()
    }

    private fun handleEvent(e: RunnerGame.Event) {
        when (e) {
            is RunnerGame.Event.Coin -> burst(e.x, e.y, e.z, 14, 1f, 0.78f, 0.2f, 4f)
            is RunnerGame.Event.Star -> burst(e.x, e.y, e.z, 40, 1f, 1f, 0.6f, 7f)
            is RunnerGame.Event.Hit -> {
                shake = 0.6f
                burst(game.playerX, 1.2f, -0.5f, 30, 1f, 0.3f, 0.3f, 6f)
            }
            else -> Unit
        }
    }

    // ---------------------------------------------------------------- rendering

    private fun renderScene() {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        // Camera follows the player softly, with shake on hits.
        camX += (game.playerX * 0.55f - camX) * 0.12f
        val sx = if (shake > 0) (rnd.nextFloat() - 0.5f) * shake * 0.6f else 0f
        val sy = if (shake > 0) (rnd.nextFloat() - 0.5f) * shake * 0.4f else 0f
        eye[0] = camX + sx; eye[1] = CAM_Y + game.playerY * 0.22f + sy; eye[2] = CAM_Z
        Matrix.setLookAtM(view, 0, eye[0], eye[1], eye[2], camX * 1.2f, LOOK_Y + game.playerY * 0.3f, LOOK_Z, 0f, 1f, 0f)
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)

        // Sky
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(false)
        sky.use()
        sky.set1f("uHorizon", horizon)
        sky.set1f("uAspect", width.toFloat() / height)
        sky.set1f("uTime", clock)
        sky.set1f("uSunX", 0.5f - camX * 0.01f)
        sky.set3f("uFogColor", fog[0], fog[1], fog[2])
        GLES30.glBindVertexArray(skyVao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
        GLES30.glBindVertexArray(0)

        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(true)

        // Ground
        ground.use()
        ground.setMat4("uVP", vp)
        ground.set1f("uScroll", game.distance % 600f)
        ground.set3f("uCam", eye[0], eye[1], eye[2])
        ground.set3f("uFogColor", fog[0], fog[1], fog[2])
        ground.set1f("uFogDensity", fogDensity)
        ground.set1f("uLaneW", RunnerGame.LANE_W)
        ground.set1f("uPulse", 0.5f + 0.5f * sin(clock * 3f))
        groundMesh.draw()

        lit.use()
        lit.setMat4("uVP", vp)
        lit.set3f("uCam", eye[0], eye[1], eye[2])
        lit.set3f("uFogColor", fog[0], fog[1], fog[2])
        lit.set1f("uFogDensity", fogDensity)
        lit.set3f("uLightDir", -0.35f, -0.85f, -0.4f)
        lit.set1f("uAlpha", 1f)

        drawScenery()
        drawEntities()
        drawPlayer()

        // Transparent pass
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glDepthMask(false)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        drawShadow()
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)
        drawHalos()
        drawParticles()
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
        lit.set1f("uAlpha", 1f)
    }

    private fun push() {
        stack.add(m.copyOf())
    }

    private fun pop() {
        val top = stack.removeAt(stack.size - 1)
        System.arraycopy(top, 0, m, 0, 16)
    }

    private fun drawMesh(mesh: Mesh, r: Float, g: Float, b: Float, emissive: Float = 0f) {
        lit.setMat4("uModel", m)
        lit.set3f("uColor", r, g, b)
        lit.set1f("uEmissive", emissive)
        mesh.draw()
    }

    /** Box from current matrix: translate to centre and scale. */
    private fun box(cx: Float, cy: Float, cz: Float, sx: Float, sy: Float, sz: Float, r: Float, g: Float, b: Float, e: Float = 0f, mesh: Mesh = cube) {
        push()
        Matrix.translateM(m, 0, cx, cy, cz)
        Matrix.scaleM(m, 0, sx, sy, sz)
        drawMesh(mesh, r, g, b, e)
        pop()
    }

    private fun drawScenery() {
        val scroll = game.distance
        // Neon arches over the track
        val archPeriod = ARCH_SPACING * ARCHES
        for (i in 0 until ARCHES) {
            var z = -i * ARCH_SPACING - 14f + (scroll % archPeriod)
            if (z > 10f) z -= archPeriod
            Matrix.setIdentityM(m, 0)
            val hw = RunnerGame.LANE_W * 1.5f + 0.7f
            val cyan = i % 2 == 0
            val r = if (cyan) 0.13f else 1f
            val g = if (cyan) 0.88f else 0.24f
            val b = if (cyan) 1f else 0.65f
            box(-hw, 2.6f, z, 0.25f, 5.2f, 0.25f, r, g, b, 0.9f)
            box(hw, 2.6f, z, 0.25f, 5.2f, 0.25f, r, g, b, 0.9f)
            box(0f, 5.2f, z, hw * 2 + 0.25f, 0.22f, 0.25f, r, g, b, 0.9f)
            // dark pylon bases
            box(-hw, 0.4f, z, 0.6f, 0.8f, 0.6f, 0.08f, 0.08f, 0.16f)
            box(hw, 0.4f, z, 0.6f, 0.8f, 0.6f, 0.08f, 0.08f, 0.16f)
        }
        // Skyline blocks
        val period = BUILDING_SPACING * (BUILDINGS / 2)
        for (i in 0 until BUILDINGS) {
            val side = if (i % 2 == 0) -1f else 1f
            var z = -(i / 2) * BUILDING_SPACING - (i % 2) * BUILDING_SPACING * 0.5f + (scroll * 0.9f % period)
            if (z > 12f) z -= period
            val h = bHeights[i]
            val w = bWidths[i]
            val x = side * bOffsets[i]
            Matrix.setIdentityM(m, 0)
            box(x, h / 2, z, w, h, w, 0.07f, 0.06f, 0.15f, 0f)
            val (ar, ag, ab) = when (bAccent[i]) {
                0 -> Triple(0.13f, 0.88f, 1f)
                1 -> Triple(1f, 0.24f, 0.65f)
                else -> Triple(0.55f, 0.36f, 1f)
            }
            // glowing roof edge + vertical neon stripe facing the track
            box(x, h + 0.05f, z, w + 0.1f, 0.12f, w + 0.1f, ar, ag, ab, 1f)
            box(x - side * (w / 2 + 0.02f), h * 0.5f, z, 0.06f, h * 0.8f, 0.25f, ar, ag, ab, 1f)
        }
    }

    private fun drawEntities() {
        for (e in game.entities) {
            if (e.z < -RunnerGame.SPAWN_Z - 5) continue
            if (e.done && e.isObstacle.not()) continue
            Matrix.setIdentityM(m, 0)
            val w = if (e.wide) RunnerGame.LANE_W * 3f else RunnerGame.LANE_W * 0.9f
            val x = if (e.wide) 0f else e.x
            val hitDim = if (e.hit) 0.35f else 1f
            when (e.kind) {
                RunnerGame.Kind.HURDLE -> {
                    val c = floatArrayOf(1f * hitDim, 0.76f * hitDim, 0.24f * hitDim)
                    box(x - w / 2 + 0.08f, 0.38f, e.z, 0.12f, 0.76f, 0.12f, 0.2f, 0.18f, 0.25f)
                    box(x + w / 2 - 0.08f, 0.38f, e.z, 0.12f, 0.76f, 0.12f, 0.2f, 0.18f, 0.25f)
                    box(x, 0.72f, e.z, w, 0.22f, 0.22f, c[0], c[1], c[2], 0.75f)
                    box(x, 0.42f, e.z, w * 0.96f, 0.08f, 0.08f, c[0], c[1], c[2], 0.5f)
                }
                RunnerGame.Kind.BAR -> {
                    val c = floatArrayOf(0.29f * hitDim, 0.66f * hitDim, 1f * hitDim)
                    box(x - w / 2 + 0.1f, 1.15f, e.z, 0.16f, 2.3f, 0.16f, 0.14f, 0.2f, 0.35f)
                    box(x + w / 2 - 0.1f, 1.15f, e.z, 0.16f, 2.3f, 0.16f, 0.14f, 0.2f, 0.35f)
                    box(x, 1.6f, e.z, w, 0.62f, 0.3f, c[0], c[1], c[2], 0.7f)
                    box(x, 1.26f, e.z, w * 0.98f, 0.06f, 0.34f, 0.8f, 0.95f, 1f, 1f)
                }
                RunnerGame.Kind.WALL -> {
                    val c = floatArrayOf(1f * hitDim, 0.35f * hitDim, 0.3f * hitDim)
                    box(x, 1.35f, e.z, w * 0.92f, 2.7f, 1.4f, c[0] * 0.75f, c[1] * 0.75f, c[2] * 0.75f, 0.25f)
                    // neon frame
                    box(x, 2.72f, e.z + 0.71f, w * 0.94f, 0.1f, 0.04f, 1f, 0.55f, 0.45f, 1f)
                    box(x - w * 0.46f, 1.35f, e.z + 0.71f, 0.1f, 2.7f, 0.04f, 1f, 0.55f, 0.45f, 1f)
                    box(x + w * 0.46f, 1.35f, e.z + 0.71f, 0.1f, 2.7f, 0.04f, 1f, 0.55f, 0.45f, 1f)
                    // chevrons
                    box(x, 1.6f, e.z + 0.71f, w * 0.6f, 0.16f, 0.04f, 1f, 0.85f, 0.8f, 1f)
                    box(x, 1.1f, e.z + 0.71f, w * 0.6f, 0.16f, 0.04f, 1f, 0.85f, 0.8f, 1f)
                }
                RunnerGame.Kind.COIN -> {
                    push()
                    Matrix.translateM(m, 0, e.x, e.y + sin(clock * 4f + e.z * 0.3f) * 0.08f, e.z)
                    Matrix.rotateM(m, 0, (clock * 220f + e.z * 20f) % 360f, 0f, 1f, 0f)
                    Matrix.rotateM(m, 0, 90f, 1f, 0f, 0f)
                    Matrix.scaleM(m, 0, 0.7f, 0.12f, 0.7f)
                    drawMesh(cyl, 1f, 0.78f, 0.2f, 0.55f)
                    pop()
                }
                RunnerGame.Kind.STAR -> {
                    push()
                    Matrix.translateM(m, 0, e.x, e.y + sin(clock * 3f) * 0.2f, e.z)
                    Matrix.rotateM(m, 0, (clock * 160f) % 360f, 0f, 1f, 0f)
                    Matrix.scaleM(m, 0, 1.3f, 1.3f, 1.3f)
                    drawMesh(star, 1f, 0.95f, 0.65f, 0.85f)
                    pop()
                }
            }
        }
    }

    private fun drawPlayer() {
        if (game.invulnerable > 0f && ((game.invulnerable * 10).toInt() % 2 == 0)) return
        val px = game.playerX
        val py = game.playerY
        val lean = (game.targetLane * RunnerGame.LANE_W - px) * -6f
        val cyc = game.runCycle
        val running = game.phase == RunnerGame.Phase.RUNNING || game.phase == RunnerGame.Phase.COUNTDOWN
        val airborne = game.jumpT >= 0f
        val swing = if (running && !airborne) sin(cyc) * 45f else if (airborne) 30f else 0f
        val bob = if (running && !airborne && !game.sliding) kotlin.math.abs(sin(cyc)) * 0.08f else 0f

        // palette
        val bodyR = 0.92f; val bodyG = 0.94f; val bodyB = 1f
        val acR = 0.13f; val acG = 0.88f; val acB = 1f

        Matrix.setIdentityM(m, 0)
        Matrix.translateM(m, 0, px, py + bob, 0f)
        Matrix.rotateM(m, 0, lean, 0f, 0f, 1f)
        if (game.sliding) {
            Matrix.translateM(m, 0, 0f, 0.15f, 0f)
            Matrix.rotateM(m, 0, 62f, 1f, 0f, 0f)
        } else {
            Matrix.rotateM(m, 0, -8f, 1f, 0f, 0f) // forward lean while running
        }

        // torso
        box(0f, 1.38f, 0f, 0.62f, 0.72f, 0.36f, bodyR, bodyG, bodyB, 0.05f)
        box(0f, 1.0f, 0f, 0.5f, 0.2f, 0.32f, 0.2f, 0.22f, 0.35f)
        // backpack w/ glowing core (visible from the chase camera)
        box(0f, 1.45f, 0.24f, 0.4f, 0.46f, 0.14f, 0.18f, 0.2f, 0.32f)
        box(0f, 1.45f, 0.32f, 0.12f, 0.3f, 0.03f, acR, acG, acB, 1f)
        // head + visor ring
        push()
        Matrix.translateM(m, 0, 0f, 2.0f, 0f)
        Matrix.scaleM(m, 0, 0.52f, 0.52f, 0.52f)
        drawMesh(sphere, bodyR, bodyG, bodyB, 0.05f)
        pop()
        box(0f, 2.0f, -0.05f, 0.56f, 0.12f, 0.46f, acR, acG, acB, 1f)
        box(0f, 2.32f, 0f, 0.06f, 0.18f, 0.06f, 1f, 0.24f, 0.65f, 1f) // antenna
        // arms
        for (s in intArrayOf(-1, 1)) {
            push()
            Matrix.translateM(m, 0, s * 0.42f, 1.68f, 0f)
            Matrix.rotateM(m, 0, if (game.sliding) -150f else -swing * s * 0.9f, 1f, 0f, 0f)
            box(0f, -0.3f, 0f, 0.17f, 0.6f, 0.17f, bodyR, bodyG, bodyB)
            box(0f, -0.62f, 0f, 0.2f, 0.12f, 0.2f, acR, acG, acB, 0.8f)
            pop()
        }
        // legs
        for (s in intArrayOf(-1, 1)) {
            push()
            Matrix.translateM(m, 0, s * 0.17f, 0.95f, 0f)
            val legA = when {
                game.sliding -> -80f
                airborne -> if (s < 0) -50f else 25f
                else -> swing * s
            }
            Matrix.rotateM(m, 0, legA, 1f, 0f, 0f)
            box(0f, -0.45f, 0f, 0.22f, 0.9f, 0.22f, 0.25f, 0.27f, 0.42f)
            box(0f, -0.9f, -0.06f, 0.26f, 0.14f, 0.38f, 1f, 0.24f, 0.65f, 0.9f) // shoes
            pop()
        }
    }

    private fun drawShadow() {
        Matrix.setIdentityM(m, 0)
        val s = (1.1f - game.playerY * 0.15f).coerceAtLeast(0.5f)
        Matrix.translateM(m, 0, game.playerX, 0.02f, 0f)
        Matrix.scaleM(m, 0, s, 0.01f, s * 0.8f)
        lit.set1f("uAlpha", 0.55f)
        drawMesh(cyl, 0f, 0f, 0f, 1f)
    }

    /** Additive halos around obstacles' neon parts: a cheap bloom substitute. */
    private fun drawHalos() {
        lit.set1f("uAlpha", 0.16f)
        for (e in game.entities) {
            if (!e.isObstacle || e.hit || e.z < -70f) continue
            Matrix.setIdentityM(m, 0)
            val w = if (e.wide) RunnerGame.LANE_W * 3f else RunnerGame.LANE_W * 0.9f
            val x = if (e.wide) 0f else e.x
            when (e.kind) {
                RunnerGame.Kind.HURDLE -> box(x, 0.72f, e.z, w + 0.3f, 0.6f, 0.6f, 1f, 0.76f, 0.24f, 1f)
                RunnerGame.Kind.BAR -> box(x, 1.6f, e.z, w + 0.3f, 1.0f, 0.7f, 0.29f, 0.66f, 1f, 1f)
                RunnerGame.Kind.WALL -> box(x, 1.35f, e.z + 0.7f, w + 0.35f, 3.1f, 0.1f, 1f, 0.35f, 0.3f, 1f)
                else -> Unit
            }
        }
    }

    // ---------------------------------------------------------------- particles

    private fun burst(x: Float, y: Float, z: Float, n: Int, r: Float, g: Float, b: Float, power: Float) {
        repeat(n) {
            val i = pNext
            pNext = (pNext + 1) % MAX_P
            pPos[i * 3] = x; pPos[i * 3 + 1] = y; pPos[i * 3 + 2] = z
            val a = rnd.nextFloat() * 2 * PI.toFloat()
            val up = rnd.nextFloat()
            pVel[i * 3] = kotlin.math.cos(a) * power * (0.3f + rnd.nextFloat())
            pVel[i * 3 + 1] = up * power * 1.2f
            pVel[i * 3 + 2] = sin(a) * power * (0.3f + rnd.nextFloat())
            pLife[i] = 0.6f + rnd.nextFloat() * 0.4f
            pCol[i * 3] = r; pCol[i * 3 + 1] = g; pCol[i * 3 + 2] = b
        }
    }

    private fun updateParticles(dt: Float) {
        val dz = if (game.phase == RunnerGame.Phase.RUNNING) game.speed * dt * 0.5f else 0f
        for (i in 0 until MAX_P) {
            if (pLife[i] <= 0f) continue
            pLife[i] -= dt
            pVel[i * 3 + 1] -= 9f * dt
            pPos[i * 3] += pVel[i * 3] * dt
            pPos[i * 3 + 1] += pVel[i * 3 + 1] * dt
            pPos[i * 3 + 2] += pVel[i * 3 + 2] * dt + dz
        }
    }

    private fun drawParticles() {
        for (i in 0 until MAX_P) {
            val life = pLife[i]
            if (life <= 0f) continue
            lit.set1f("uAlpha", (life * 1.4f).coerceAtMost(1f))
            Matrix.setIdentityM(m, 0)
            Matrix.translateM(m, 0, pPos[i * 3], pPos[i * 3 + 1], pPos[i * 3 + 2])
            Matrix.rotateM(m, 0, life * 500f, 1f, 1f, 0f)
            val s = 0.12f + life * 0.1f
            Matrix.scaleM(m, 0, s, s, s)
            drawMesh(cube, pCol[i * 3], pCol[i * 3 + 1], pCol[i * 3 + 2], 1f)
        }
    }

    companion object {
        private const val CAM_Y = 3.5f
        private const val CAM_Z = 6.4f
        private const val LOOK_Y = 1.2f
        private const val LOOK_Z = -9f
        private const val ARCHES = 6
        private const val ARCH_SPACING = 30f
        private const val BUILDINGS = 36
        private const val BUILDING_SPACING = 12f
        private const val MAX_P = 240
    }
}
