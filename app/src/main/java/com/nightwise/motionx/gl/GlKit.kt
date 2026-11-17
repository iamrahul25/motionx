package com.nightwise.motionx.gl

import android.opengl.GLES30
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Compiled + linked GLSL program with cached uniform locations. */
class ShaderProgram(vertexSrc: String, fragmentSrc: String) {
    val id: Int
    private val uniforms = HashMap<String, Int>()

    init {
        val vs = compile(GLES30.GL_VERTEX_SHADER, vertexSrc)
        val fs = compile(GLES30.GL_FRAGMENT_SHADER, fragmentSrc)
        id = GLES30.glCreateProgram()
        GLES30.glAttachShader(id, vs)
        GLES30.glAttachShader(id, fs)
        GLES30.glLinkProgram(id)
        val ok = IntArray(1)
        GLES30.glGetProgramiv(id, GLES30.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(id)
            Log.e("GL", "Link failed: $log")
            throw RuntimeException("Shader link failed: $log")
        }
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
    }

    fun use() = GLES30.glUseProgram(id)

    fun loc(name: String): Int = uniforms.getOrPut(name) { GLES30.glGetUniformLocation(id, name) }

    fun set1f(name: String, v: Float) = GLES30.glUniform1f(loc(name), v)
    fun set2f(name: String, a: Float, b: Float) = GLES30.glUniform2f(loc(name), a, b)
    fun set3f(name: String, a: Float, b: Float, c: Float) = GLES30.glUniform3f(loc(name), a, b, c)
    fun set4f(name: String, a: Float, b: Float, c: Float, d: Float) = GLES30.glUniform4f(loc(name), a, b, c, d)
    fun setMat4(name: String, m: FloatArray) = GLES30.glUniformMatrix4fv(loc(name), 1, false, m, 0)

    private fun compile(type: Int, src: String): Int {
        val s = GLES30.glCreateShader(type)
        GLES30.glShaderSource(s, src)
        GLES30.glCompileShader(s)
        val ok = IntArray(1)
        GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(s)
            Log.e("GL", "Compile failed: $log\n$src")
            throw RuntimeException("Shader compile failed: $log")
        }
        return s
    }
}

/** Indexed triangle mesh with interleaved position(3) + normal(3), uploaded to a VAO. */
class Mesh(vertices: FloatArray, indices: ShortArray) {
    private val vao: Int
    val indexCount = indices.size

    init {
        val ids = IntArray(1)
        GLES30.glGenVertexArrays(1, ids, 0)
        vao = ids[0]
        GLES30.glBindVertexArray(vao)
        val bufs = IntArray(2)
        GLES30.glGenBuffers(2, bufs, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, bufs[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.size * 4, floatBuf(vertices), GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, bufs[1])
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, indices.size * 2, shortBuf(indices), GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 24, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 24, 12)
        GLES30.glBindVertexArray(0)
    }

    fun draw() {
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_SHORT, 0)
        GLES30.glBindVertexArray(0)
    }

    companion object {
        fun floatBuf(a: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(a); position(0) }

        fun shortBuf(a: ShortArray): ShortBuffer =
            ByteBuffer.allocateDirect(a.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer().apply { put(a); position(0) }
    }
}

/** Procedural mesh generation (all meshes are unit-ish and scaled by the model matrix). */
class MeshBuilder {
    private val v = ArrayList<Float>()
    private val idx = ArrayList<Short>()
    private val count get() = v.size / 6

    private fun vert(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float): Int {
        v.add(x); v.add(y); v.add(z); v.add(nx); v.add(ny); v.add(nz)
        return count - 1
    }

    private fun quad(a: Int, b: Int, c: Int, d: Int) {
        idx.add(a.toShort()); idx.add(b.toShort()); idx.add(c.toShort())
        idx.add(a.toShort()); idx.add(c.toShort()); idx.add(d.toShort())
    }

    /** Axis-aligned box centred at (cx, cy, cz). */
    fun box(cx: Float = 0f, cy: Float = 0f, cz: Float = 0f, sx: Float = 1f, sy: Float = 1f, sz: Float = 1f): MeshBuilder {
        val hx = sx / 2; val hy = sy / 2; val hz = sz / 2
        val faces = arrayOf(
            floatArrayOf(0f, 0f, 1f), floatArrayOf(0f, 0f, -1f), floatArrayOf(1f, 0f, 0f),
            floatArrayOf(-1f, 0f, 0f), floatArrayOf(0f, 1f, 0f), floatArrayOf(0f, -1f, 0f),
        )
        for (n in faces) {
            // two tangent axes
            val u = if (n[1] != 0f) floatArrayOf(1f, 0f, 0f) else floatArrayOf(0f, 1f, 0f)
            val w = floatArrayOf(n[1] * u[2] - n[2] * u[1], n[2] * u[0] - n[0] * u[2], n[0] * u[1] - n[1] * u[0])
            fun p(su: Float, sw: Float): Int {
                val x = cx + (n[0] + u[0] * su + w[0] * sw) * hx
                val y = cy + (n[1] + u[1] * su + w[1] * sw) * hy
                val z = cz + (n[2] + u[2] * su + w[2] * sw) * hz
                return vert(x, y, z, n[0], n[1], n[2])
            }
            val a = p(-1f, -1f); val b = p(1f, -1f); val c = p(1f, 1f); val d = p(-1f, 1f)
            // ensure CCW when seen from outside
            quad(a, d, c, b)
        }
        return this
    }

