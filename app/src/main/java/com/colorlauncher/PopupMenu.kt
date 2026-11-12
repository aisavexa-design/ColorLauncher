package com.colorlauncher

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.Gravity
import android.view.Window
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView

private const val PM_TAG = "CL"

/**
 * Chhota rounded popup box. Bottom sheet nahi.
 * Icon ke upar ya neeche (jahan jagah ho) khulta hai, scale-in animation ke saath.
 * Ek time par sirf ek popup. Naya popup purana band kar deta hai.
 */
class PopupMenu(private val ctx: Context, private val popupMs: Long = 260L) {

    private var current: Dialog? = null

    fun dismiss() {
        try {
            current?.dismiss()
        } catch (e: Exception) {
            Log.e(PM_TAG, "popup dismiss", e)
        }
        current = null
    }

    /**
     * Popup dikhao.
     * @param rawX @param rawY screen par touch point (icon ka center ya long press point)
     * @param heading upar ka chhota title
     * @param actions label aur click action ki list
     */
    fun show(
        rawX: Float,
        rawY: Float,
        heading: String,
        actions: List<Pair<String, () -> Unit>>
    ) {
        try {
            dismiss()
            val d = Dialog(ctx)
            d.requestWindowFeature(Window.FEATURE_NO_TITLE)
            val density = ctx.resources.displayMetrics.density
            fun dp(v: Int) = (v * density).toInt()

            val boxW = dp(220)
            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(6), dp(10), dp(6), dp(8))
                background = GradientDrawable().apply {
                    cornerRadius = dp(20).toFloat()
                    setColor(0xF01E1E24.toInt())
                }
            }
            card.addView(TextView(ctx).apply {
                text = heading
                textSize = 14f
                setTextColor(0xFFAAAAB2.toInt())
                setPadding(dp(12), dp(4), dp(12), dp(8))
            })
            for ((label, action) in actions) {
                card.addView(TextView(ctx).apply {
                    text = label
                    textSize = 16f
                    setTextColor(Color.WHITE)
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                    setOnClickListener {
                        d.dismiss()
                        try {
                            action()
                        } catch (e: Exception) {
                            Log.e(PM_TAG, "action $label", e)
                        }
                    }
                }, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ))
            }
            d.setContentView(card, LinearLayout.LayoutParams(boxW, LinearLayout.LayoutParams.WRAP_CONTENT))

            val sw = ctx.resources.displayMetrics.widthPixels
            val sh = ctx.resources.displayMetrics.heightPixels
            val estH = dp(44 + 48 * actions.size)
            val below = rawY < sh / 2f
            val py = if (below) rawY.toInt() + dp(16) else (rawY - estH - dp(16)).toInt()
            val px = (rawX - boxW / 2f).toInt().coerceIn(dp(12), sw - boxW - dp(12))
            val pyFinal = py.coerceIn(dp(40), sh - estH - dp(40))

            d.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setDimAmount(0.3f)
                setWindowAnimations(0)
                setGravity(Gravity.TOP or Gravity.START)
                val lp = attributes
                lp.x = px
                lp.y = pyFinal
                attributes = lp
            }
            d.setOnShowListener {
                card.pivotX = boxW / 2f
                card.pivotY = if (below) 0f else estH.toFloat()
                card.scaleX = 0.6f
                card.scaleY = 0.6f
                card.alpha = 0f
                card.animate().scaleX(1f).scaleY(1f).alpha(1f)
                    .setDuration(popupMs).setInterpolator(OvershootInterpolator(0.8f)).start()
            }
            d.setOnDismissListener { if (current === d) current = null }
            current = d
            d.show()
        } catch (e: Exception) {
            Log.e(PM_TAG, "popup show", e)
        }
    }
}
