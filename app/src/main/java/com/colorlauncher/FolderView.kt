package com.colorlauncher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.Interpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

private const val FV_TAG = "CL"
private const val FV_MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val FV_WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/**
 * Folder popup: scrim, card, title, apps grid.
 * Open/close animation ke saath. Stage blur ke liye callback deta hai.
 */
class FolderView(
    private val ctx: Context,
    private val emphasized: Interpolator,
    private val onLaunch: (View, AppItem) -> Unit,
    private val onAppLongPress: (AppItem) -> Unit,
    private val onStageBlur: (Float) -> Unit,
    private val sharedPool: RecyclerView.RecycledViewPool,
    dp: (Int) -> Int
) {
    /** Layer jo MainActivity root mein add karega. */
    val layer: FrameLayout
    private val scrim: View
    private val card: LinearLayout
    private val title: TextView
    private val grid: RecyclerView
    val adapter: AppAdapter

    /** Folder ka id jo abhi khula hai. */
    var openId: String? = null
        private set
    var isOpen: Boolean = false
        private set

    private var progress = 0f
    private var anim: ValueAnimator? = null

    init {
        scrim = View(ctx).apply {
            setBackgroundColor(0x88000000.toInt())
            alpha = 0f
        }
        title = TextView(ctx).apply {
            textSize = 20f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        adapter = AppAdapter(false, { v, i -> onLaunch(v, i) }, { onAppLongPress(it) })
        grid = RecyclerView(ctx).apply {
            layoutManager = GridLayoutManager(ctx, 3)
            this.adapter = this@FolderView.adapter
            setRecycledViewPool(sharedPool)
            overScrollMode = View.OVER_SCROLL_NEVER
            itemAnimator = null
            clipToPadding = false
        }
        card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat()
                setColor(0xF21C1C22.toInt())
            }
            setPadding(dp(16), dp(16), dp(16), dp(12))
            isClickable = true
            alpha = 0f
            scaleX = 0.8f
            scaleY = 0.8f
            addView(title, LinearLayout.LayoutParams(FV_MATCH, FV_WRAP))
            addView(grid, LinearLayout.LayoutParams(FV_MATCH, FV_WRAP).apply { topMargin = dp(8) })
        }
        layer = FrameLayout(ctx).apply {
            visibility = View.GONE
            addView(scrim, FrameLayout.LayoutParams(FV_MATCH, FV_MATCH))
            addView(card, FrameLayout.LayoutParams(dp(300), FV_WRAP, Gravity.CENTER))
        }
    }

    /** Folder kholo: naam, apps, aur open animation. */
    fun open(id: String, name: String, apps: List<AppItem>) {
        try {
            openId = id
            isOpen = true
            title.text = name
            adapter.submitDiff(apps)
            layer.visibility = View.VISIBLE
            animate(1f)
        } catch (e: Exception) {
            Log.e(FV_TAG, "open", e)
        }
    }

    /** Refresh (app list update par) bina animation ke. */
    fun refresh(name: String, apps: List<AppItem>) {
        try {
            if (!isOpen) return
            title.text = name
            adapter.submitDiff(apps)
        } catch (e: Exception) {
            Log.e(FV_TAG, "refresh", e)
        }
    }

    /** Animation se band karo. */
    fun close() {
        if (!isOpen) return
        isOpen = false
        animate(0f)
    }

    /** Turant band (launch ya folder delete par). */
    fun closeInstant() {
        try {
            anim?.cancel()
            isOpen = false
            openId = null
            progress = 0f
            layer.visibility = View.GONE
            scrim.alpha = 0f
            card.alpha = 0f
            card.scaleX = 0.8f
            card.scaleY = 0.8f
            onStageBlur(0f)
        } catch (e: Exception) {
            Log.e(FV_TAG, "closeInstant", e)
        }
    }

    private fun animate(target: Float) {
        try {
            anim?.cancel()
            val a = ValueAnimator.ofFloat(progress, target)
            a.duration = if (target > 0f) 320L else 220L
            a.interpolator = if (target > 0f) OvershootInterpolator(0.7f) else emphasized
            a.addUpdateListener {
                val f = it.animatedValue as Float
                progress = f
                val c = f.coerceIn(0f, 1f)
                scrim.alpha = c
                card.alpha = c
                val s = 0.8f + 0.2f * f
                card.scaleX = s
                card.scaleY = s
                onStageBlur(c)
            }
            a.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (target == 0f && !isOpen) {
                        layer.visibility = View.GONE
                        openId = null
                    }
                }
            })
            anim = a
            a.start()
        } catch (e: Exception) {
            Log.e(FV_TAG, "animate", e)
        }
    }
}