    /** Cylinder along Y, centred at origin, radius r, height h. */
    fun cylinder(r: Float = 0.5f, h: Float = 1f, seg: Int = 24, cy: Float = 0f): MeshBuilder {
        val hh = h / 2
        val base = count
        for (i in 0..seg) {
            val a = (i.toFloat() / seg) * 2 * PI.toFloat()
            val c = cos(a); val s = sin(a)
            vert(c * r, cy - hh, s * r, c, 0f, s)
            vert(c * r, cy + hh, s * r, c, 0f, s)
        }
        for (i in 0 until seg) {
            val a = base + i * 2
            quad(a, a + 1, a + 3, a + 2)
        }
        for (top in listOf(true, false)) {
            val y = if (top) cy + hh else cy - hh
            val ny = if (top) 1f else -1f
            val center = vert(0f, y, 0f, 0f, ny, 0f)
            val ring = count
            for (i in 0..seg) {
                val a = (i.toFloat() / seg) * 2 * PI.toFloat()
                vert(cos(a) * r, y, sin(a) * r, 0f, ny, 0f)
            }
            for (i in 0 until seg) {
                if (top) {
                    idx.add(center.toShort()); idx.add((ring + i + 1).toShort()); idx.add((ring + i).toShort())
                } else {
                    idx.add(center.toShort()); idx.add((ring + i).toShort()); idx.add((ring + i + 1).toShort())
                }
            }
        }
        return this
    }

    fun sphere(r: Float = 0.5f, rings: Int = 14, seg: Int = 20): MeshBuilder {
        val base = count
        for (i in 0..rings) {
            val th = i.toFloat() / rings * PI.toFloat()
            for (j in 0..seg) {
                val ph = j.toFloat() / seg * 2 * PI.toFloat()
                val x = sin(th) * cos(ph); val y = cos(th); val z = sin(th) * sin(ph)
                vert(x * r, y * r, z * r, x, y, z)
            }
        }
        for (i in 0 until rings) for (j in 0 until seg) {
            val a = base + i * (seg + 1) + j
            val b = a + seg + 1
            idx.add(a.toShort()); idx.add((a + 1).toShort()); idx.add(b.toShort())
            idx.add(b.toShort()); idx.add((a + 1).toShort()); idx.add((b + 1).toShort())
        }
        return this
    }

    /** Five-point star prism facing +Z, thickness [t]. */
    fun star(rOuter: Float = 0.6f, rInner: Float = 0.26f, t: Float = 0.22f): MeshBuilder {
        val pts = (0 until 10).map { i ->
            val a = (PI / 2 + i * PI / 5).toFloat()
            val r = if (i % 2 == 0) rOuter else rInner
            floatArrayOf(cos(a) * r, sin(a) * r)
        }
        for (front in listOf(true, false)) {
            val z = if (front) t / 2 else -t / 2
            val nz = if (front) 1f else -1f
            val c = vert(0f, 0f, z, 0f, 0f, nz)
            val ring = count
            pts.forEach { vert(it[0], it[1], z, 0f, 0f, nz) }
            for (i in 0 until 10) {
                val a = ring + i; val b = ring + (i + 1) % 10
                if (front) { idx.add(c.toShort()); idx.add(a.toShort()); idx.add(b.toShort()) }
                else { idx.add(c.toShort()); idx.add(b.toShort()); idx.add(a.toShort()) }
            }
        }
        for (i in 0 until 10) {
            val p0 = pts[i]; val p1 = pts[(i + 1) % 10]
            val ex = p1[0] - p0[0]; val ey = p1[1] - p0[1]
            val len = sqrt(ex * ex + ey * ey)
            val nx = ey / len; val ny = -ex / len
            val a = vert(p0[0], p0[1], t / 2, nx, ny, 0f)
            val b = vert(p1[0], p1[1], t / 2, nx, ny, 0f)
            val c = vert(p1[0], p1[1], -t / 2, nx, ny, 0f)
            val d = vert(p0[0], p0[1], -t / 2, nx, ny, 0f)
            quad(a, d, c, b)
        }
        return this
    }

    /** Flat quad on the XZ plane (normal +Y). */
    fun plane(x0: Float, z0: Float, x1: Float, z1: Float, y: Float = 0f): MeshBuilder {
        val a = vert(x0, y, z0, 0f, 1f, 0f)
        val b = vert(x1, y, z0, 0f, 1f, 0f)
        val c = vert(x1, y, z1, 0f, 1f, 0f)
        val d = vert(x0, y, z1, 0f, 1f, 0f)
        quad(a, d, c, b)
        return this
    }

    fun build(): Mesh = Mesh(v.toFloatArray(), idx.toShortArray())
}
