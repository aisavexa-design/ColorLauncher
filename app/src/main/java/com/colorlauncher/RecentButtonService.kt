package com.colorlauncher

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout

class RecentButtonService : AccessibilityService() {

    private var wm: WindowManager? = null
    private var overlay: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var prefs: SharedPreferences? = null

    /** true = Settings ke andar (dikhega, drag ho sakta hai). false = locked, almost transparent. */
    private var editMode = false

    companion object {
        const val PREFS = "recent_btn"
        const val KEY_X = "x"
        const val KEY_Y = "y"
        const val KEY_ENABLED = "enabled"
        const val TAG = "CL-Recent"
        /** Recent icon ke area se thoda bada, taaki tap pakka overlay ko mile. */
        const val SIZE_DP = 56
        /** 0 nahi, taaki touch consume ho par dikhe nahi. */
        const val IDLE_ALPHA = 0.01f

        @Volatile var instance: RecentButtonService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            instance = this
            prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            wm = getSystemService(WINDOW_SERVICE) as WindowManager
            if (prefs?.getBoolean(KEY_ENABLED, true) == true) showOverlay(editMode = false)
        } catch (e: Exception) {
            Log.e(TAG, "connect", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        removeOverlay()
        instance = null
        return super.onUnbind(intent)
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

    /** Overlay dikhata hai ya update karta hai. */
    fun showOverlay(editMode: Boolean) {
        try {
            this.editMode = editMode
            if (overlay != null) {
                applyMode()
                return
            }
            val size = dpToPx(SIZE_DP)
            val p = prefs ?: return
            val lp = WindowManager.LayoutParams(
                size, size,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = p.getInt(KEY_X, resources.displayMetrics.widthPixels / 2 - size / 2)
                y = p.getInt(KEY_Y, resources.displayMetrics.heightPixels - dpToPx(56))
            }
            val v = FrameLayout(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#3322CC66"))
                }
                setOnTouchListener { _, e -> onTouch(e) }
            }
            wm?.addView(v, lp)
            overlay = v
            params = lp
            applyMode()
        } catch (e: Exception) {
            Log.e(TAG, "showOverlay", e)
        }
    }

    /** Edit mode: dikhe, drag ho. Locked mode: almost transparent, touch lock. */
    private fun applyMode() {
        val v = overlay ?: return
        val lp = params ?: return
        try {
            v.alpha = if (editMode) 1f else IDLE_ALPHA
            wm?.updateViewLayout(v, lp)
        } catch (e: Exception) {
            Log.e(TAG, "applyMode", e)
        }
    }

    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0
    private var moved = false

    private fun onTouch(e: MotionEvent): Boolean {
        val lp = params ?: return true
        try {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = e.rawX
                    downRawY = e.rawY
                    startX = lp.x
                    startY = lp.y
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!editMode) return true
                    val dx = e.rawX - downRawX
                    val dy = e.rawY - downRawY
                    if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) moved = true
                    if (moved) {
                        lp.x = startX + dx.toInt()
                        lp.y = startY + dy.toInt()
                        wm?.updateViewLayout(overlay, lp)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (editMode) {
                        if (moved) savePosition(lp.x, lp.y)
                    } else {
                        performGlobalAction(GLOBAL_ACTION_RECENTS)
                    }
                    moved = false
                }
            }
        } catch (ex: Exception) {
            Log.e(TAG, "touch", ex)
        }
        return true
    }

    /** Settings se X/Y set karne ke liye. */
    fun savePosition(x: Int, y: Int) {
        try {
            prefs?.edit()?.putInt(KEY_X, x)?.putInt(KEY_Y, y)?.apply()
            val lp = params ?: return
            lp.x = x
            lp.y = y
            wm?.updateViewLayout(overlay, lp)
        } catch (e: Exception) {
            Log.e(TAG, "savePosition", e)
        }
    }

    fun enterEditMode() = showOverlay(editMode = true)

    fun exitEditMode() = showOverlay(editMode = false)

    fun removeOverlay() {
        try {
            overlay?.let { wm?.removeView(it) }
        } catch (e: Exception) {
            Log.e(TAG, "removeOverlay", e)
        }
        overlay = null
        params = null
    }

    fun setEnabled(on: Boolean) {
        prefs?.edit()?.putBoolean(KEY_ENABLED, on)?.apply()
        if (on) showOverlay(editMode = false) else removeOverlay()
    }
}
