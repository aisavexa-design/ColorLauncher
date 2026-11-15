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
 * Notch close: rectangle linearly notch ke rect par collapse hota hai (center fix),
 * corner 56dp se pill tak, aur saath mein fade out.
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

    fun prepare(l: Float, tp: Float, r: Float, b: Float, c: Int, bmp: Bitmap) {
        from.set(l, tp, r, b)
        color = c
        icon = bmp
        notchMode = false
        t = 0f
    }

    /** Notch ka rect: top center, camera cutout jaisa chhota pill. */
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
        if (notchMode) drawNotch(canvas, tt) else drawIcon(canvas, tt)
    }

    /** Icon origin par launch/close, corner 56dp -> 36dp. */
    private fun drawIcon(canvas: Canvas, tt: Float) {
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
        iconPaint.alpha = 255
        val half = from.width() / 2f
        dst.set(cur.centerX() - half, cur.centerY() - half, cur.centerX() + half, cur.centerY() + half)
        canvas.drawBitmap(bmp, null, dst, iconPaint)
    }

    /** Notch par close. t=1 full screen, t=0 notch rect. Har edge notch ke edge tak jaati hai. */
    private fun drawNotch(canvas: Canvas, tt: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        val shrink = 1f - tt                       // 0 = full, 1 = notch
        val k = shrink * shrink * (1.2f - 0.2f * shrink) // ease-in: pehle dheere, end mein tez

        val l = 0f + (from.left - 0f) * k
        val tp = 0f + (from.top - 0f) * k
        val r = w + (from.right - w) * k
        val b = h + (from.bottom - h) * k
        cur.set(l, tp, r, b)

        val notchR = from.height() / 2f
        val rad = startCorner + (notchR - startCorner) * k
        val alpha = (255f * (1f - k).coerceIn(0f, 1f)).toInt().coerceIn(0, 255)

        sheet.color = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
        canvas.drawRoundRect(cur, rad, rad, sheet)

        val bmp = icon ?: return
        if (k > 0.9f) return
        iconPaint.alpha = alpha
        val half = minOf(cur.width(), cur.height()) * 0.4f
        dst.set(cur.centerX() - half, cur.centerY() - half, cur.centerX() + half, cur.centerY() + half)
        canvas.drawBitmap(bmp, null, dst, iconPaint)
    }
}
