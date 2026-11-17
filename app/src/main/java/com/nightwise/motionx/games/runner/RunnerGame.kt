package com.nightwise.motionx.games.runner

import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

/**
 * Pure game logic for Neon Dash (no Android / GL). The player stays at z = 0; the world moves
 * towards +z. Lanes are at x = -LANE_W, 0, +LANE_W.
 */
class RunnerGame(val config: RunnerConfig, seed: Long = System.nanoTime()) {

    enum class Phase { WAITING, COUNTDOWN, RUNNING, PAUSED, OVER }
    enum class Kind { HURDLE, BAR, WALL, COIN, STAR }
    enum class Action(val say: String, val label: String) {
        LEFT("Move left", "◀ MOVE LEFT"), RIGHT("Move right", "MOVE RIGHT ▶"),
        JUMP("Hands up, jump", "✋ ▲ JUMP"), DUCK("Duck", "▼ DUCK"),
    }

    class Entity(val kind: Kind, val lane: Int, var z: Float, val y: Float = 0f, val wide: Boolean = false) {
        var done = false // collected, hit or cleared
        var hit = false
        var cued = false
        val x get() = lane * LANE_W
        val isObstacle get() = kind == Kind.HURDLE || kind == Kind.BAR || kind == Kind.WALL
    }

    sealed interface Event {
        data class Coin(val x: Float, val y: Float, val z: Float) : Event
        data class Star(val x: Float, val y: Float, val z: Float) : Event
        data class Hit(val kind: Kind) : Event
        data object Jump : Event
        data object Over : Event
        data class SpeedUp(val level: Int) : Event
        data class Cue(val action: Action) : Event
    }

    private val rnd = Random(seed)

    var phase = Phase.WAITING
        private set
    var countdown = 0f
        private set
    var time = 0f
        private set
    var distance = 0f
        private set
    var speedLevel = config.startSpeed.toFloat()
        private set
    val speed get() = RunnerConfig.worldSpeed(speedLevel)

    var lives = config.lives
        private set
    var coins = 0
        private set
    var stars = 0
        private set
    var cleared = 0
        private set
    var hits = 0
        private set
    var jumps = 0
        private set
    var ducks = 0
        private set
    var laneChanges = 0
        private set
    var combo = 0
        private set
    private var bonusScore = 0f

    val score: Int get() = ((distance * 1f + bonusScore) * config.scoreMultiplier).toInt()

    // Player
    var targetLane = 0
        private set
    var playerX = 0f
        private set
    var playerY = 0f
        private set
    var jumpT = -1f
        private set
    var sliding = false
        private set
    private var slideTimer = 0f
    private var bodyDucking = false
    var invulnerable = 0f
        private set
    var hitFlash = 0f
        private set
    var runCycle = 0f
        private set

    /** Assist callouts on/off (can be toggled mid-run from the pause menu). */
    var assistOn = config.assist

    /** Latest assist callout and how long ago it fired (for the HUD arrow). */
    var cue: Action? = null
        private set
    var cueAge = 99f
        private set

    val entities = ArrayList<Entity>(128)
    val events = ArrayList<Event>()

    private var nextRowZ = -RunnerConfig.worldSpeed(config.startSpeed.toFloat()) * 4.5f
    private var lastSpeedInt = config.startSpeed

    val jumpDuration get() = (0.85f * (18f / speed).coerceIn(0.75f, 1.35f))

    fun startCountdown() {
        if (phase == Phase.WAITING || phase == Phase.PAUSED) {
            phase = Phase.COUNTDOWN
            countdown = 3f
        }
    }

    fun pause() {
        if (phase == Phase.RUNNING || phase == Phase.COUNTDOWN) phase = Phase.PAUSED
    }

    // ---------------- input ----------------

    fun setLane(lane: Int) {
        val l = lane.coerceIn(-1, 1)
        if (l != targetLane) {
            targetLane = l
            if (phase == Phase.RUNNING) laneChanges++
        }
    }

    fun nudgeLane(delta: Int) = setLane(targetLane + delta)

    fun jump() {
        if (phase != Phase.RUNNING || config.moves == Moves.LANES_ONLY) return
        if (jumpT < 0f || jumpT > jumpDuration * 0.8f) {
            jumpT = 0f
            sliding = false
            slideTimer = 0f
            jumps++
            events += Event.Jump
        }
    }

    fun setDucking(d: Boolean) {
        if (d && !bodyDucking) duck(0.45f)
        bodyDucking = d
    }

