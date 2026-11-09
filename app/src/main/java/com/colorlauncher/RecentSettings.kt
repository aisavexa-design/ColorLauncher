package com.colorlauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Settings screen mein "Recent button" ka section. */
object RecentSettings {

    fun addTo(activity: Activity, container: LinearLayout) {
        val dp = { v: Int -> (v * activity.resources.displayMetrics.density).toInt() }
        val prefs = activity.getSharedPreferences(RecentButtonService.PREFS, Activity.MODE_PRIVATE)

        val title = TextView(activity).apply {
            text = "Recent button overlay"
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(16), 0, dp(8))
        }

        val status = TextView(activity).apply {
            textSize = 13f
            setTextColor(0xB3FFFFFF.toInt())
            setPadding(0, 0, 0, dp(8))
        }

        val toggle = Switch(activity).apply {
            text = "Recent button chalu rakho"
            setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean(RecentButtonService.KEY_ENABLED, true)
            setOnCheckedChangeListener { _, on ->
                val svc = RecentButtonService.instance
                if (svc != null) svc.setEnabled(on)
                else prefs.edit().putBoolean(RecentButtonService.KEY_ENABLED, on).apply()
                status.text = statusText(on)
            }
        }

        val accessBtn = Button(activity).apply {
            text = "Accessibility permission kholo"
            setOnClickListener {
                activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        val xInput = EditText(activity).apply {
            hint = "X (px)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            setText(prefs.getInt(RecentButtonService.KEY_X, activity.resources.displayMetrics.widthPixels / 2).toString())
            setTextColor(Color.WHITE)
            setHintTextColor(0x99FFFFFF.toInt())
        }

        val yInput = EditText(activity).apply {
            hint = "Y (px)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            setText(prefs.getInt(RecentButtonService.KEY_Y, activity.resources.displayMetrics.heightPixels - dp(56)).toString())
            setTextColor(Color.WHITE)
            setHintTextColor(0x99FFFFFF.toInt())
        }

        val xyRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(xInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(yInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        val applyBtn = Button(activity).apply {
            text = "Position set karo"
            setOnClickListener {
                val x = xInput.text.toString().toIntOrNull()
                val y = yInput.text.toString().toIntOrNull()
                if (x == null || y == null) {
                    Toast.makeText(activity, "X aur Y number likho", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val svc = RecentButtonService.instance
                if (svc != null) {
                    svc.savePosition(x, y)
                    Toast.makeText(activity, "Position save ho gayi", Toast.LENGTH_SHORT).show()
                } else {
                    prefs.edit().putInt(RecentButtonService.KEY_X, x).putInt(RecentButtonService.KEY_Y, y).apply()
                    Toast.makeText(activity, "Position save hogi, pehle Accessibility on karo", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val hint = TextView(activity).apply {
            text = "Settings kholne par button dikhega. Settings se bahar aate hi wo almost transparent ho jayega, aur recent icon par tap karne par Recents khulega."
            textSize = 12f
            setTextColor(0x99FFFFFF.toInt())
            setPadding(0, dp(6), 0, dp(4))
        }

        status.text = statusText(toggle.isChecked)

        container.addView(title)
        container.addView(status)
        container.addView(toggle)
        container.addView(accessBtn)
        container.addView(xyRow)
        container.addView(applyBtn)
        container.addView(hint)
    }

    /** SettingsActivity ke onResume mein call karo: edit mode on. */
    fun onEnter() {
        RecentButtonService.instance?.enterEditMode()
    }

    /** SettingsActivity ke onStop mein call karo: locked mode. */
    fun onExit() {
        RecentButtonService.instance?.exitEditMode()
    }

    private fun statusText(on: Boolean): String {
        val running = RecentButtonService.instance != null
        return when {
            !running -> "Status: Accessibility service band hai"
            on -> "Status: Chalu hai"
            else -> "Status: Button band hai"
        }
    }
}
