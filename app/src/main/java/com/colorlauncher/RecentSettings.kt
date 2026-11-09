package com.colorlauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Settings screen mein "Recent button" ka section. Activity ki file mein sirf addTo() call karna hai. */
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
            text = "Recent button dikhao"
            setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean(RecentButtonService.KEY_ENABLED, true)
            setOnCheckedChangeListener { _, on ->
                prefs.edit().putBoolean(RecentButtonService.KEY_ENABLED, on).apply()
                val svc = RecentButtonService.instance
                if (svc != null) svc.setEnabled(on)
                else Toast.makeText(activity, "Pehle Accessibility permission on karo", Toast.LENGTH_SHORT).show()
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
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
            setText(prefs.getInt(RecentButtonService.KEY_X, activity.resources.displayMetrics.widthPixels - dp(56)).toString())
            setTextColor(Color.WHITE)
            setHintTextColor(0x99FFFFFF.toInt())
        }

        val yInput = EditText(activity).apply {
            hint = "Y (px)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
            setText(prefs.getInt(RecentButtonService.KEY_Y, activity.resources.displayMetrics.heightPixels - dp(200)).toString())
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
                } else {
                    prefs.edit().putInt(RecentButtonService.KEY_X, x).putInt(RecentButtonService.KEY_Y, y).apply()
                }
                Toast.makeText(activity, "Position save ho gayi", Toast.LENGTH_SHORT).show()
            }
        }

        val previewBtn = Button(activity).apply {
            text = "Preview dikhao"
            setOnClickListener {
                val svc = RecentButtonService.instance
                if (svc == null) {
                    Toast.makeText(activity, "Pehle Accessibility permission on karo", Toast.LENGTH_SHORT).show()
                } else {
                    svc.setEnabled(true)
                    svc.showButton()
                }
            }
        }

        val hint = TextView(activity).apply {
            text = "X/Y screen ke top-left se pixels mein hain. Button ko drag karke bhi position badal sakte ho."
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
        container.addView(previewBtn)
        container.addView(hint)
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