    fun duck(minHold: Float = 0.9f) {
        if (phase != Phase.RUNNING || config.moves != Moves.ALL) return
        if (!sliding) ducks++
        if (jumpT >= 0f) jumpT = -1f // fast-fall into a slide
        sliding = true
        slideTimer = maxOf(slideTimer, minHold)
    }

    // ---------------- simulation ----------------

    fun update(dtIn: Float) {
        val dt = dtIn.coerceIn(0f, 0.05f)
        hitFlash = (hitFlash - dt).coerceAtLeast(0f)
        when (phase) {
            Phase.COUNTDOWN -> {
                countdown -= dt
                animatePlayer(dt, 0.4f)
                if (countdown <= 0f) phase = Phase.RUNNING
                return
            }
            Phase.RUNNING -> Unit
            else -> return
        }

        time += dt
        if (config.mode == SpeedMode.INCREASING) {
            val p = min(1f, time / RAMP_SECONDS)
            speedLevel = config.startSpeed + (config.topSpeed - config.startSpeed) * p
            val s = speedLevel.toInt()
            if (s > lastSpeedInt) {
                lastSpeedInt = s; events += Event.SpeedUp(s)
            }
        }
        val dz = speed * dt
        distance += dz
        invulnerable = (invulnerable - dt).coerceAtLeast(0f)

        animatePlayer(dt, 1f)

        // move world
        for (e in entities) e.z += dz
        nextRowZ += dz
        while (nextRowZ > -SPAWN_Z) spawnRow()

        collide()
        if (assistOn) assist()
        cueAge += dt
        entities.removeAll { it.z > 8f }
    }

    private fun animatePlayer(dt: Float, runRate: Float) {
        // lane change: critically-damped-ish exponential approach
        val tx = targetLane * LANE_W
        playerX += (tx - playerX) * (1f - kotlin.math.exp(-14f * dt))
        runCycle += dt * runRate * (6f + speed * 0.25f)

        if (jumpT >= 0f) {
            jumpT += dt
            val p = jumpT / jumpDuration
            if (p >= 1f) {
                jumpT = -1f; playerY = 0f
            } else {
                playerY = 4f * JUMP_H * p * (1f - p)
            }
        } else {
            playerY = 0f
        }
        if (sliding) {
            slideTimer -= dt
            if (slideTimer <= 0f && !bodyDucking) sliding = false
        }
    }

    private fun collide() {
        val pw = 0.55f
        for (e in entities) {
            if (e.done) continue
            val xOverlap = if (e.wide) true else abs(playerX - e.x) < (LANE_W / 2 + pw) * 0.62f
            when (e.kind) {
                Kind.COIN, Kind.STAR -> {
                    if (abs(e.z) < 0.9f && abs(playerX - e.x) < 0.95f && abs((playerY + 0.9f) - e.y) < 1.3f) {
                        e.done = true
                        if (e.kind == Kind.COIN) {
                            coins++; bonusScore += 10f; events += Event.Coin(e.x, e.y, e.z)
                        } else {
                            stars++; bonusScore += 100f; events += Event.Star(e.x, e.y, e.z)
                        }
                    }
                }
                else -> {
                    val depth = if (e.kind == Kind.WALL) 0.9f else 0.25f
                    if (e.z > -depth - 0.35f && e.z < depth + 0.35f && xOverlap) {
                        val safe = when (e.kind) {
                            Kind.HURDLE -> playerY > HURDLE_H * 0.85f
                            Kind.BAR -> sliding
                            else -> false
                        }
                        if (!safe && invulnerable <= 0f) {
                            e.done = true; e.hit = true
                            onHit(e.kind)
                        }
                    } else if (e.z > depth + 0.35f) {
                        e.done = true
                        cleared++
                        combo++
                        bonusScore += 25f + min(combo, 20) * 2f
                    }
                }
            }
        }
    }

    private fun onHit(kind: Kind) {
        hits++
        combo = 0
        hitFlash = 0.5f
        invulnerable = 2.0f
        events += Event.Hit(kind)
        if (config.lives > 0) {
            lives--
            if (lives <= 0) {
                phase = Phase.OVER
                events += Event.Over
            }
        }
    }

    /** End a run early (e.g. user quits a training run). */
    fun finish() {
        if (phase != Phase.OVER) {
            phase = Phase.OVER
            events += Event.Over
        }
    }

    // ---------------- level generation ----------------

    private fun allowedKinds(): List<Kind> = when (config.moves) {
        Moves.ALL -> listOf(Kind.WALL, Kind.WALL, Kind.HURDLE, Kind.HURDLE, Kind.BAR, Kind.BAR)
        Moves.NO_DUCK -> listOf(Kind.WALL, Kind.WALL, Kind.HURDLE, Kind.HURDLE)
        Moves.LANES_ONLY -> listOf(Kind.WALL)
    }

