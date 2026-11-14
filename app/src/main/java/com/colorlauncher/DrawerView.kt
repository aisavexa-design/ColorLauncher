package com.colorlauncher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.Interpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EdgeEffect
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

private const val DV_TAG = "CL"
private const val DV_MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val DV_WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
private const val DV_COLS = 4

/** Edge glow band karne ke liye. */
private class DvNoEdge(ctx: Context) : EdgeEffect(ctx) {
    override fun setSize(width: Int, height: Int) {}
    override fun isFinished(): Boolean = true
    override fun finish() {}
    override fun onPull(deltaDistance: Float) {}
    override fun onPull(deltaDistance: Float, displacement: Float) {}
    override fun onRelease() {}
    override fun onAbsorb(velocity: Int) {}
    override fun draw(canvas: android.graphics.Canvas): Boolean = false
}

/**
 * RecyclerView: fading edge + elastic stretch.
 * Stretch poore list (RecyclerView) ke translationY par hota hai, isliye icons ek saath
 * shift hote hain aur kabhi overlap nahi karte. Child views ko kuch nahi kiya jata.
 * canStretch = false par stretch band (drawer open/close ke waqt).
 */
class DvFadeRecycler(ctx: Context) : RecyclerView(ctx) {

    var canStretch = true

    private val density = ctx.resources.displayMetrics.density
    private val maxStretch = 120f * density
    private var lastY = 0f
    private var touching = false
    private var stretchPx = 0f
    private var shownPx = 0f
    private var animating: ValueAnimator? = null
    private var smoothing = false

    private val smoother = object : Runnable {
        override fun run() {
            shownPx += (stretchPx - shownPx) * 0.15f
            if (abs(stretchPx - shownPx) < 0.3f) shownPx = stretchPx
            translationY = shownPx
            if (shownPx != stretchPx) {
                postOnAnimation(this)
            } else {
                smoothing = false
            }
        }
    }

    override fun getTopFadingEdgeStrength(): Float {
        val len = maxOf(1, verticalFadingEdgeLength).toFloat()
        return (computeVerticalScrollOffset() / len).coerceIn(0f, 1f)
    }

    override fun getBottomFadingEdgeStrength(): Float {
        val len = maxOf(1, verticalFadingEdgeLength).toFloat()
        val rest = computeVerticalScrollRange() - computeVerticalScrollOffset() - computeVerticalScrollExtent()
        return (rest / len).coerceIn(0f, 1f)
    }

    fun resetStretch() {
        animating?.cancel()
        animating = null
        stretchPx = 0f
        shownPx = 0f
        smoothing = false
        translationY = 0f
    }

    private fun kick() {
        if (!smoothing) {
            smoothing = true
            postOnAnimation(smoother)
        }
    }

    private fun setTarget(px: Float) {
        stretchPx = px.coerceIn(-maxStretch, maxStretch)
        kick()
    }

    override fun onScrolled(dx: Int, dy: Int) {
        super.onScrolled(dx, dy)
        if (!canStretch || touching) return
        val atTop = !canScrollVertically(-1)
        val atBottom = !canScrollVertically(1)
        if (atTop && dy < 0) {
            animating?.cancel()
            setTarget(stretchPx + (-dy) * 2.5f)
        } else if (atBottom && dy > 0) {
            animating?.cancel()
            setTarget(stretchPx - dy * 2.5f)
        }
    }

    override fun onScrollStateChanged(state: Int) {
        super.onScrollStateChanged(state)
        if (state == SCROLL_STATE_IDLE && !touching && stretchPx != 0f) springBack()
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            lastY = e.y
            touching = true
            animating?.cancel()
        }
        return super.onInterceptTouchEvent(e)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val r = super.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastY = e.y
                touching = true
                animating?.cancel()
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = e.y - lastY
                lastY = e.y
                if (!canStretch) return r
                val atTop = !canScrollVertically(-1)
                val atBottom = !canScrollVertically(1)
                if ((atTop && dy > 0) || (atBottom && dy < 0) || stretchPx != 0f) {
                    setTarget(stretchPx + dy * 0.6f)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touching = false
                springBack()
            }
        }
        return r
    }

    private fun springBack() {
        animating?.cancel()
        val start = stretchPx
        if (start == 0f) return
        val a = ValueAnimator.ofFloat(start, 0f)
        a.duration = 700
        a.interpolator = OvershootInterpolator(0.4f)
        a.addUpdateListener {
            stretchPx = it.animatedValue as Float
            kick()
        }
        animating = a
        a.start()
    }
}

