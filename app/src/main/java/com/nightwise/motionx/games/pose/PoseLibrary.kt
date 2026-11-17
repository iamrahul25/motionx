package com.nightwise.motionx.games.pose

/**
 * Pose definitions for Pose Match.
 *
 * Everything is described in *screen space as the player sees themselves* (selfie mirror):
 * "SL" = the limb on the screen-left side, "SR" = screen-right. Angles are the direction of a bone
 * in degrees: 0 = pointing right, 90 = down, 180 = left, -90 = up.
 */
enum class Bone(val label: String) {
    TORSO("Torso"),
    SL_UPPER_ARM("Arm"), SL_FOREARM("Forearm"),
    SR_UPPER_ARM("Arm"), SR_FOREARM("Forearm"),
    SL_THIGH("Thigh"), SL_SHIN("Shin"),
    SR_THIGH("Thigh"), SR_SHIN("Shin");

    /** Same bone on the other side of the body. */
    val partner: Bone
        get() = when (this) {
            TORSO -> TORSO
            SL_UPPER_ARM -> SR_UPPER_ARM; SR_UPPER_ARM -> SL_UPPER_ARM
            SL_FOREARM -> SR_FOREARM; SR_FOREARM -> SL_FOREARM
            SL_THIGH -> SR_THIGH; SR_THIGH -> SL_THIGH
            SL_SHIN -> SR_SHIN; SR_SHIN -> SL_SHIN
        }
}

enum class Joint {
    NOSE, SL_SHOULDER, SR_SHOULDER, SL_ELBOW, SR_ELBOW, SL_WRIST, SR_WRIST,
    SL_HIP, SR_HIP, SL_KNEE, SR_KNEE, SL_ANKLE, SR_ANKLE;

    val partner: Joint
        get() = when (this) {
            NOSE -> NOSE
            SL_SHOULDER -> SR_SHOULDER; SR_SHOULDER -> SL_SHOULDER
            SL_ELBOW -> SR_ELBOW; SR_ELBOW -> SL_ELBOW
            SL_WRIST -> SR_WRIST; SR_WRIST -> SL_WRIST
            SL_HIP -> SR_HIP; SR_HIP -> SL_HIP
            SL_KNEE -> SR_KNEE; SR_KNEE -> SL_KNEE
            SL_ANKLE -> SR_ANKLE; SR_ANKLE -> SL_ANKLE
        }
}

sealed interface Constraint {
    /** Distance between two joints must be ≤ [max] torso lengths. */
    data class Near(val a: Joint, val b: Joint, val max: Float, val hint: String) : Constraint

    /** Hip-to-ankle distance on one side ≤ [maxRatio] torso lengths (knee bent / leg raised). */
    data class LegBent(val sl: Boolean, val maxRatio: Float, val hint: String) : Constraint

    fun mirrored(): Constraint = when (this) {
        is Near -> copy(a = a.partner, b = b.partner)
        is LegBent -> copy(sl = !sl)
    }
}

enum class Tier(val label: String, val emoji: String, val tolerance: Float, val hold: Float, val points: Int) {
    EASY("Easy", "🌱", 28f, 3f, 100),
    MEDIUM("Medium", "🔥", 25f, 3f, 150),
    HARD("Hard", "⚡", 22f, 4f, 200),
    VERY_HARD("Very hard", "💎", 20f, 4f, 300),
    EXTREME("Extreme", "👑", 20f, 5f, 400),
}

