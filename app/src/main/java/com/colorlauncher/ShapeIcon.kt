package com.colorlauncher

import android.content.Context
import android.view.View
import android.view.ViewOutlineProvider
import android.graphics.Outline
import android.widget.ImageView

/** Icon shape GPU outline se (0 squircle, 1 circle, 2 square). */
class ShapeIcon(ctx: Context) : ImageView(ctx) {
    var shape = 0
        set(v) {
            if (field == v) return
            field = v
            invalidateOutline()
        }

    init {
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val w = view.width
                val h = view.height
                if (w <= 0 || h <= 0) return
                when (shape) {
                    1 -> outline.setOval(0, 0, w, h)
                    2 -> outline.setRect(0, 0, w, h)
                    else -> outline.setRoundRect(0, 0, w, h, w * 0.3f)
                }
            }
        }
    }
}
