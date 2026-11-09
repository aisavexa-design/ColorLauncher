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
import kotlin.math.abs

class RecentButtonService : AccessibilityService() {

    private var wm: WindowManager? = null
    private var overlay: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var prefs: SharedPreferences? = null

    /** true = Settings se set karne ka mode (dikhega, drag ho sakta hai). false = normal mode (100% transparent, locked). */
    private var editMode = false

    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0
    private var moved = false

    companion object {
        const val PREFS = "recent_btn"
        const val KEY_X = "x"
        const val KEY_Y = "y"
        const val KEY_ENABLED = "enabled"
        const val TAG = "CL-Recent"
        const val SIZE_DP = 48

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

    /**
     * Overlay dikhata hai.
     * editMode = true: visible (alpha 1), drag karne do.
     * editMode = false: alpha 0 (100% transparent), touch lock, sirf tap par Recents.
     */
    fun showOverlay(editMode: Boolean) {
        try {
            this.editMode = editMode
            val existing = overlay
            if (existing != null) {
                applyMode()
                return
            }
            val size = dpToPx(SIZE_DP)
            val p = prefs ?: return
            val lp = WindowManager.LayoutParams(
                size, size,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = p.getInt(KEY_X, resources.displayMetrics.widthPixels - size - dpToPx(12))
                y = p.getInt(KEY_Y, resources.displayMetrics.heightPixels - dpToPx(160))
            }
            val v = FrameLayout(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#2222CC66"))
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

    /** Mode ke hisaab se alpha aur lock. */
    private fun applyMode() {
        val v = overlay ?: return
        val lp = params ?: return
        try {
            if (editMode) {
                v.alpha = 1f
                v.background?.alpha = 255
                lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            } else {
                // 100% transparent: dikhega nahi, par touch lega (tap = Recents)
                v.alpha = 0f
                lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            }
            wm?.updateViewLayout(v, lp)
        } catch (e: Exception) {
            Log.e(TAG, "applyMode", e)
        }
    }

    private fun onTouch(e: MotionEvent): Boolean {
        val lp = params ?: return false
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
                    // Sirf edit mode mein drag allowed hai
                    if (!editMode) return true
                    val dx = e.rawX - downRawX
                    val dy = e.rawY - downRawY
                    if (abs(dx) > 8 || abs(dy) > 8) moved = true
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
                        // Normal mode: tap par seedha Recents
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

    /** Settings se X/Y set karne ke liye. Position save hoti hai, overlay wahin rehta hai. */
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

    /** Settings screen khulne par call karo: button dikhe aur drag ho. */
    fun enterEditMode() {
        showOverlay(editMode = true)
    }

    /** Settings se bahar aane par call karo: button 100% transparent aur locked. */
    fun exitEditMode() {
        showOverlay(editMode = false)
    }

    fun removeOverlay() {
        try {
            overlay?.let { wm?.removeView(it) }
        } catch (e: Exception) {
            Log.e(TAG, "removeOverlay", e)
        }
        overlay = null
        params = null
    }

    /** Settings se on/off. */
    fun setEnabled(on: Boolean) {
        prefs?.edit()?.putBoolean(KEY_ENABLED, on)?.apply()
        if (on) showOverlay(editMode = false) else removeOverlay()
    }
}
