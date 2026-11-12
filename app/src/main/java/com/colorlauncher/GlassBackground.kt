package com.colorlauncher

import android.graphics.drawable.GradientDrawable
import android.content.Context

/**
 * Liquid glass ka lightweight look: semi-transparent tint + light border + top highlight.
 * Bitmap ya live blur nahi, isliye 4GB RAM par cheap hai.
 */
object GlassBackground {

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
}
