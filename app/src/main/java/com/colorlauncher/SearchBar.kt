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
import android.view.animation.PathInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Home par niche swipe se search panel. Panel UPAR se aata hai.
 * setProgress(p) se swipe ke saath home fade hota hai, aur keyboard turant khulta hai.
 */
class SearchBar(
    private val ctx: Context,
    private val dp: (Int) -> Int,
    private val onLaunch: (View, AppItem) -> Unit,
    private val onProgress: (Float) -> Unit
) {
    val layer: FrameLayout = FrameLayout(ctx).apply {
        visibility = View.GONE
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = true
    }

    private val sheet: LinearLayout
    private val input: EditText
    private val suggestRow: LinearLayout
    private val suggestIcons = mutableListOf<ImageView>()
    private val suggestLabels = mutableListOf<TextView>()
    private val emphasized = PathInterpolator(0.4f, 0f, 0.2f, 1f)

    private var source: List<AppItem> = emptyList()
    private var shown = false
    private var progress = 0f

    init {
        sheet = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(56), dp(16), dp(20))
            background = GlassBackground.make(ctx, 32f)
        }

        input = EditText(ctx).apply {
            hint = "Search apps"
            setHintTextColor(0x88FFFFFF.toInt())
            setTextColor(Color.WHITE)
            textSize = 18f
            setSingleLine(true)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
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
            val icon = ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
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

        // Sheet upar se aata hai (Gravity.TOP)
        layer.addView(sheet, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))
        layer.setOnClickListener { hide() }
        sheet.setOnClickListener { }
    }

    fun submitApps(apps: List<AppItem>) {
        source = apps
        refresh(input.text?.toString() ?: "")
    }

    private fun refresh(q: String) {
        val list = if (q.isBlank()) source else source.filter { it.label.contains(q, ignoreCase = true) }
        val top = list.take(4)
        for (i in 0 until 4) {
            val item = top.getOrNull(i)
            val icon = suggestIcons[i]
            val label = suggestLabels[i]
            val col = icon.parent as View
            if (item == null) {
                icon.setImageDrawable(null)
                label.text = ""
                col.visibility = View.INVISIBLE
            } else {
                col.visibility = View.VISIBLE
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

    /**
     * Swipe progress (0..1). 0 = home normal, 1 = search poora khula.
     * Home ko fade karta hai aur panel upar se slide karta hai.
     */
    fun setProgress(p: Float) {
        progress = p.coerceIn(0f, 1f)
        layer.visibility = if (progress > 0f) View.VISIBLE else View.GONE
        layer.alpha = progress
        val h = sheet.height.takeIf { it > 0 } ?: dp(400)
        sheet.translationY = -(1f - progress) * h
        onProgress(progress)
    }

    /** Swipe complete ya fling par poora khulna. Keyboard turant khulta hai. */
    fun show() {
        if (shown) return
        shown = true
        layer.visibility = View.VISIBLE
        animateTo(1f)
        input.requestFocus()
        val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        val row = suggestRow
        for (idx in 0 until row.childCount) {
            val col = row.getChildAt(idx)
            col.alpha = 0f
            col.translationY = dp(24).toFloat()
            col.animate().alpha(1f).translationY(0f).setStartDelay(120L + idx * 50L)
                .setDuration(300).setInterpolator(DecelerateInterpolator()).start()
        }
    }

    /** Animation ke saath band, keyboard bhi band. */
    fun hide() {
        if (!shown && progress == 0f) return
        shown = false
        val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(input.windowToken, 0)
        input.clearFocus()
        animateTo(0f) {
            input.setText("")
            layer.visibility = View.GONE
        }
    }

    private fun animateTo(target: Float, end: (() -> Unit)? = null) {
        val start = progress
        val a = android.animation.ValueAnimator.ofFloat(start, target)
        a.duration = if (target > 0f) 320L else 240L
        a.interpolator = emphasized
        a.addUpdateListener { setProgress(it.animatedValue as Float) }
        a.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) { end?.invoke() }
        })
        a.start()
    }

    fun isShown(): Boolean = shown
}
