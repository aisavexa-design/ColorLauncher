package com.colorlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Launch aur close overlay.
 * t = 1 : poori screen (app khula hua)
 * t = 0 : origin (icon rect) ya notch (top center) par chhota
 *
 * Notch close: 3 phase
 *  1. (0 - 0.25) : thoda sa "pull" (overlay slightly shrink, poori opacity)
 *  2. (0.25 - 0.85) : tez shrink ke saath notch ki taraf, corner radius pill ban jata hai
 *  3. (0.85 - 1) : notch ke andar glow fade + chhota dot
 * Har phase ek hi draw pass mein hota hai, koi blur ya bitmap copy nahi.
 */
class LaunchOverlay(ctx: Context) : View(ctx) {
    private val density = resources.displayMetrics.density
    private val sheet = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
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

    /** Notch par multi-phase close. t: 1 = full screen, 0 = notch point. */
    private fun drawNotch(canvas: Canvas, tt: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        // Phase 1: thoda pull (0..0.25 -> screen ka 0.92 tak)
        val pull = if (tt > 0.75f) 1f else 1f - 0.08f * ((0.75f - tt) / 0.75f).pow(2f)
        // Main shrink: 0.75..0 -> full se notch tak, ease-in
        val shrinkT = min(1f, max(0f, (tt - 0.0f) / 1f))
        val k = shrinkT.pow(1.6f)

        val fullW = w * pull
        val fullH = h * pull
        val left = (w - fullW) / 2f
        val top = (h - fullH) / 2f

        val cx = from.centerX()
        val cy = from.centerY()
        val l = left + (cx - left) * (1f - k)
        val tp = top + (cy - top) * (1f - k)
        val r = (left + fullW) + (cx + from.width() / 2f - (left + fullW)) * (1f - k)
        val b = (top + fullH) + (cy + from.height() / 2f - (top + fullH)) * (1f - k)
        cur.set(l, tp, r, b)

        // Corner: screen corner -> pill (notch height / 2)
        val pillR = from.height() / 2f
        val rad = pillR + (endCorner - pillR) * shrinkT * 0f + (startCorner - pillR) * shrinkT
        val radius = max(pillR, min(rad, cur.height() / 2f))

        // Fade: notch ke paas (k ~ 1) alpha 0; poori screen par 255
        val fade = (1f - k).coerceIn(0f, 1f)
        val alpha = (255f * fade.pow(0.6f)).toInt().coerceIn(0, 255)

        // Soft glow ring: notch ki taraf jaate hue halka halo
        if (tt in 0.02f..0.98f) {
            val glowA = (60f * (1f - abs(tt - 0.5f) * 2f)).toInt().coerceIn(0, 60)
            glowPaint.color = Color.argb(glowA, Color.red(color), Color.green(color), Color.blue(color))
            val g = 10f * density * (1f - k)
            canvas.drawRoundRect(
                cur.left - g, cur.top - g, cur.right + g, cur.bottom + g,
                radius + g, radius + g, glowPaint
            )
        }

        sheet.color = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
        canvas.drawRoundRect(cur, radius, radius, sheet)

        val bmp = icon ?: return
        if (k > 0.9f) return
        iconPaint.alpha = alpha
        val half = min(cur.width(), cur.height()) * 0.4f
        dst.set(cur.centerX() - half, cur.centerY() - half, cur.centerX() + half, cur.centerY() + half)
        canvas.drawBitmap(bmp, null, dst, iconPaint)
    }

    private fun abs(v: Float) = if (v < 0f) -v else v
}
