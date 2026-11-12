package com.colorlauncher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
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
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.max

private const val HS_TAG = "CL"
private const val HS_MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val HS_WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
private const val HS_COLS = 4
private const val HS_PAGE_CELLS = 24
private const val HS_DOCK_SLOTS = 4
private const val HS_MAX_PAGES = 10
private const val HS_MAX_FOLDER = 12
const val HS_FOLDER_PREFIX = "folder:"

/** Drag state ka callback MainActivity ko milta hai (folder/app naam, toast, persist). */
interface HomeCallbacks {
    fun onPagesChanged()
    fun onPersist()
    fun onToast(msg: String)
    fun onFolderCreated(id: String)
    fun onRemoveFolder(id: String)
    fun resolve(key: String): AppItem?
    fun folderName(id: String): String?
    fun folderKeys(id: String): MutableList<String>?
    fun createFolder(id: String, a: String, b: String)
    fun deleteFolder(id: String)
    fun pageIsOpen(): Boolean
}

/**
 * Home screen: pages, dock, drag-and-drop, folder create/add, remove zone, hit tests.
 */
class HomeScreen(
    private val ctx: Context,
    private val sharedPool: RecyclerView.RecycledViewPool,
    private val onLaunch: (View, AppItem) -> Unit,
    private val dp: (Int) -> Int,
    private val cb: HomeCallbacks
) {
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
    val removeZone: TextView

    val pageGrids = mutableListOf<RecyclerView>()
    val pageAdapters = mutableListOf<AppAdapter>()
    val pageGuides = mutableListOf<GridGuide>()

    var pageCount: Int = 1
        private set
    var currentPage: Int = 0
    var pageOffset: Float = 0f
        private set

    /** Drag ke dauran screen coordinates. */
    var dragLock = false
        private set
    private var dropX = 0f
    private var dropY = 0f
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var lastEdgeTime = 0L

    private var pageAnimator: ValueAnimator? = null
    private val emphasized: Interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
    private val easeOut: Interpolator = PathInterpolator(0.2f, 0.8f, 0.2f, 1f)

    /** Slots: key (app key ya "folder:id") ya null. */
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
            background = if (FeatureFlags.glassOn(ctx, FeatureFlags.KEY_GLASS_DOCK)) {
                GlassBackground.make(ctx, 30f)
            } else {
                GradientDrawable().apply {
                    cornerRadius = dp(30).toFloat()
                    setColor(0x33FFFFFF)
                }
             }           
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        dockHolder = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(dock, LinearLayout.LayoutParams(HS_MATCH, HS_WRAP))
        }

        removeZone = TextView(ctx).apply {
            text = "Drop here to remove"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(0xCCE5484D.toInt())
            }
            visibility = View.GONE
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

    /** Grid guide (drag ke dauran dashed slots). */
    inner class GridGuide : RecyclerView.ItemDecoration() {
        var visible = false
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = 0x66FFFFFF
            pathEffect = DashPathEffect(floatArrayOf(12f, 10f), 0f)
        }
        private val r = RectF()

        override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            if (!visible) return
            try {
                val child = parent.getChildAt(0) ?: return
                val rowH = child.height.toFloat()
                if (rowH <= 0f) return
                val left = parent.paddingLeft.toFloat()
                val right = (parent.width - parent.paddingRight).toFloat()
                val cw = (right - left) / HS_COLS
                val inset = dp(4).toFloat()
                for (row in 0 until HS_PAGE_CELLS / HS_COLS) for (col in 0 until HS_COLS) {
                    val top = parent.paddingTop + row * rowH
                    r.set(left + col * cw + inset, top + inset, left + (col + 1) * cw - inset, top + rowH - inset)
                    c.drawRoundRect(r, dp(16).toFloat(), dp(16).toFloat(), paint)
                }
            } catch (e: Exception) {
                Log.e(HS_TAG, "guide", e)
            }
        }
    }

    // ---------------- Pages ----------------

    fun setPageCount(count: Int) {
        pageCount = count.coerceIn(1, HS_MAX_PAGES)
    }

    /** Pages (grids) dobara banao aur drag helpers attach karo. */
    fun rebuildPages(resolve: (String) -> AppItem?) {
        try {
            pageRow.removeAllViews()
            pageGrids.clear()
            pageAdapters.clear()
            pageGuides.clear()
            for (p in 0 until pageCount) {
                val adapter = AppAdapter(true, { v, i -> onLaunch(v, i) }, null)
                val guide = GridGuide()
                val grid = RecyclerView(ctx).apply {
                    layoutManager = GridLayoutManager(ctx, HS_COLS)
                    this.adapter = adapter
                    setRecycledViewPool(sharedPool)
                    overScrollMode = View.OVER_SCROLL_NEVER
                    itemAnimator = null
                    clipToPadding = false
                    setPadding(dp(8), dp(16), dp(8), 0)
                    addItemDecoration(guide)
                }
                pageGrids.add(grid)
                pageAdapters.add(adapter)
                pageGuides.add(guide)
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
            attachDrags()
            refresh(resolve)
        } catch (e: Exception) {
            Log.e(HS_TAG, "rebuildPages", e)
        }
    }

    /** Slots se adapters ka data bharo. resolve key ko AppItem ya folder item deta hai. */
    fun refresh(resolve: (String) -> AppItem?) {
        try {
            for (p in 0 until pageAdapters.size) {
                val from = p * HS_PAGE_CELLS
                val to = minOf(from + HS_PAGE_CELLS, homeSlots.size)
                if (from >= to) break
                pageAdapters[p].submitDiff(homeSlots.subList(from, to).map { k -> k?.let(resolve) })
            }
            dockAdapter.submitDiff(dockSlots.map { k -> k?.let(resolve) })
            hint.visibility = if (homeSlots.all { it == null }) View.VISIBLE else View.GONE
            updateDots()
        } catch (e: Exception) {
            Log.e(HS_TAG, "refresh", e)
        }
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

    fun addPage() {
        if (pageCount >= HS_MAX_PAGES) return
        pageCount++
        for (i in 0 until HS_PAGE_CELLS) homeSlots.add(null)
        cb.onPagesChanged()
    }

    fun trimEmptyPages() {
        try {
            while (pageCount > 1) {
                val lastStart = (pageCount - 1) * HS_PAGE_CELLS
                if (lastStart + HS_PAGE_CELLS > homeSlots.size) break
                val empty = (lastStart until lastStart + HS_PAGE_CELLS).all { homeSlots.getOrNull(it) == null }
                if (!empty || currentPage == pageCount - 1) break
                repeat(HS_PAGE_CELLS) { homeSlots.removeAt(homeSlots.size - 1) }
                pageCount--
            }
            if (currentPage >= pageCount) currentPage = pageCount - 1
        } catch (e: Exception) {
            Log.e(HS_TAG, "trim", e)
        }
    }

    fun setDockIconDp(v: Int) {
        dockAdapter.applyStyle(IconStyle(v, 0, false, 8, 12))
    }

    // ---------------- Intro ----------------

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

    // ---------------- Drag and drop ----------------

    private fun attachDrags() {
        for (i in pageGrids.indices) {
            dragHelper(pageAdapters[i], i, pageGrids[i], pageGuides[i], true).attachToRecyclerView(pageGrids[i])
        }
        dragHelper(dockAdapter, -1, dock, null, false).attachToRecyclerView(dock)
    }

    private fun isFolder(k: String?) = k != null && k.startsWith(HS_FOLDER_PREFIX)

    private fun folderIdOf(k: String) = k.removePrefix(HS_FOLDER_PREFIX)

    private fun childPosAt(rv: RecyclerView, x: Float, y: Float): Int {
        val loc = IntArray(2)
        rv.getLocationOnScreen(loc)
        val child = rv.findChildViewUnder(x - loc[0], y - loc[1]) ?: return RecyclerView.NO_POSITION
        return rv.getChildAdapterPosition(child)
    }

    fun inside(v: View, x: Float, y: Float): Boolean {
        if (v.visibility != View.VISIBLE || v.height == 0) return false
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        return x >= loc[0] && x <= loc[0] + v.width && y >= loc[1] && y <= loc[1] + v.height
    }

    private fun placeAt(slots: MutableList<String?>, from: Int, target: Int, allowFolders: Boolean) {
        if (from !in slots.indices || target !in slots.indices) return
        val src = slots[from] ?: return
        val tgt = slots[target]
        if (tgt == null) {
            slots[target] = src
            slots[from] = null
            return
        }
        if (allowFolders && isFolder(tgt) && !isFolder(src)) {
            val keys = cb.folderKeys(folderIdOf(tgt))
            if (keys != null && keys.size < HS_MAX_FOLDER) {
                keys.add(src)
                slots[from] = null
            } else {
                java.util.Collections.swap(slots, from, target)
            }
            return
        }
        if (allowFolders && !isFolder(src) && !isFolder(tgt)) {
            val id = System.currentTimeMillis().toString()
            cb.createFolder(id, tgt, src)
            slots[target] = HS_FOLDER_PREFIX + id
            slots[from] = null
            cb.onFolderCreated(id)
            return
        }
        if (!allowFolders && (isFolder(src) || isFolder(tgt))) return
        java.util.Collections.swap(slots, from, target)
    }

    private fun dragHelper(
        adapter: AppAdapter,
        pageIdx: Int,
        rv: RecyclerView,
        guide: GridGuide?,
        allowFolders: Boolean
    ): ItemTouchHelper {
        val cbh = object : ItemTouchHelper.Callback() {
            override fun isLongPressDragEnabled(): Boolean = true
            override fun isItemViewSwipeEnabled(): Boolean = false

            override fun getMovementFlags(recycler: RecyclerView, vh: RecyclerView.ViewHolder): Int {
                if (adapter.itemAt(vh.bindingAdapterPosition) == null) return 0
                return makeMovementFlags(
                    ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0
                )
            }

            override fun onMove(recycler: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false
            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}

            override fun onChildDraw(
                c: Canvas, recyclerView: RecyclerView, vh: RecyclerView.ViewHolder,
                dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
            ) {
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && isCurrentlyActive) {
                    dropX = dragStartX + dX + vh.itemView.width / 2f
                    dropY = dragStartY + dY + vh.itemView.height / 2f
                    if (pageIdx >= 0) checkEdgePage(dropX)
                }
                super.onChildDraw(c, recyclerView, vh, dX, dY, actionState, isCurrentlyActive)
            }

            override fun onSelectedChanged(vh: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(vh, actionState)
                try {
                    if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && vh != null) {
                        dragLock = true
                        guide?.visible = true
                        removeZone.visibility = View.VISIBLE
                        vh.itemView.alpha = 0.7f
                        val loc = IntArray(2)
                        vh.itemView.getLocationOnScreen(loc)
                        dragStartX = loc[0].toFloat()
                        dragStartY = loc[1].toFloat()
                        dropX = dragStartX + vh.itemView.width / 2f
                        dropY = dragStartY + vh.itemView.height / 2f
                    }
                } catch (e: Exception) {
                    Log.e(HS_TAG, "dragStart", e)
                }
            }

            override fun clearView(recycler: RecyclerView, vh: RecyclerView.ViewHolder) {
                super.clearView(recycler, vh)
                try {
                    vh.itemView.alpha = 1f
                    dragLock = false
                    guide?.visible = false
                    val removing = removeZone.visibility == View.VISIBLE && inside(removeZone, dropX, dropY)
                    removeZone.visibility = View.GONE
                    val local = vh.bindingAdapterPosition
                    if (local != RecyclerView.NO_POSITION) {
                        if (pageIdx < 0) {
                            handleDrop(dockSlots, local, rv, removing, allowFolders)
                        } else {
                            val base = pageIdx * HS_PAGE_CELLS
                            if (removing) {
                                val idx = base + local
                                val src = homeSlots.getOrNull(idx)
                                if (src != null) {
                                    homeSlots[idx] = null
                                    if (isFolder(src)) cb.deleteFolder(folderIdOf(src))
                                }
                            } else if (inside(rv, dropX, dropY)) {
                                val tLocal = childPosAt(rv, dropX, dropY)
                                if (tLocal != RecyclerView.NO_POSITION) {
                                    placeAt(homeSlots, base + local, base + tLocal, true)
                                }
                            }
                        }
                    }
                    trimEmptyPages()
                    cb.onPersist()
                    rv.post { cb.onPagesChanged() }
                } catch (e: Exception) {
                    Log.e(HS_TAG, "clearView", e)
                }
            }
        }
        return ItemTouchHelper(cbh)
    }

    private fun handleDrop(slots: MutableList<String?>, local: Int, rv: RecyclerView, removing: Boolean, allowFolders: Boolean) {
        if (local !in slots.indices) return
        val src = slots[local] ?: return
        if (removing) {
            slots[local] = null
            if (isFolder(src)) cb.deleteFolder(folderIdOf(src))
        } else if (inside(rv, dropX, dropY)) {
            val target = childPosAt(rv, dropX, dropY)
            if (target != RecyclerView.NO_POSITION && target != local && target < slots.size) {
                placeAt(slots, local, target, allowFolders)
            }
        }
    }

    /** Drag ke dauran page edge par pahunchne se page change. */
    private fun checkEdgePage(rawX: Float) {
        try {
            if (!dragLock) return
            val w = layer.width
            val now = System.currentTimeMillis()
            if (now - lastEdgeTime < 700) return
            if (rawX < dp(24)) {
                if (currentPage > 0) {
                    lastEdgeTime = now
                    goToPage(currentPage - 1, true, true)
                }
            } else if (rawX > w - dp(24)) {
                if (currentPage < pageCount - 1) {
                    lastEdgeTime = now
                    goToPage(currentPage + 1, true, true)
                } else if (pageCount < HS_MAX_PAGES) {
                    lastEdgeTime = now
                    addPage()
                    goToPage(currentPage + 1, true, true)
                }
            }
        } catch (e: Exception) {
            Log.e(HS_TAG, "edgePage", e)
        }
    }

    // ---------------- Hit tests (long-press, drawer item) ----------------

    fun onItem(rv: RecyclerView, x: Float, y: Float): Boolean {
        if (!inside(rv, x, y)) return false
        val loc = IntArray(2)
        rv.getLocationOnScreen(loc)
        val child = rv.findChildViewUnder(x - loc[0], y - loc[1]) ?: return false
        val pos = rv.getChildAdapterPosition(child)
        val adapter = rv.adapter as? AppAdapter ?: return false
        return pos != RecyclerView.NO_POSITION && adapter.itemAt(pos) != null
    }

    fun isOnAnyItem(x: Float, y: Float): Boolean {
        for (g in pageGrids) if (onItem(g, x, y)) return true
        return onItem(dock, x, y)
    }
}