data class PoseDef(
    val id: String,
    val name: String,
    val tier: Tier,
    val emoji: String,
    val cue: String,
    val angles: Map<Bone, Float>,
    val constraints: List<Constraint> = emptyList(),
    /** Asymmetric pose: the mirror image (other side) also counts. */
    val mirrorOk: Boolean = false,
    /** Done side-on to the camera (narrow shoulders/hips in the figure, L/R labels unreliable). */
    val sideView: Boolean = false,
    /** Drawing only: foreshortening of each leg (1 = full length). */
    val legScaleSL: Float = 1f,
    val legScaleSR: Float = 1f,
    val tolerance: Float = tier.tolerance,
    val hold: Float = tier.hold,
) {
    fun mirrored(): PoseDef = copy(
        angles = angles.entries.associate { (b, a) -> b.partner to reflect(a) },
        constraints = constraints.map { it.mirrored() },
        legScaleSL = legScaleSR,
        legScaleSR = legScaleSL,
    )

    companion object {
        /** Reflect a direction across the vertical axis. */
        fun reflect(a: Float): Float = norm(180f - a)
        fun norm(a: Float): Float {
            var x = a % 360f
            if (x > 180f) x -= 360f
            if (x <= -180f) x += 360f
            return x
        }
    }
}

object PoseLibrary {
    // Direction shorthands (screen space)
    private const val R = 0f
    private const val D = 90f
    private const val L = 180f
    private const val U = -90f

    private val ARMS_DOWN = mapOf(Bone.SL_UPPER_ARM to 100f, Bone.SL_FOREARM to 95f, Bone.SR_UPPER_ARM to 80f, Bone.SR_FOREARM to 85f)
    private val ARMS_UP = mapOf(Bone.SL_UPPER_ARM to -100f, Bone.SL_FOREARM to -95f, Bone.SR_UPPER_ARM to -80f, Bone.SR_FOREARM to -85f)
    private val ARMS_T = mapOf(Bone.SL_UPPER_ARM to L, Bone.SL_FOREARM to L, Bone.SR_UPPER_ARM to R, Bone.SR_FOREARM to R)
    private val GOAL_POST = mapOf(Bone.SL_UPPER_ARM to L, Bone.SL_FOREARM to U, Bone.SR_UPPER_ARM to R, Bone.SR_FOREARM to U)
    private val LEGS = mapOf(Bone.SL_THIGH to 93f, Bone.SL_SHIN to 90f, Bone.SR_THIGH to 87f, Bone.SR_SHIN to 90f)
    private val UPRIGHT = mapOf(Bone.TORSO to U)

    private fun m(vararg parts: Map<Bone, Float>, extra: Map<Bone, Float> = emptyMap()): Map<Bone, Float> {
        val out = LinkedHashMap<Bone, Float>()
        parts.forEach { out.putAll(it) }
        out.putAll(extra)
        return out
    }

    private fun a(vararg pairs: Pair<Bone, Float>) = mapOf(*pairs)

