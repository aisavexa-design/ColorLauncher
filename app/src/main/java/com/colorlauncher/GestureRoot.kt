package com.colorlauncher

import android.content.Context
import android.util.Log
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

private const val GR_TAG = "CL"
internal const val MODE_NONE = 0
internal const val MODE_PENDING = 1
internal const val MODE_LONG = 2
internal const val MODE_DRAG = 3
internal const val MODE_PAGE = 4
internal const val MODE_IGNORE = 5

/**
 * Touch layer.
 * - Long press 700ms: sirf tab, jab touch bilkul na hila ho aur list scroll na ho rahi ho.
 * - Movement slop se zyada hote hi long-press turant cancel.
 * - Vertical drag (drawer) aur horizontal page swipe.
 * - Home par neeche ki taraf swipe (ACTION_UP par) -> onSearchSwipe().
 */
class GestureRoot(ctx: Context, private val host: Host) : FrameLayout(ctx) {

    interface Host {
        fun canStartDrag(dy: Float): Boolean
        fun onDragStart()
        fun onDrag(dy: Float)
        fun onDragEnd(vy: Float)
        fun canStartPageSwipe(dx: Float): Boolean
        fun onPageDrag(dx: Float)
        fun onPageDragEnd(vx: Float)
        fun isBusy(): Boolean
        fun canLongPress(): Boolean
        fun onLongPress(rawX: Float, rawY: Float)
        fun onSearchSwipe()
    }

    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private val edgePx = 24 * ctx.resources.displayMetrics.density
    private val longMs = 700L
    private var mode = MODE_NONE
    private var downX = 0f
    private var downY = 0f
    private var rawDownX = 0f
    private var rawDownY = 0f
    private var moved = false
    private var vt: VelocityTracker? = null
    private var lastKey = ""

    private val longRunnable = Runnable {
        try {
            if (mode == MODE_PENDING && !moved && host.canLongPress()) {
                mode = MODE_LONG
                host.onLongPress(rawDownX, rawDownY)
            }
        } catch (e: Exception) {
            Log.e(GR_TAG, "longPress", e)
        }
    }

    /** Scroll ya movement shuru hone par bahar se bhi call hota hai. */
    fun cancelLongTimer() {
        removeCallbacks(longRunnable)
        moved = true
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(longRunnable)
        vt?.recycle()
        vt = null
        super.onDetachedFromWindow()
    }

    private fun inEdge(x: Float, y: Float): Boolean =
        x < edgePx || x > width - edgePx || y > height - edgePx

    private fun track(e: MotionEvent) {
        val key = "${e.eventTime}:${e.actionMasked}:${e.x}:${e.y}"
        if (key == lastKey) return
        lastKey = key
        try {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.x
                    downY = e.y
                    rawDownX = e.rawX
                    rawDownY = e.rawY
                    moved = false
                    removeCallbacks(longRunnable)
                    vt?.recycle()
                    vt = VelocityTracker.obtain()
                    vt?.addMovement(e)
                    if (host.isBusy() || inEdge(e.x, e.y)) {
                        mode = MODE_IGNORE
                    } else {
                        mode = MODE_PENDING
                        postDelayed(longRunnable, longMs)
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (mode == MODE_NONE || mode == MODE_IGNORE || mode == MODE_LONG) return
                    vt?.addMovement(e)
                    val dx = e.x - downX
                    val dy = e.y - downY
                    if (!moved && (abs(dx) > slop || abs(dy) > slop)) {
                        moved = true
                        removeCallbacks(longRunnable)
                    }
                    when (mode) {
                        MODE_PENDING -> {
                            if (abs(dy) > slop && abs(dy) > abs(dx) * 1.5f && host.canStartDrag(dy)) {
                                mode = MODE_DRAG
                                removeCallbacks(longRunnable)
                                host.onDragStart()
                                host.onDrag(dy)
                                parent?.requestDisallowInterceptTouchEvent(true)
                            } else if (abs(dx) > slop && abs(dx) > abs(dy) * 1.5f && host.canStartPageSwipe(dx)) {
                                mode = MODE_PAGE
                                removeCallbacks(longRunnable)
                                host.onPageDrag(dx)
                                parent?.requestDisallowInterceptTouchEvent(true)
                            }
                        }
                        MODE_DRAG -> host.onDrag(dy)
                        MODE_PAGE -> host.onPageDrag(dx)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    removeCallbacks(longRunnable)
                    val cancel = e.actionMasked == MotionEvent.ACTION_CANCEL
                    // Home par neeche swipe: search bar
                    if (mode == MODE_PENDING && !cancel &&
                        (e.y - downY) > slop * 3 && abs(e.y - downY) > abs(e.x - downX) * 1.5f
                    ) {
                        host.onSearchSwipe()
                    }
                    if (mode == MODE_DRAG) {
                        vt?.addMovement(e)
                        vt?.computeCurrentVelocity(1000)
                        host.onDragEnd(if (cancel) 0f else (vt?.yVelocity ?: 0f))
                    } else if (mode == MODE_PAGE) {
                        vt?.addMovement(e)
                        vt?.computeCurrentVelocity(1000)
                        host.onPageDragEnd(if (cancel) 0f else (vt?.xVelocity ?: 0f))
                    }
                    mode = MODE_NONE
                    moved = false
                    vt?.recycle()
                    vt = null
                }
            }
        } catch (e: Exception) {
            Log.e(GR_TAG, "gesture", e)
            mode = MODE_NONE
        }
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        track(e)
        if (e.actionMasked == MotionEvent.ACTION_MOVE && mode == MODE_PENDING) {
            if (abs(e.x - downX) > slop || abs(e.y - downY) > slop) cancelLongTimer()
        }
        return mode == MODE_DRAG || mode == MODE_PAGE
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        track(e)
        return true
    }
}
