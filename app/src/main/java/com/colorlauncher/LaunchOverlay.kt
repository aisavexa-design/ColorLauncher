package com.colorlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/**
 * Launch aur close dono ke liye overlay.
 * - Launch: origin icon rect se full screen tak expand.
 * - Close (reverse): full screen se origin icon rect tak shrink.
 * - Origin nahi ho to notch (top center) se expand/shrink.
 * t = 0 (icon / origin) .. 1 (full screen).
 */
class LaunchOverlay(ctx: Context) : View(ctx) {
    private val sheet = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val from = RectF()
    private val cur = RectF()
    private val dst = RectF()
    private var color = Color.DKGRAY
    private var icon: Bitmap? = null
    private var iconCorner = 0f
    private var hasOrigin = true

    private val startCorner = 56f * resources.displayMetrics.density
    private val endCorner = 36f * resources.displayMetrics.density

    var t = 0f
        set(v) {
            field = v
            invalidate()
        }

    /** Origin ko icon rect se set karo. */
    fun prepare(l: Float, tp: Float, r: Float, b: Float, c: Int, bmp: Bitmap) {
        from.set(l, tp, r, b)
        color = c
        icon = bmp
        iconCorner = (r - l) * 0.28f
        hasOrigin = true
        t = 0f
    }

    /** Origin nahi (notch se animate). */
    fun prepareNotch(c: Int, bmp: Bitmap?) {
        val d = resources.displayMetrics.density
        val w = width.toFloat().takeIf { it > 0f } ?: resources.displayMetrics.widthPixels.toFloat()
        val cx = w / 2f
        val s = 40f * d
        from.set(cx - s / 2f, 0f, cx + s / 2f, s)
        color = c
        icon = bmp
        iconCorner = s / 2f
        hasOrigin = false
        t = 0f
    }

    override fun onDraw(canvas: Canvas) {
        val tt = t.coerceIn(0f, 1f)
        val w = width.toFloat()
        val h = height.toFloat()
        cur.set(
            from.left * (1f - tt),
            from.top * (1f - tt),
            from.right + (w - from.right) * tt,
            from.bottom + (h - from.bottom) * tt
        )
        sheet.color = color
        val rad = startCorner + (endCorner - startCorner) * tt
        canvas.drawRoundRect(cur, rad, rad, sheet)
        val bmp = icon ?: return
        if (!hasOrigin && tt > 0.85f) return
        val half = from.width() / 2f
        dst.set(cur.centerX() - half, cur.centerY() - half, cur.centerX() + half, cur.centerY() + half)
        canvas.drawBitmap(bmp, null, dst, iconPaint)
    }
}
