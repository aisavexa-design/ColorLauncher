package com.colorlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

class LaunchOverlay(ctx: Context) : View(ctx) {
    private val sheet = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val from = RectF()
    private val cur = RectF()
    private val dst = RectF()
    private var color = Color.DKGRAY
    private var icon: Bitmap? = null
    private var iconCorner = 0f
    private val screenCorner = 56f * resources.displayMetrics.density
    var t = 0f
        set(v) {
            field = v
            invalidate()
        }

    fun prepare(l: Float, tp: Float, r: Float, b: Float, c: Int, bmp: Bitmap) {
        from.set(l, tp, r, b)
        color = c
        icon = bmp
        iconCorner = (r - l) * 0.28f
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
        val rad = iconCorner + (screenCorner - iconCorner) * tt
        canvas.drawRoundRect(cur, rad, rad, sheet)
        val bmp = icon ?: return
        val half = from.width() / 2f
        dst.set(cur.centerX() - half, cur.centerY() - half, cur.centerX() + half, cur.centerY() + half)
        canvas.drawBitmap(bmp, null, dst, iconPaint)
    }
}
