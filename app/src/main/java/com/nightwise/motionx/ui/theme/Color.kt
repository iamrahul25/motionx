package com.nightwise.motionx.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor

/**
 * MotionX palette, modelled on a sports watch face: true black, bold white type
 * and a handful of vivid system-style accents used only for data and actions.
 */
object MX {
    // surfaces
    val Night = Color(0xFF000000)
    val Night2 = Color(0xFF0B0B0C)
    val Surface = Color(0xFF161618)
    val SurfaceHi = Color(0xFF232326)
    val Glass = Color(0xFF161618)
    val GlassDark = Color(0xF0161618)
    val Stroke = Color(0xFF2E2E32)
    val StrokeSoft = Color(0xFF222225)

    // accents (names kept for compatibility, values are the new system colours)
    val Cyan = Color(0xFF3DB8E8)    // watch "100%" blue
    val Magenta = Color(0xFFFF3B5C) // heart pink-red
    val Violet = Color(0xFF7B7BFF)  // sleep indigo
    val Lime = Color(0xFF34D35C)    // activity green
    val Amber = Color(0xFFFF8A3D)   // heart-rate orange
    val Coral = Color(0xFFFF453A)   // alert red
    val Sky = Color(0xFF64D2FF)
    val Yellow = Color(0xFFFFD60A)

    val TextHi = Color(0xFFFFFFFF)
    val TextMid = Color(0xFFA1A1A6)
    val TextLow = Color(0xFF6C6C70)

    val Hurdle = Amber
    val Bar = Sky
    val Wall = Coral

    // Formerly gradients; now solid so nothing in the UI renders a gradient.
    val PrimaryGradient: Brush = SolidColor(Cyan)
    val HotGradient: Brush = SolidColor(Magenta)
    val LimeGradient: Brush = SolidColor(Lime)
}