    val ALL: List<PoseDef> = listOf(
        // ------------------------------------------------------------------ EASY
        PoseDef("stand", "Stand tall", Tier.EASY, "🧍", "Stand straight, arms relaxed by your sides", m(UPRIGHT, ARMS_DOWN, LEGS)),
        PoseDef("reach", "Reach for the sky", Tier.EASY, "🙌", "Raise both arms straight up", m(UPRIGHT, ARMS_UP, LEGS)),
        PoseDef("tpose", "T-pose", Tier.EASY, "✈️", "Arms straight out to the sides at shoulder height", m(UPRIGHT, ARMS_T, LEGS)),
        PoseDef(
            "onehand", "One hand up", Tier.EASY, "✋", "Raise one arm straight up, the other stays down",
            m(UPRIGHT, LEGS, a(Bone.SL_UPPER_ARM to 100f, Bone.SL_FOREARM to 95f, Bone.SR_UPPER_ARM to -80f, Bone.SR_FOREARM to -85f)),
            mirrorOk = true,
        ),
        PoseDef(
            "hips", "Hands on hips", Tier.EASY, "🦸", "Put both hands on your hips, elbows out",
            m(UPRIGHT, LEGS, a(Bone.SL_UPPER_ARM to 130f, Bone.SL_FOREARM to 50f, Bone.SR_UPPER_ARM to 50f, Bone.SR_FOREARM to 130f)),
            constraints = listOf(
                Constraint.Near(Joint.SL_WRIST, Joint.SL_HIP, 0.4f, "Hands on your hips"),
                Constraint.Near(Joint.SR_WRIST, Joint.SR_HIP, 0.4f, "Hands on your hips"),
            ),
        ),
        PoseDef(
            "ypose", "Y-pose", Tier.EASY, "🙆", "Arms up and wide like the letter Y",
            m(UPRIGHT, LEGS, a(Bone.SL_UPPER_ARM to -130f, Bone.SL_FOREARM to -130f, Bone.SR_UPPER_ARM to -50f, Bone.SR_FOREARM to -50f)),
        ),
        PoseDef("goal", "Goal post", Tier.EASY, "🌵", "Upper arms out to the sides, forearms pointing up", m(UPRIGHT, GOAL_POST, LEGS)),
        PoseDef(
            "head", "Hands on head", Tier.EASY, "🤯", "Elbows wide, both hands on top of your head",
            m(UPRIGHT, LEGS, a(Bone.SL_UPPER_ARM to -150f, Bone.SL_FOREARM to -20f, Bone.SR_UPPER_ARM to -30f, Bone.SR_FOREARM to -160f)),
            constraints = listOf(
                Constraint.Near(Joint.SL_WRIST, Joint.NOSE, 0.5f, "Hands on your head"),
                Constraint.Near(Joint.SR_WRIST, Joint.NOSE, 0.5f, "Hands on your head"),
            ),
        ),

        // ------------------------------------------------------------------ MEDIUM
        PoseDef(
            "chair", "Chair pose", Tier.MEDIUM, "🪑", "Arms up, bend your knees like sitting on a chair",
            m(UPRIGHT, ARMS_UP, a(Bone.SL_THIGH to 100f, Bone.SL_SHIN to 80f, Bone.SR_THIGH to 80f, Bone.SR_SHIN to 100f)),
            constraints = listOf(
                Constraint.LegBent(true, 1.25f, "Bend your knees more"),
                Constraint.LegBent(false, 1.25f, "Bend your knees more"),
            ),
            legScaleSL = 0.72f, legScaleSR = 0.72f,
        ),
        PoseDef(
            "warrior2", "Warrior II", Tier.MEDIUM, "⚔️", "Wide stance, bend one knee, arms out in a T",
            m(UPRIGHT, ARMS_T, a(Bone.SL_THIGH to 125f, Bone.SL_SHIN to 92f, Bone.SR_THIGH to 58f, Bone.SR_SHIN to 58f)),
            mirrorOk = true,
        ),
        PoseDef(
            "star", "Star", Tier.MEDIUM, "⭐", "Arms and legs wide like a star",
            m(UPRIGHT, a(
                Bone.SL_UPPER_ARM to -135f, Bone.SL_FOREARM to -135f, Bone.SR_UPPER_ARM to -45f, Bone.SR_FOREARM to -45f,
                Bone.SL_THIGH to 112f, Bone.SL_SHIN to 112f, Bone.SR_THIGH to 68f, Bone.SR_SHIN to 68f,
            )),
        ),
        PoseDef(
            "sidebend", "Side bend", Tier.MEDIUM, "🌙", "Lean to one side, top arm reaching over your head",
            m(LEGS, a(Bone.TORSO to -110f, Bone.SL_UPPER_ARM to 105f, Bone.SL_FOREARM to 100f, Bone.SR_UPPER_ARM to -125f, Bone.SR_FOREARM to -150f)),
            mirrorOk = true,
        ),
        PoseDef(
            "tree", "Tree pose", Tier.MEDIUM, "🌳", "Foot on the other leg, palms together above your head",
            m(UPRIGHT, a(
                Bone.SL_UPPER_ARM to -115f, Bone.SL_FOREARM to -50f, Bone.SR_UPPER_ARM to -65f, Bone.SR_FOREARM to -130f,
                Bone.SL_THIGH to 92f, Bone.SL_SHIN to 90f, Bone.SR_THIGH to 35f, Bone.SR_SHIN to 165f,
            )),
            constraints = listOf(Constraint.Near(Joint.SL_WRIST, Joint.SR_WRIST, 0.4f, "Palms together overhead")),
            mirrorOk = true,
        ),
        PoseDef(
            "flamingo", "Flamingo", Tier.MEDIUM, "🦩", "Arms in a T, lift one knee up high",
            m(UPRIGHT, ARMS_T, a(Bone.SL_THIGH to 92f, Bone.SL_SHIN to 90f, Bone.SR_THIGH to 80f, Bone.SR_SHIN to 95f)),
            constraints = listOf(Constraint.LegBent(false, 1.0f, "Lift your knee higher")),
            mirrorOk = true, legScaleSR = 0.55f,
        ),
        PoseDef(
            "goddess", "Goddess", Tier.MEDIUM, "🔱", "Wide squat, knees out, goal-post arms",
            m(UPRIGHT, GOAL_POST, a(Bone.SL_THIGH to 150f, Bone.SL_SHIN to 95f, Bone.SR_THIGH to 30f, Bone.SR_SHIN to 85f)),
        ),
        PoseDef(
            "squatT", "Squat T", Tier.MEDIUM, "🏋️", "Squat down low with arms straight out",
            m(UPRIGHT, ARMS_T, a(Bone.SL_THIGH to 100f, Bone.SL_SHIN to 80f, Bone.SR_THIGH to 80f, Bone.SR_SHIN to 100f)),
            constraints = listOf(
                Constraint.LegBent(true, 1.1f, "Squat lower"),
                Constraint.LegBent(false, 1.1f, "Squat lower"),
            ),
            legScaleSL = 0.6f, legScaleSR = 0.6f,
        ),

        // ------------------------------------------------------------------ HARD
        PoseDef(
            "deepsquat", "Deep squat", Tier.HARD, "🐸", "Sink into a deep squat, hands on your knees",
            m(UPRIGHT, a(
                Bone.SL_UPPER_ARM to 100f, Bone.SL_FOREARM to 95f, Bone.SR_UPPER_ARM to 80f, Bone.SR_FOREARM to 85f,
                Bone.SL_THIGH to 100f, Bone.SL_SHIN to 80f, Bone.SR_THIGH to 80f, Bone.SR_SHIN to 100f,
            )),
            constraints = listOf(
                Constraint.LegBent(true, 0.95f, "Go deeper"),
                Constraint.LegBent(false, 0.95f, "Go deeper"),
                Constraint.Near(Joint.SL_WRIST, Joint.SL_KNEE, 0.45f, "Hands on your knees"),
                Constraint.Near(Joint.SR_WRIST, Joint.SR_KNEE, 0.45f, "Hands on your knees"),
            ),
            legScaleSL = 0.5f, legScaleSR = 0.5f,
        ),
        PoseDef(
            "triangle", "Triangle", Tier.HARD, "📐", "Wide legs, tilt sideways: one hand down, one hand up",
            a(
                Bone.TORSO to -165f,
                Bone.SL_UPPER_ARM to 95f, Bone.SL_FOREARM to 95f, Bone.SR_UPPER_ARM to -85f, Bone.SR_FOREARM to -85f,
                Bone.SL_THIGH to 120f, Bone.SL_SHIN to 120f, Bone.SR_THIGH to 60f, Bone.SR_SHIN to 60f,
            ),
            mirrorOk = true,
        ),
        PoseDef(
            "legraise", "Side leg raise", Tier.HARD, "🦵", "Arms in a T, lift one straight leg out to the side",
            m(ARMS_T, a(Bone.TORSO to -95f, Bone.SL_THIGH to 92f, Bone.SL_SHIN to 90f, Bone.SR_THIGH to 30f, Bone.SR_SHIN to 30f)),
            mirrorOk = true,
        ),
        PoseDef(
            "lunge", "Low lunge", Tier.HARD, "🏃", "Side-on: front knee bent, back knee low, arms up",
            m(UPRIGHT, a(
                Bone.SL_UPPER_ARM to -95f, Bone.SL_FOREARM to -95f, Bone.SR_UPPER_ARM to -95f, Bone.SR_FOREARM to -95f,
                Bone.SL_THIGH to 175f, Bone.SL_SHIN to 90f, Bone.SR_THIGH to 60f, Bone.SR_SHIN to 15f,
            )),
            mirrorOk = true, sideView = true,
        ),
        PoseDef(
            "plank", "Plank", Tier.HARD, "🪵", "Side-on to the camera: straight line from head to heels",
            a(
                Bone.TORSO to L,
                Bone.SL_UPPER_ARM to D, Bone.SL_FOREARM to D, Bone.SR_UPPER_ARM to 92f, Bone.SR_FOREARM to 92f,
                Bone.SL_THIGH to 2f, Bone.SL_SHIN to 2f, Bone.SR_THIGH to R, Bone.SR_SHIN to R,
            ),
            mirrorOk = true, sideView = true,
        ),
        PoseDef(
            "sideangle", "Extended side angle", Tier.HARD, "📏", "Deep lunge sideways, top arm reaching over your ear",
            a(
                Bone.TORSO to -160f,
                Bone.SL_UPPER_ARM to 100f, Bone.SL_FOREARM to 100f, Bone.SR_UPPER_ARM to -160f, Bone.SR_FOREARM to -160f,
                Bone.SL_THIGH to 150f, Bone.SL_SHIN to 90f, Bone.SR_THIGH to 50f, Bone.SR_SHIN to 50f,
            ),
            mirrorOk = true,
        ),
        PoseDef(
            "boat", "Boat", Tier.HARD, "⛵", "Side-on, sit and balance: lean back, legs up, arms forward",
            a(
                Bone.TORSO to -115f,
                Bone.SL_UPPER_ARM to R, Bone.SL_FOREARM to R, Bone.SR_UPPER_ARM to R, Bone.SR_FOREARM to R,
                Bone.SL_THIGH to -40f, Bone.SL_SHIN to -40f, Bone.SR_THIGH to -42f, Bone.SR_SHIN to -42f,
            ),
            mirrorOk = true, sideView = true,
        ),

        // ------------------------------------------------------------------ VERY HARD
        PoseDef(
            "halfmoon", "Half moon", Tier.VERY_HARD, "🌗", "Balance on one leg, body and other leg level, arms in a vertical line",
            a(
                Bone.TORSO to L,
                Bone.SL_UPPER_ARM to D, Bone.SL_FOREARM to D, Bone.SR_UPPER_ARM to U, Bone.SR_FOREARM to U,
                Bone.SL_THIGH to D, Bone.SL_SHIN to D, Bone.SR_THIGH to R, Bone.SR_SHIN to R,
            ),
            mirrorOk = true,
        ),
        PoseDef(
            "warrior3", "Warrior III", Tier.VERY_HARD, "🦅", "Side-on: body and back leg level, arms forward",
            a(
                Bone.TORSO to L,
                Bone.SL_UPPER_ARM to L, Bone.SL_FOREARM to L, Bone.SR_UPPER_ARM to L, Bone.SR_FOREARM to L,
                Bone.SL_THIGH to D, Bone.SL_SHIN to D, Bone.SR_THIGH to R, Bone.SR_SHIN to R,
            ),
            mirrorOk = true, sideView = true,
        ),
        PoseDef(
            "sideplank", "Side plank", Tier.VERY_HARD, "📈", "On one hand, body in a straight diagonal, top arm up",
            a(
                Bone.TORSO to -20f,
                Bone.SL_UPPER_ARM to U, Bone.SL_FOREARM to U, Bone.SR_UPPER_ARM to D, Bone.SR_FOREARM to D,
                Bone.SL_THIGH to 160f, Bone.SL_SHIN to 160f, Bone.SR_THIGH to 162f, Bone.SR_SHIN to 162f,
            ),
            mirrorOk = true,
        ),
        PoseDef(
            "split", "Standing split", Tier.VERY_HARD, "🩰", "Side-on: fold forward, hands down, one leg high behind",
            a(
                Bone.TORSO to 110f,
                Bone.SL_UPPER_ARM to D, Bone.SL_FOREARM to D, Bone.SR_UPPER_ARM to D, Bone.SR_FOREARM to D,
                Bone.SL_THIGH to D, Bone.SL_SHIN to D, Bone.SR_THIGH to -70f, Bone.SR_SHIN to -70f,
            ),
            mirrorOk = true, sideView = true,
        ),
        PoseDef(
            "dancer", "Dancer", Tier.VERY_HARD, "💃", "Side-on: reach forward, hold your back foot up behind you",
            a(
                Bone.TORSO to -125f,
                Bone.SL_UPPER_ARM to -150f, Bone.SL_FOREARM to -150f, Bone.SR_UPPER_ARM to R, Bone.SR_FOREARM to R,
                Bone.SL_THIGH to D, Bone.SL_SHIN to D, Bone.SR_THIGH to -10f, Bone.SR_SHIN to -110f,
            ),
            constraints = listOf(Constraint.Near(Joint.SR_WRIST, Joint.SR_ANKLE, 0.45f, "Hold your back foot")),
            mirrorOk = true, sideView = true,
        ),

        // ------------------------------------------------------------------ EXTREME
        PoseDef(
            "handstand", "Handstand", Tier.EXTREME, "🤸", "Upside down on your hands, legs straight up",
            a(
                Bone.TORSO to D,
                Bone.SL_UPPER_ARM to D, Bone.SL_FOREARM to D, Bone.SR_UPPER_ARM to D, Bone.SR_FOREARM to D,
                Bone.SL_THIGH to -95f, Bone.SL_SHIN to -95f, Bone.SR_THIGH to -85f, Bone.SR_SHIN to -85f,
            ),
        ),
        PoseDef(
            "headstand", "Headstand", Tier.EXTREME, "🙃", "Head and forearms on the floor, legs straight up",
            a(Bone.TORSO to D, Bone.SL_THIGH to -95f, Bone.SL_SHIN to -95f, Bone.SR_THIGH to -85f, Bone.SR_SHIN to -85f),
        ),
        PoseDef(
            "hsplit", "Handstand split", Tier.EXTREME, "🧜", "Handstand with legs split wide",
            a(
                Bone.TORSO to D,
                Bone.SL_UPPER_ARM to D, Bone.SL_FOREARM to D, Bone.SR_UPPER_ARM to D, Bone.SR_FOREARM to D,
                Bone.SL_THIGH to -135f, Bone.SL_SHIN to -135f, Bone.SR_THIGH to -45f, Bone.SR_SHIN to -45f,
            ),
        ),
        PoseDef(
            "sidestar", "Side plank star", Tier.EXTREME, "🌟", "Side plank, then lift your top leg and arm high",
            a(
                Bone.TORSO to -20f,
                Bone.SL_UPPER_ARM to U, Bone.SL_FOREARM to U, Bone.SR_UPPER_ARM to D, Bone.SR_FOREARM to D,
                Bone.SL_THIGH to -150f, Bone.SL_SHIN to -150f, Bone.SR_THIGH to 162f, Bone.SR_SHIN to 162f,
            ),
            mirrorOk = true,
        ),
    )

    fun tier(t: Tier) = ALL.filter { it.tier == t }
    fun byId(id: String) = ALL.firstOrNull { it.id == id }
}
