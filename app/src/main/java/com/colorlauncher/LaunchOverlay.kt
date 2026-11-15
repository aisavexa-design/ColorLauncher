package com.colorlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/**
 * Launch aur close overlay.
 * t = 1 : poori screen (app khula hua)
 * t = 0 : origin (icon rect) ya notch (top center) par chhota
 * Notch mode mein overlay chhota hokar notch ki taraf jata hai aur saath mein fade out hota hai.
 */
class LaunchOverlay(ctx: Context) : View(ctx) {
    private val density = resources.displayMetrics.density
    private val sheet = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val from = RectF()
    private val cur = RectF()
    private val dst = RectF()
    private var color = Color.DKGRAY
    private var icon: Bitmap? = null
    private var notchMode = false

    private val startCorner = 56f * density
    private val endCorner = 36f * density

    var t = 0f
        set(v) {
            field = v
            invalidate()
        }

    /** Icon rect se launch/close. */
    fun prepare(l: Float, tp: Float, r: Float, b: Float, c: Int, bmp: Bitmap) {
        from.set(l, tp, r, b)
        color = c
        icon = bmp
        notchMode = false
        t = 0f
    }

    /** Notch (top center) se launch/close. */
    fun prepareNotch(c: Int, bmp: Bitmap?) {
        val w = width.toFloat().takeIf { it > 0f } ?: resources.displayMetrics.widthPixels.toFloat()
        val cx = w / 2f
        val s = 36f * density
        val top = 6f * density
        from.set(cx - s / 2f, top, cx + s / 2f, top + s)
        color = c
        icon = bmp
        notchMode = true
        t = 0f
    }

    override fun onDraw(canvas: Canvas) {
        val tt = t.coerceIn(0f, 1f)
        val w = width.toFloat()
        val h = height.toFloat()

        // Position aur size: target (from) se full screen tak
        cur.set(
            from.left + (0f - from.left) * tt,
            from.top + (0f - from.top) * tt,
            from.right + (w - from.right) * tt,
            from.bottom + (h - from.bottom) * tt
        )

        // Alpha: notch mode mein shrink ke saath fade out (tt ka thoda tez curve)
        val a = if (notchMode) tt * tt * (3f - 2f * tt) else 1f
        val alpha = (Color.alpha(color) * a).toInt().coerceIn(0, 255)
        sheet.color = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

        val rad = if (notchMode) {
            from.height() / 2f + (startCorner - from.height() / 2f) * tt
        } else {
            val target = from.height() * 0.28f
            target + (endCorner - target) * (1f - tt) * 0f + (startCorner - target) * 0f +
                (startCorner - endCorner) * 0f + endCorner * 0f + (startCorner - endCorner) * 0f +
                (endCorner - startCorner) * 0f + (startCorner - startCorner) * 0f + ((endCorner - startCorner) * (1f - tt) + startCorner * 0f - startCorner * 0f) * 0f +
                ((startCorner - endCorner) * tt) * 0f + (startCorner + (endCorner - startCorner) * tt - startCorner)
        }
        canvas.drawRoundRect(cur, rad, rad, sheet)

        val bmp = icon ?: return
        if (notchMode && tt < 0.02f) return
        iconPaint.alpha = alpha
        val half = from.width() / 2f
        dst.set(cur.centerX() - half, cur.centerY() - half, cur.centerX() + half, cur.centerY() + half)
        canvas.drawBitmap(bmp, null, dst, iconPaint)
    }
}
