package com.colorlauncher

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView

class RecentButtonService : AccessibilityService() {

    private var wm: WindowManager? = null
    private var button: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private var prefs: SharedPreferences? = null

    companion object {
        const val PREFS = "recent_btn"
        const val KEY_X = "x"
        const val KEY_Y = "y"
        const val KEY_ENABLED = "enabled"
        const val TAG = "CL-Recent"
        const val SIZE_DP = 44

        @Volatile var instance: RecentButtonService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            instance = this
            prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            wm = getSystemService(WINDOW_SERVICE) as WindowManager
            if (prefs?.getBoolean(KEY_ENABLED, true) == true) showButton()
        } catch (e: Exception) {
            Log.e(TAG, "connect", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        removeButton()
        instance = null
        return super.onUnbind(intent)
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

    /** Button dikhata hai. Position prefs se, default right side par. */
    fun showButton() {
        try {
            if (button != null) return
            val size = dpToPx(SIZE_DP)
            val p = prefs ?: return
            val lp = WindowManager.LayoutParams(
                size, size,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = p.getInt(KEY_X, resources.displayMetrics.widthPixels - size - dpToPx(12))
                y = p.getInt(KEY_Y, resources.displayMetrics.heightPixels - dpToPx(160))
            }
            val tv = TextView(this).apply {
                text = "▢"
                setTextColor(Color.TRANSPARENT)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0x33FFFFFF)
                }
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT
                setOnClickListener { performGlobalAction(GLOBAL_ACTION_RECENTS) }
                setOnTouchListener { _, e -> onTouch(e) }
            }
            wm?.addView(tv, lp)
            button = tv
            params = lp
        } catch (e: Exception) {
            Log.e(TAG, "showButton", e)
        }
    }

    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0
    private var moved = false

    private fun onTouch(e: MotionEvent): Boolean {
        val lp = params ?: return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = e.rawX
                downRawY = e.rawY
                startX = lp.x
                startY = lp.y
                moved = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - downRawX
                val dy = e.rawY - downRawY
                if (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8) moved = true
                if (moved) {
                    lp.x = startX + dx.toInt()
                    lp.y = startY + dy.toInt()
                    try { wm?.updateViewLayout(button, lp) } catch (_: Exception) {}
                }
            }
            MotionEvent.ACTION_UP -> {
                if (moved) savePosition(lp.x, lp.y) else performGlobalAction(GLOBAL_ACTION_RECENTS)
            }
        }
        return true
    }

    /** Settings se position set karne ke liye. */
    fun savePosition(x: Int, y: Int) {
    prefs?.edit()?.putInt(KEY_X, x)?.putInt(KEY_Y, y)?.apply()
    val lp = params ?: return
    lp.x = x
    lp.y = y
    try { wm?.updateViewLayout(button, lp) } catch (e: Exception) { Log.e(TAG, "move", e) }
    }

    fun removeButton() {
        try {
            button?.let { wm?.removeView(it) }
        } catch (e: Exception) {
            Log.e(TAG, "removeButton", e)
        }
        button = null
        params = null
    }

    /** Settings se button on/off. */
    fun setEnabled(on: Boolean) {
        prefs?.edit()?.putBoolean(KEY_ENABLED, on)?.apply()
        if (on) showButton() else removeButton()
    }
}
