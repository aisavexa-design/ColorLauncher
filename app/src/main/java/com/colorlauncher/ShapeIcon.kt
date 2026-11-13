package com.colorlauncher

import android.content.Context
import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView

/**
 * Icon shape.
 * 0 = Default: clip nahi, icon apni original shape mein dikhega (pack/adaptive shape).
 * 1 = Circle, 2 = Square: sirf in cases mein clip hoga.
 */
class ShapeIcon(ctx: Context) : ImageView(ctx) {
    var shape = 0
        set(v) {
            if (field == v) return
            field = v
            applyClip()
        }

    init {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val w = view.width
                val h = view.height
                if (w <= 0 || h <= 0) return
                when (shape) {
                    1 -> outline.setOval(0, 0, w, h)
                    2 -> outline.setRect(0, 0, w, h)
                    else -> outline.setEmpty()
                }
            }
        }
        applyClip()
    }

    private fun applyClip() {
        clipToOutline = shape != 0
        invalidateOutline()
    }
}