    /**
     * Seconds between obstacle rows. Body moves need real time (hop ≈ 0.5 s plus tracking latency),
     * so slow levels leave lots of room: speed 1 → ~3.2 s, speed 5 → ~2.4 s, speed 9 → ~1.6 s.
     */
    private fun rowGapSeconds(): Float {
        val base = 3.4f - speedLevel * 0.2f
        return base.coerceIn(1.3f, 3.4f) * (0.95f + rnd.nextFloat() * 0.3f)
    }

    /** Voice/HUD callout shortly before each obstacle row that needs an action. */
    private fun assist() {
        val lead = speed * CUE_SECONDS
        val next = entities.filter { it.isObstacle && !it.cued && !it.done && it.z < 0f && it.z > -lead }
            .maxByOrNull { it.z } ?: return
        val row = entities.filter { it.isObstacle && !it.done && abs(it.z - next.z) < 1.2f }
        row.forEach { it.cued = true }
        val walls = row.filter { it.kind == Kind.WALL }.map { it.lane }.toSet()
        val action: Action? = when {
            walls.isNotEmpty() && targetLane in walls -> {
                val free = (-1..1).filter { it !in walls }
                val goal = free.minByOrNull { abs(it - targetLane) * 10 + abs(it) } ?: return
                if (goal < targetLane) Action.LEFT else Action.RIGHT
            }
            row.any { it.kind == Kind.HURDLE && (it.wide || it.lane == targetLane) } -> Action.JUMP
            row.any { it.kind == Kind.BAR && (it.wide || it.lane == targetLane) } -> Action.DUCK
            else -> null
        }
        if (action != null) {
            cue = action
            cueAge = 0f
            events += Event.Cue(action)
        }
    }

    private fun spawnRow() {
        val z = nextRowZ
        val kinds = allowedKinds()
        val kind = kinds[rnd.nextInt(kinds.size)]
        val hard = speedLevel >= 6
        val medium = speedLevel >= 3
        when (kind) {
            Kind.WALL -> {
                val blockTwo = hard && rnd.nextFloat() < 0.45f
                val free = rnd.nextInt(3) - 1
                val lanes = if (blockTwo) (-1..1).filter { it != free } else listOf((-1..1).filter { it != targetLane || rnd.nextFloat() < 0.6f }.random(rnd))
                lanes.forEach { entities += Entity(Kind.WALL, it, z) }
                val coinLane = if (blockTwo) free else (-1..1).filter { it !in lanes }.random(rnd)
                coinLine(coinLane, z + 6f, 5)
            }
            Kind.HURDLE, Kind.BAR -> {
                val wide = rnd.nextFloat() < (if (config.moves == Moves.LANES_ONLY) 0f else if (medium) 0.5f else 0.7f)
                if (wide) {
                    entities += Entity(kind, 0, z, wide = true)
                    if (kind == Kind.HURDLE) coinArc(rnd.nextInt(3) - 1, z) else coinLine(rnd.nextInt(3) - 1, z + 1f, 3, y = 0.6f)
                } else {
                    val lane = rnd.nextInt(3) - 1
                    entities += Entity(kind, lane, z)
                    if (kind == Kind.HURDLE) coinArc(lane, z)
                    else coinLine(lane, z - 3f, 3, y = 0.6f)
                }
            }
            else -> Unit
        }
        if (rnd.nextFloat() < 0.07f) {
            val lane = rnd.nextInt(3) - 1
            entities += Entity(Kind.STAR, lane, z - speed * 0.6f, y = 1.4f)
        }
        nextRowZ -= speed * rowGapSeconds()
    }

    private fun coinLine(lane: Int, zStart: Float, n: Int, y: Float = 0.9f) {
        for (i in 0 until n) entities += Entity(Kind.COIN, lane, zStart - i * 2.2f, y = y)
    }

    private fun coinArc(lane: Int, zCenter: Float) {
        val half = speed * jumpDuration / 2f
        for (i in -2..2) {
            val zz = zCenter + i * half / 2.5f
            val p = 0.5f + i / 5f
            entities += Entity(Kind.COIN, lane, zz, y = 0.9f + 4f * JUMP_H * p * (1 - p))
        }
    }

    companion object {
        const val LANE_W = 2.2f
        const val SPAWN_Z = 110f
        const val JUMP_H = 1.7f
        const val HURDLE_H = 0.8f
        const val BAR_Y = 1.25f
        const val RAMP_SECONDS = 180f
        /** Callout this many seconds before an obstacle arrives. */
        const val CUE_SECONDS = 1.9f
    }
}
