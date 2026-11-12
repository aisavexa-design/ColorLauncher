package com.colorlauncher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.Interpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.max

private const val HS_TAG = "CL"
private const val HS_MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val HS_WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
private const val HS_COLS = 4
private const val HS_PAGE_CELLS = 24
private const val HS_DOCK_SLOTS = 4

/**
 * Home screen (Hissa 1): clock, pages (4x6 grid), page dots, dock, page swipe, intro stagger.
 * Drag-and-drop aur folder-create Hissa 2 mein hain.
 */
class HomeScreen(
    private val ctx: Context,
    private val sharedPool: RecyclerView.RecycledViewPool,
    private val onLaunch: (View, AppItem) -> Unit,
    private val dp: (Int) -> Int
) {
    /** Root view. MainActivity stage mein add karega. */
    val layer: LinearLayout

    val clock: TextClock
    val date: TextClock
    val widgetBox: LinearLayout
    val hint: TextView
    val pageHost: FrameLayout
    val pageRow: LinearLayout
    val pageDots: LinearLayout
    val dock: RecyclerView
    val dockHolder: LinearLayout
    val dockAdapter: AppAdapter

    val pageGrids = mutableListOf<RecyclerView>()
    val pageAdapters = mutableListOf<AppAdapter>()

    var pageCount: Int = 1
        private set
    var currentPage: Int = 0
    var pageOffset: Float = 0f
        private set

    private var pageAnimator: ValueAnimator? = null
    private val emphasized: Interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
    private val easeOut: Interpolator = PathInterpolator(0.2f, 0.8f, 0.2f, 1f)
    private var dockIconDp = 58

    /** Pages ka data: 0..(pageCount*24-1). Sirf index pe kaam, bind MainActivity/repo karega. */
    val homeSlots = MutableList<String?>(HS_PAGE_CELLS) { null }
    val dockSlots = MutableList<String?>(HS_DOCK_SLOTS) { null }

    init {
        dockAdapter = AppAdapter(false, { v, i -> onLaunch(v, i) }, null)

        clock = TextClock(ctx).apply {
            format12Hour = "h:mm"
            format24Hour = "H:mm"
            textSize = 64f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        date = TextClock(ctx).apply {
            format12Hour = "EEE, d MMMM"
            format24Hour = "EEE, d MMMM"
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        widgetBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        hint = TextView(ctx).apply {
            text = "Long-press an app to add it here."
            textSize = 13f
            setTextColor(0xB3FFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(32), dp(24), 0)
        }

        pageRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        pageHost = FrameLayout(ctx).apply {
            clipChildren = true
            addView(pageRow, FrameLayout.LayoutParams(HS_MATCH, HS_MATCH))
        }
        pageDots = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        dock = RecyclerView(ctx).apply {
            layoutManager = GridLayoutManager(ctx, HS_DOCK_SLOTS)
            adapter = dockAdapter
            setRecycledViewPool(sharedPool)
            overScrollMode = View.OVER_SCROLL_NEVER
            itemAnimator = null
            background = GradientDrawable().apply {
                cornerRadius = dp(30).toFloat()
                setColor(0x33FFFFFF)
            }
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        dockHolder = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(dock, LinearLayout.LayoutParams(HS_MATCH, HS_WRAP))
        }

        layer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(clock, LinearLayout.LayoutParams(HS_MATCH, HS_WRAP))
            addView(date, LinearLayout.LayoutParams(HS_MATCH, HS_WRAP))
            addView(widgetBox, LinearLayout.LayoutParams(HS_MATCH, HS_WRAP))
            addView(hint, LinearLayout.LayoutParams(HS_MATCH, HS_WRAP))
            addView(pageHost, LinearLayout.LayoutParams(HS_MATCH, 0, 1f))
            addView(pageDots, LinearLayout.LayoutParams(HS_MATCH, HS_WRAP).apply { topMargin = dp(6) })
            addView(dockHolder, LinearLayout.LayoutParams(HS_MATCH, HS_WRAP))
        }
    }

    /** Pages ka data load ho gaya ho to pageCount set karo (MainActivity loadPages ke baad). */
    fun setPageCount(count: Int) {
        pageCount = count.coerceIn(1, 10)
    }

    /** Pages (RecyclerView grids) dobara banao. */
    fun rebuildPages(onLong: (AppItem) -> Unit) {
        try {
            pageRow.removeAllViews()
            pageGrids.clear()
            pageAdapters.clear()
            for (p in 0 until pageCount) {
                val adapter = AppAdapter(true, { v, i -> onLaunch(v, i) }, null)
                val grid = RecyclerView(ctx).apply {
                    layoutManager = GridLayoutManager(ctx, HS_COLS)
                    this.adapter = adapter
                    setRecycledViewPool(sharedPool)
                    overScrollMode = View.OVER_SCROLL_NEVER
                    itemAnimator = null
                    clipToPadding = false
                    setPadding(dp(8), dp(16), dp(8), 0)
                }
                pageGrids.add(grid)
                pageAdapters.add(adapter)
                pageRow.addView(grid, LinearLayout.LayoutParams(HS_MATCH, HS_MATCH))
            }
            pageRow.layoutParams = FrameLayout.LayoutParams(HS_MATCH, HS_MATCH)
            pageHost.post { layoutPages() }
            pageDots.removeAllViews()
            for (p in 0 until pageCount) {
                pageDots.addView(View(ctx), LinearLayout.LayoutParams(dp(6), dp(6)).apply {
                    marginStart = dp(4); marginEnd = dp(4)
                })
            }
            pageDots.visibility = if (pageCount > 1) View.VISIBLE else View.GONE
            if (currentPage >= pageCount) currentPage = pageCount - 1
            updateDots()
        } catch (e: Exception) {
            Log.e(HS_TAG, "rebuildPages", e)
        }
    }

    /** Page ka slot data grids ko do (MainActivity refresh ke waqt). */
    fun pageItems(p: Int): List<AppItem?> {
        val from = p * HS_PAGE_CELLS
        val to = minOf(from + HS_PAGE_CELLS, homeSlots.size)
        if (from >= to) return emptyList()
        return homeSlots.subList(from, to).map { null }
    }

    fun setDockIconDp(v: Int) {
        dockIconDp = v
    }

    fun layoutPages() {
        try {
            val w = pageHost.width
            if (w <= 0) return
            for (grid in pageGrids) {
                val lp = grid.layoutParams as LinearLayout.LayoutParams
                if (lp.width != w) {
                    lp.width = w
                    grid.layoutParams = lp
                }
            }
            pageRow.requestLayout()
            setPageOffset(-currentPage * w.toFloat())
        } catch (e: Exception) {
            Log.e(HS_TAG, "layoutPages", e)
        }
    }

    fun pageWidth(): Float = pageHost.width.toFloat().coerceAtLeast(1f)

    fun setPageOffset(x: Float) {
        pageOffset = x
        pageRow.translationX = x
    }

    fun updateDots() {
        try {
            for (i in 0 until pageDots.childCount) {
                val v = pageDots.getChildAt(i)
                val on = i == currentPage
                val size = if (on) dp(8) else dp(6)
                v.layoutParams = (v.layoutParams as LinearLayout.LayoutParams).apply {
                    width = size
                    height = size
                }
                v.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (on) 0xFFFFFFFF.toInt() else 0x66FFFFFF)
                }
            }
        } catch (e: Exception) {
            Log.e(HS_TAG, "updateDots", e)
        }
    }

    /** Page change, animated ya seedha. */
    fun goToPage(p: Int, animated: Boolean, animEnabled: Boolean) {
        try {
            val target = p.coerceIn(0, max(0, pageCount - 1))
            currentPage = target
            updateDots()
            val end = -target * pageWidth()
            pageAnimator?.cancel()
            if (animated && animEnabled) {
                val a = ValueAnimator.ofFloat(pageOffset, end)
                a.duration = 260
                a.interpolator = emphasized
                a.addUpdateListener { setPageOffset(it.animatedValue as Float) }
                pageAnimator = a
                a.start()
            } else {
                setPageOffset(end)
            }
        } catch (e: Exception) {
            Log.e(HS_TAG, "goToPage", e)
        }
    }

    fun cancelPageAnim() {
        pageAnimator?.cancel()
    }

    fun isPageAnimating(): Boolean = pageAnimator?.isRunning == true

    /** Icons stagger (unlock, app close). */
    fun playIntro(enabled: Boolean) {
        try {
            if (!enabled) return
            val page = pageGrids.getOrNull(currentPage) ?: return
            val items = ArrayList<Pair<View, Int>>()
            for (i in 0 until page.childCount) {
                val v = page.getChildAt(i)
                val pos = page.getChildAdapterPosition(v)
                if (pos != RecyclerView.NO_POSITION) items.add(v to pos / HS_COLS)
            }
            for (i in 0 until dock.childCount) items.add(dock.getChildAt(i) to 0)
            val offset = dp(32).toFloat()
            for ((v, _) in items) {
                v.animate().cancel()
                v.translationY = offset
                v.alpha = 0f
                v.scaleX = 0.9f
                v.scaleY = 0.9f
            }
            for ((v, row) in items) {
                v.animate()
                    .translationY(0f).alpha(1f).scaleX(1f).scaleY(1f)
                    .setStartDelay(row * 40L)
                    .setDuration(380)
                    .setInterpolator(easeOut)
                    .start()
            }
        } catch (e: Exception) {
            Log.e(HS_TAG, "playIntro", e)
        }
    }
}
