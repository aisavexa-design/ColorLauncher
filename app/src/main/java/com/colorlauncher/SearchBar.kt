package com.colorlauncher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Home par niche se aane wala search bar. Neeche se slide hota hai, 4 suggestion icons dikhata hai.
 * onSearch(query) se filter, onLaunch(item) se app khulta hai.
 */
class SearchBar(
    private val ctx: Context,
    private val dp: (Int) -> Int,
    private val onLaunch: (View, AppItem) -> Unit
) {
    val layer: FrameLayout = FrameLayout(ctx).apply {
        visibility = View.GONE
        setBackgroundColor(0x99000000.toInt())
        isClickable = true
    }

    private val sheet: LinearLayout
    private val input: EditText
    private val suggestRow: LinearLayout
    private val suggestIcons = mutableListOf<ImageView>()
    private val suggestLabels = mutableListOf<TextView>()

    private var source: List<AppItem> = emptyList()
    private var shown = false

    init {
        sheet = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
            background = GlassBackground.make(ctx, 28f)
        }

        input = EditText(ctx).apply {
            hint = "Search apps"
            setHintTextColor(0x88FFFFFF.toInt())
            setTextColor(Color.WHITE)
            textSize = 18f
            setSingleLine(true)
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(0x33FFFFFF)
            }
            setPadding(dp(16), dp(12), dp(16), dp(12))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) { refresh(s?.toString() ?: "") }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        sheet.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        suggestRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, 0)
        }
        for (i in 0 until 4) {
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
            val icon = ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            val label = TextView(ctx).apply {
                setTextColor(Color.WHITE)
                textSize = 11f
                maxLines = 1
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, 0)
            }
            col.addView(icon, LinearLayout.LayoutParams(dp(56), dp(56)))
            col.addView(label, LinearLayout.LayoutParams(dp(72), ViewGroup.LayoutParams.WRAP_CONTENT))
            col.setOnClickListener { pick(i) }
            suggestIcons.add(icon)
            suggestLabels.add(label)
            suggestRow.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        sheet.addView(suggestRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        layer.addView(sheet, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        layer.setOnClickListener { hide() }
        sheet.setOnClickListener { }
    }

    /** Apps list dene par suggestions update honge. */
    fun submitApps(apps: List<AppItem>) {
        source = apps
        refresh(input.text?.toString() ?: "")
    }

    /** Pehle se 4 suggestions: query khali ho to top 4 apps, warna matching apps. */
    private fun refresh(q: String) {
        val list = if (q.isBlank()) source else source.filter { it.label.contains(q, ignoreCase = true) }
        val top = list.take(4)
        for (i in 0 until 4) {
            val item = top.getOrNull(i)
            val icon = suggestIcons[i]
            val label = suggestLabels[i]
            if (item == null) {
                icon.setImageDrawable(null)
                label.text = ""
                icon.parent?.let { (it as View).visibility = View.INVISIBLE }
            } else {
                (icon.parent as View).visibility = View.VISIBLE
                icon.setImageBitmap(item.icon)
                label.text = item.label
            }
        }
    }

    private fun pick(i: Int) {
        val q = input.text?.toString() ?: ""
        val list = if (q.isBlank()) source else source.filter { it.label.contains(q, ignoreCase = true) }
        val item = list.getOrNull(i) ?: return
        hide()
        onLaunch(suggestIcons[i], item)
    }

    /** Neeche se slide-up animation ke saath dikhao. */
    fun show() {
        if (shown) return
        shown = true
        layer.visibility = View.VISIBLE
        layer.alpha = 0f
        layer.animate().alpha(1f).setDuration(200).start()
        sheet.translationY = dp(400).toFloat()
        sheet.animate().translationY(0f).setDuration(360)
            .setInterpolator(OvershootInterpolator(0.9f)).start()
        // Icons ek-ek karke rubber pop-in
        for ((idx, col) in suggestRow.let { r -> (0 until r.childCount).map { r.getChildAt(it) } }.withIndex()) {
            col.alpha = 0f
            col.translationY = dp(30).toFloat()
            col.animate().alpha(1f).translationY(0f).setStartDelay(120L + idx * 60L)
                .setDuration(320).setInterpolator(DecelerateInterpolator()).start()
        }
    }

    /** Animation ke saath band karo. */
    fun hide() {
        if (!shown) return
        shown = false
        layer.animate().alpha(0f).setDuration(160).withEndAction {
            layer.visibility = View.GONE
            input.setText("")
            input.clearFocus()
        }.start()
        sheet.animate().translationY(dp(400).toFloat()).setDuration(220).start()
    }

    fun isShown(): Boolean = shown
}
