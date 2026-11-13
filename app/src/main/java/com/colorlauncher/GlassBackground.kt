package com.colorlauncher

import android.content.Context
import android.graphics.drawable.GradientDrawable

/**
 * Liquid glass ka lightweight look: semi-transparent tint + light border + top highlight.
 * Bitmap ya live blur nahi, isliye 4GB RAM par cheap hai.
 * radiusDp: corner radius (dp). Phone screen jaisa gol karne ke liye screen corner radius do.
 */
object GlassBackground {

    /** Screen corner radius (dp). Most modern phones ~ 36-48dp. Ise apne phone ke hisaab se badal sakte ho. */
    const val SCREEN_CORNER_DP = 40f

    fun make(ctx: Context, radiusDp: Float, dark: Boolean = true): GradientDrawable {
        val d = ctx.resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusDp * d
            colors = if (dark) {
                intArrayOf(0x40FFFFFF.toInt(), 0x18FFFFFF.toInt())
            } else {
                intArrayOf(0x66FFFFFF.toInt(), 0x33FFFFFF.toInt())
            }
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
            setStroke((1 * d).toInt().coerceAtLeast(1), 0x59FFFFFF.toInt())
        }
    }

    /** Drawer ke liye: sirf top corners screen jaise gol, bottom corners bhi. */
    fun drawerScreen(ctx: Context): GradientDrawable =
        make(ctx, SCREEN_CORNER_DP)
}