/**
 * Simple drawer (A-Z list). Header (title, search, search button), apps grid.
 * Progress (0..1) spring se animate hota hai; MainActivity isko home/drawer visibility ke liye use karta hai.
 */
class DrawerView(
    private val ctx: Context,
    private val emphasized: Interpolator,
    private val onLaunch: (View, AppItem) -> Unit,
    private val onSearchToggle: () -> Unit,
    private val onMoreClick: () -> Unit,
    private val sharedPool: RecyclerView.RecycledViewPool,
    private val dp: (Int) -> Int,
    private val onProgress: (Float) -> Unit
) {
    val layer: FrameLayout
    val list: DvFadeRecycler
    val adapter: AppAdapter
    val titleView: TextView
    val searchBox: EditText
    val header: LinearLayout

    var searching: Boolean = false
        private set

    var progress: Float = 0f
        private set

    private var gapH = 8
    private var gapV = 12
    private var bottomGap = 0
    private var allApps: List<AppItem> = emptyList()
    private var anim: SpringAnimation? = null

    init {
        adapter = AppAdapter(true, { v, i -> onLaunch(v, i) }, null)

        list = DvFadeRecycler(ctx).apply {
            layoutManager = GridLayoutManager(ctx, DV_COLS)
            this.adapter = this@DrawerView.adapter
            setRecycledViewPool(sharedPool)
            setHasFixedSize(true)
            setItemViewCacheSize(24)
            itemAnimator = null
            clipToPadding = true
            overScrollMode = View.OVER_SCROLL_NEVER
            edgeEffectFactory = object : RecyclerView.EdgeEffectFactory() {
                override fun createEdgeEffect(view: RecyclerView, direction: Int): EdgeEffect =
                    if (direction == RecyclerView.EdgeEffectFactory.DIRECTION_BOTTOM) EdgeEffect(view.context)
                    else DvNoEdge(view.context)
            }
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(56))
            setPadding(dp(8), dp(64), dp(8), 0)
        }
        list.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                val n = state.itemCount
                if (n == 0) return
                val pos = parent.getChildAdapterPosition(view)
                if (pos == RecyclerView.NO_POSITION) return
                val h = dp(gapH) / 2
                val v = dp(gapV) / 2
                outRect.set(h, v, h, v)
                val rem = n % DV_COLS
                val lastRowStart = n - (if (rem == 0) DV_COLS else rem)
                if (pos >= lastRowStart) outRect.bottom += bottomGap
            }
        })

        titleView = TextView(ctx).apply {
            text = "All apps"
            textSize = 26f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        searchBox = EditText(ctx).apply {
            hint = "Search apps"
            setHintTextColor(0x99FFFFFF.toInt())
            setTextColor(Color.WHITE)
            textSize = 18f
            setSingleLine(true)
            background = null
            visibility = View.GONE
            doAfterTextChanged { filter(it?.toString() ?: "") }
        }
        val searchBtn = SearchIcon(ctx).apply {
            setOnClickListener { onSearchToggle() }
        }
        val moreBtn = MoreIcon(ctx).apply {
            setOnClickListener { onMoreClick() }
        }
        header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), 0, dp(12), 0)
            addView(titleView, LinearLayout.LayoutParams(0, DV_WRAP, 1f))
            addView(searchBox, LinearLayout.LayoutParams(0, DV_WRAP, 1f))
            addView(searchBtn, LinearLayout.LayoutParams(dp(44), dp(44)))
            addView(moreBtn, LinearLayout.LayoutParams(dp(44), dp(44)))
        }

        layer = FrameLayout(ctx).apply {
            visibility = View.INVISIBLE
            addView(list, FrameLayout.LayoutParams(DV_MATCH, DV_MATCH))
            addView(header, FrameLayout.LayoutParams(DV_MATCH, dp(56), Gravity.TOP))
        }
    }

    fun submitApps(apps: List<AppItem>) {
        allApps = apps
        filter(searchBox.text?.toString() ?: "")
    }

    fun setStyle(s: IconStyle) {
        adapter.applyStyle(s)
        gapH = s.gapH
        gapV = s.gapV
        list.invalidateItemDecorations()
    }

    fun setInsets(top: Int, bottom: Int, ime: Int) {
        (header.layoutParams as FrameLayout.LayoutParams).topMargin = top
        header.requestLayout()
        list.setPadding(dp(8), top + dp(64), dp(8), ime)
        bottomGap = bottom + dp(24)
        list.invalidateItemDecorations()
    }

    private fun filter(q: String) {
        try {
            val l = if (q.isBlank()) allApps else allApps.filter { it.label.contains(q, ignoreCase = true) }
            adapter.submitDiff(l)
        } catch (e: Exception) {
            Log.e(DV_TAG, "filter", e)
        }
    }

    fun setProgress(p: Float) {
        try {
            progress = p
            if (p < 0.999f) list.resetStretch()
            layer.alpha = p
            val ph = (layer.parent as? View)?.height ?: 0
            layer.translationY = (1f - p) * 0.25f * ph
            val v = if (p > 0.001f) View.VISIBLE else View.INVISIBLE
            if (layer.visibility != v) layer.visibility = v
            onProgress(p)
        } catch (e: Exception) {
            Log.e(DV_TAG, "setProgress", e)
        }
    }

    /** Drawer open/close gesture ke waqt list stretch band. */
    fun setDrawerGesture(active: Boolean) {
        list.canStretch = !active
        if (active) list.resetStretch()
    }

    fun animateTo(target: Float, velocity: Float, onEnd: () -> Unit) {
        try {
            anim?.cancel()
            list.canStretch = false
            list.resetStretch()
            val a = SpringAnimation(FloatValueHolder(progress))
            a.spring = SpringForce(target).setStiffness(380f).setDampingRatio(0.92f)
            a.setStartVelocity(velocity)
            a.setMinimumVisibleChange(1f / 256f)
            a.addUpdateListener { _, value, _ -> setProgress(value.coerceIn(0f, 1f)) }
            a.addEndListener { _, canceled, _, _ ->
                list.canStretch = true
                if (!canceled) {
                    setProgress(target)
                    onEnd()
                }
            }
            anim = a
            a.start()
        } catch (e: Exception) {
            Log.e(DV_TAG, "animateTo", e)
        }
    }

    fun cancelAnim() {
        anim?.cancel()
        list.canStretch = true
    }

    fun isAnimating(): Boolean = anim?.isRunning == true

    fun setSearch(on: Boolean, imm: InputMethodManager?) {
        try {
            searching = on
            titleView.visibility = if (on) View.GONE else View.VISIBLE
            searchBox.visibility = if (on) View.VISIBLE else View.GONE
            playRows(on)
            if (on) {
                searchBox.alpha = 0f
                searchBox.translationY = dp(24).toFloat()
                searchBox.animate().alpha(1f).translationY(0f).setDuration(320).setInterpolator(emphasized).start()
                searchBox.requestFocus()
                imm?.showSoftInput(searchBox, 0)
            } else {
                searchBox.setText("")
                imm?.hideSoftInputFromWindow(searchBox.windowToken, 0)
            }
        } catch (e: Exception) {
            Log.e(DV_TAG, "setSearch", e)
        }
    }

    private fun playRows(show: Boolean) {
        for (i in 0 until list.childCount) {
            val v = list.getChildAt(i)
            val pos = list.getChildAdapterPosition(v)
            val row = if (pos == RecyclerView.NO_POSITION) 0 else pos / DV_COLS
            v.animate().cancel()
            if (show) {
                v.translationY = dp(120).toFloat()
                v.alpha = 0f
                v.animate().translationY(0f).alpha(1f).setStartDelay(row * 48L).setDuration(560)
                    .setInterpolator(OvershootInterpolator(1.35f)).start()
            } else {
                v.animate().translationY(dp(80).toFloat()).alpha(0f).setStartDelay(row * 20L).setDuration(200)
                    .setInterpolator(emphasized)
                    .withEndAction { v.translationY = 0f; v.alpha = 1f }
                    .start()
            }
        }
    }
}
