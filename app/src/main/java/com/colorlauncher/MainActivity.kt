package com.colorlauncher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.TargetApi
import android.app.Activity
import android.app.ActivityOptions
import android.app.Dialog
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EdgeEffect
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

class AppItem(val label: String, val key: String, val intent: Intent, val icon: Bitmap)

class Folder(var name: String, val keys: MutableList<String>)

class SearchIcon(ctx: Context) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        val s = minOf(width, height).toFloat()
        paint.strokeWidth = s * 0.08f
        val r = s * 0.2f
        val cx = width / 2f - s * 0.06f
        val cy = height / 2f - s * 0.06f
        canvas.drawCircle(cx, cy, r, paint)
        val a = r * 0.7071f
        canvas.drawLine(cx + a, cy + a, cx + a + s * 0.17f, cy + a + s * 0.17f, paint)
    }
}

class MoreIcon(ctx: Context) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    override fun onDraw(canvas: Canvas) {
        val s = minOf(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val r = s * 0.06f
        canvas.drawCircle(cx, cy - s * 0.2f, r, paint)
        canvas.drawCircle(cx, cy, r, paint)
        canvas.drawCircle(cx, cy + s * 0.2f, r, paint)
    }
}

/** Icon ko shape ke hisaab se clip karta hai (0 squircle, 1 circle, 2 square). */
class ShapeIcon(ctx: Context) : ImageView(ctx) {
    var shape = 0
        set(v) {
            field = v
            invalidate()
        }
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) {
            super.onDraw(canvas)
            return
        }
        path.reset()
        val rect = RectF(0f, 0f, w, h)
        when (shape) {
            1 -> path.addOval(rect, Path.Direction.CW)
            2 -> path.addRect(rect, Path.Direction.CW)
            else -> path.addRoundRect(rect, w * 0.3f, h * 0.3f, Path.Direction.CW)
        }
        val save = canvas.save()
        canvas.clipPath(path)
        super.onDraw(canvas)
        canvas.restoreToCount(save)
    }
}

/** Icon se full screen tak rounded-corner sheet expand hoti hai (sirf open animation). */
class LaunchOverlay(ctx: Context) : View(ctx) {
    private val sheet = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val from = RectF()
    private val cur = RectF()
    private val dst = RectF()
    private var color = Color.DKGRAY
    private var icon: Bitmap? = null
    private var iconCorner = 0f
    private val screenCorner = 36f * resources.displayMetrics.density
    var t = 0f
        set(v) {
            field = v
            invalidate()
        }

    fun prepare(l: Float, tp: Float, r: Float, b: Float, c: Int, bmp: Bitmap) {
        from.set(l, tp, r, b)
        color = c
        icon = bmp
        iconCorner = (r - l) * 0.28f
        t = 0f
    }

    override fun onDraw(canvas: Canvas) {
        val tt = t.coerceIn(0f, 1f)
        val w = width.toFloat()
        val h = height.toFloat()
        cur.set(
            from.left * (1f - tt),
            from.top * (1f - tt),
            from.right + (w - from.right) * tt,
            from.bottom + (h - from.bottom) * tt
        )
        sheet.color = color
        sheet.alpha = (minOf(1f, tt * 6f) * 255).toInt()
        val rad = iconCorner + (screenCorner - iconCorner) * tt
        canvas.drawRoundRect(cur, rad, rad, sheet)
        val bmp = icon ?: return
        val half = from.width() / 2f
        dst.set(cur.centerX() - half, cur.centerY() - half, cur.centerX() + half, cur.centerY() + half)
        canvas.drawBitmap(bmp, null, dst, iconPaint)
    }
}

/** Top edge ke liye no-op: upar khinchne par koi stretch nahi hoga. */
private class NoEdge(ctx: Context) : EdgeEffect(ctx) {
    override fun setSize(width: Int, height: Int) {}
    override fun isFinished(): Boolean = true
    override fun finish() {}
    override fun onPull(deltaDistance: Float) {}
    override fun onPull(deltaDistance: Float, displacement: Float) {}
    override fun onRelease() {}
    override fun onAbsorb(velocity: Int) {}
    override fun draw(canvas: Canvas): Boolean = false
}

/** RecyclerView jiska top/bottom fade scroll ke saath smoothly badhta hai. */
class FadeRecycler(ctx: Context) : RecyclerView(ctx) {
    override fun getTopFadingEdgeStrength(): Float {
        val len = maxOf(1, verticalFadingEdgeLength).toFloat()
        return (computeVerticalScrollOffset() / len).coerceIn(0f, 1f)
    }

    override fun getBottomFadingEdgeStrength(): Float {
        val len = maxOf(1, verticalFadingEdgeLength).toFloat()
        val rest = computeVerticalScrollRange() - computeVerticalScrollOffset() - computeVerticalScrollExtent()
        return (rest / len).coerceIn(0f, 1f)
    }
}

/** Sabse upar wali touch layer: drawer swipe, page swipe aur khali jagah par long-press. */
class GestureRoot(ctx: Context, private val host: Host) : FrameLayout(ctx) {

    interface Host {
        fun canStartDrag(dy: Float, rawStartY: Float): Boolean
        fun onDragStart()
        fun onDrag(dy: Float)
        fun onDragEnd(vy: Float)
        fun onEmptyLongPress(rawX: Float, rawY: Float)
    }

    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var rawDownX = 0f
    private var rawDownY = 0f
    private var dragging = false
    private var longFired = false
    private var vt: VelocityTracker? = null

    private val longPressRunnable = Runnable {
        if (!dragging && !longFired) {
            longFired = true
            host.onEmptyLongPress(rawDownX, rawDownY)
        }
    }

    private fun cancelLong() = removeCallbacks(longPressRunnable)

    private fun track(e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x
                downY = e.y
                rawDownX = e.rawX
                rawDownY = e.rawY
                dragging = false
                longFired = false
                cancelLong()
                postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                vt?.recycle()
                vt = VelocityTracker.obtain()
                vt?.addMovement(e)
            }
            MotionEvent.ACTION_MOVE -> {
                vt?.addMovement(e)
                val dy = e.y - downY
                val dx = e.x - downX
                if (!dragging) {
                    if (abs(dy) > slop || abs(dx) > slop) cancelLong()
                    if (abs(dy) > slop && abs(dy) > abs(dx) * 1.5f && host.canStartDrag(dy, rawDownY)) {
                        cancelLong()
                        dragging = true
                        host.onDragStart()
                        host.onDrag(dy)
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                } else {
                    host.onDrag(dy)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cancelLong()
                if (dragging) {
                    vt?.addMovement(e)
                    vt?.computeCurrentVelocity(1000)
                    val cancel = e.actionMasked == MotionEvent.ACTION_CANCEL
                    host.onDragEnd(if (cancel) 0f else (vt?.yVelocity ?: 0f))
                }
                dragging = false
                vt?.recycle()
                vt = null
            }
        }
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        track(e)
        return dragging
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        track(e)
        return true
    }
}

class MainActivity : Activity(), GestureRoot.Host {

    companion object {
        private const val COLS = 4
        private const val PAGE_CELLS = 24
        private const val DOCK_SLOTS = 4
        private const val MAX_PAGES = 10
        private const val MAX_FOLDER = 12
        private const val FOLDER_PREFIX = "folder:"
        private const val HOST_ID = 1024
        private const val REQ_BIND = 11
        private const val REQ_CONFIG = 12
        private const val CACHE_VER = "#sq2"
        private val INTRO_ANGLES = intArrayOf(-135, -60, 210, 60, -165, 120, -30, 150)
    }

    private val bg = Executors.newSingleThreadExecutor()
    private lateinit var root: GestureRoot
    private lateinit var stage: FrameLayout
    private lateinit var overlay: LaunchOverlay
    private lateinit var home: LinearLayout
    private lateinit var clock: TextClock
    private lateinit var date: TextClock
    private lateinit var widgetBox: LinearLayout
    private lateinit var drawer: FrameLayout
    private lateinit var drawerList: RecyclerView
    private lateinit var pageHost: FrameLayout
    private lateinit var pageRow: LinearLayout
    private lateinit var dock: RecyclerView
    private lateinit var dockHolder: LinearLayout
    private lateinit var pageDots: LinearLayout
    private lateinit var hint: TextView
    private lateinit var header: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var searchBox: EditText
    private lateinit var removeZone: TextView
    private lateinit var dockAdapter: AppAdapter
    private lateinit var drawerAdapter: AppAdapter

    /** Har page ka apna grid (RecyclerView). */
    private val pageGrids = mutableListOf<RecyclerView>()
    private val pageAdapters = mutableListOf<AppAdapter>()
    private val pageGuides = mutableListOf<GridGuide>()

    private lateinit var folderLayer: FrameLayout
    private lateinit var folderScrim: View
    private lateinit var folderCard: LinearLayout
    private lateinit var folderTitle: TextView
    private lateinit var folderGrid: RecyclerView
    private lateinit var folderAdapter: AppAdapter

    private val widgetHost by lazy { AppWidgetHost(this, HOST_ID) }
    private val widgetMgr by lazy { AppWidgetManager.getInstance(this) }
    private var pendingWidgetId = -1

    private var allApps: List<AppItem> = emptyList()
    private var appsLoaded = false
    private var loadedPack = ""
    private var dragLock = false
    private var drawerGapH = 8
    private var drawerGapV = 12

    private var fxBlur = true
    private var fxIntro = true
    private var pageAnim = true
    private var introPending = true
    private var lastHomeBlur = -1
    private var lastStageBlur = -1

    private var dropX = 0f
    private var dropY = 0f
    private var dragStartX = 0f
    private var dragStartY = 0f

    /** Home ke saare slots (pages * 24). null = khali cell. */
    private val homeSlots = MutableList<String?>(PAGE_CELLS) { null }
    private val dockSlots = MutableList<String?>(DOCK_SLOTS) { null }
    private val folders = LinkedHashMap<String, Folder>()
    private val prefs by lazy { getSharedPreferences("home", MODE_PRIVATE) }

    private var pageCount = 1
    private var currentPage = 0
    private var pageOffset = 0f
    private var pageAnimator: ValueAnimator? = null
    private var pageDragging = false
    private var pageDragStartX = 0f
    private var pageDragBase = 0f

    private var folderOpen = false
    private var currentFolderId: String? = null
    private var folderF = 0f
    private var folderAnim: ValueAnimator? = null

    private var progress = 0f
    private var dragBase = 0f
    private var anim: SpringAnimation? = null
    private var pendingReturn = false
    private var pivX = 0f
    private var pivY = 0f

    private var searching = false
    private var depth = 0f
    private var launchAnim: ValueAnimator? = null
    private var lastIconBlur = -1
    private var iconBlurOn = true
    private var listBottomGap = 0
    private val emphasized = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)

    private val homeBlurFx: Array<RenderEffect?> by lazy {
        Array(13) { s -> if (s == 0) null else blurEffect(dp(18) * s / 12f) }
    }
    private val stageBlurFx: Array<RenderEffect?> by lazy {
        Array(13) { s -> if (s == 0) null else blurEffect(dp(20) * s / 12f) }
    }
    private val iconBlurFx: Array<RenderEffect?> by lazy {
        Array(9) { s -> if (s == 0) null else blurEffect(dp(8) * s / 8f) }
    }

    private val userPresentReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == Intent.ACTION_USER_PRESENT) {
                introPending = true
                runIntroIfNeeded()
            }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /** Grid par drag ke time dashed cells dikhata hai. */
    private inner class GridGuide : RecyclerView.ItemDecoration() {
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
            val child = parent.getChildAt(0) ?: return
            val rowH = child.height.toFloat()
            if (rowH <= 0f) return
            val left = parent.paddingLeft.toFloat()
            val right = (parent.width - parent.paddingRight).toFloat()
            val cw = (right - left) / COLS
            val rows = PAGE_CELLS / COLS
            val inset = dp(4).toFloat()
            for (row in 0 until rows) {
                for (col in 0 until COLS) {
                    val top = parent.paddingTop + row * rowH
                    r.set(
                        left + col * cw + inset,
                        top + inset,
                        left + (col + 1) * cw - inset,
                        top + rowH - inset
                    )
                    c.drawRoundRect(r, dp(16).toFloat(), dp(16).toFloat(), paint)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupWindow()
        enableHighRefresh()

        loadPages()
        loadSlots("dock", dockSlots)
        loadFolders()

        dockAdapter = AppAdapter(false, { v, i -> launch(v, i) }, null)
        drawerAdapter = AppAdapter(true, { v, i -> launch(v, i) }, { showMenu(it, "drawer") })

        clock = TextClock(this).apply {
            format12Hour = "h:mm"
            format24Hour = "H:mm"
            textSize = 64f
            setTextColor(0xFFFFFFFF.toInt())
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        date = TextClock(this).apply {
            format12Hour = "EEE, d MMMM"
            format24Hour = "EEE, d MMMM"
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER_HORIZONTAL
        }
        widgetBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        hint = TextView(this).apply {
            text = "Long-press an app to add it here."
            textSize = 13f
            setTextColor(0xB3FFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(32), dp(24), 0)
        }

        // Pages ka container: sab pages ek row mein, translationX se slide
        pageRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        pageHost = FrameLayout(this).apply {
            clipChildren = true
            addView(pageRow, FrameLayout.LayoutParams(MATCH, MATCH))
        }
        rebuildPages()

        pageDots = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        dock = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, DOCK_SLOTS)
            adapter = dockAdapter
            overScrollMode = View.OVER_SCROLL_NEVER
            itemAnimator = null
            background = GradientDrawable().apply {
                cornerRadius = dp(30).toFloat()
                setColor(0x33FFFFFF)
            }
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        dockHolder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(dock, LinearLayout.LayoutParams(MATCH, WRAP))
        }

        home = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(clock, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(date, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(widgetBox, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(hint, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(pageHost, LinearLayout.LayoutParams(MATCH, 0, 1f))
            addView(pageDots, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(6) })
            addView(dockHolder, LinearLayout.LayoutParams(MATCH, WRAP).apply {
                setMargins(dp(14), 0, dp(14), dp(14))
            })
        }

        drawerList = FadeRecycler(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, COLS)
            adapter = drawerAdapter
            setHasFixedSize(true)
            setItemViewCacheSize(24)
            itemAnimator = null
            clipToPadding = true
            overScrollMode = View.OVER_SCROLL_ALWAYS
            edgeEffectFactory = object : RecyclerView.EdgeEffectFactory() {
                override fun createEdgeEffect(view: RecyclerView, direction: Int): EdgeEffect =
                    if (direction == RecyclerView.EdgeEffectFactory.DIRECTION_BOTTOM) EdgeEffect(view.context)
                    else NoEdge(view.context)
            }
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(56))
            setPadding(dp(8), dp(64), dp(8), 0)
        }
        drawerList.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                val n = state.itemCount
                if (n == 0) return
                val pos = parent.getChildAdapterPosition(view)
                if (pos == RecyclerView.NO_POSITION) return
                val h = dp(drawerGapH) / 2
                val v = dp(drawerGapV) / 2
                outRect.set(h, v, h, v)
                val rem = n % COLS
                val lastRowStart = n - (if (rem == 0) COLS else rem)
                if (pos >= lastRowStart) outRect.bottom += listBottomGap
            }
        })
        drawer = FrameLayout(this).apply {
            addView(drawerList, MATCH, MATCH)
            visibility = View.INVISIBLE
        }

        titleView = TextView(this).apply {
            text = "All apps"
            textSize = 26f
            setTextColor(0xFFFFFFFF.toInt())
            typeface = Typeface.DEFAULT_BOLD
        }
        searchBox = EditText(this).apply {
            hint = "Search apps"
            setHintTextColor(0x99FFFFFF.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 18f
            setSingleLine(true)
            background = null
            visibility = View.GONE
            doAfterTextChanged { filterApps(it?.toString() ?: "") }
        }
        val searchBtn = SearchIcon(this).apply {
            setOnClickListener { setSearchMode(!searching) }
        }
        val moreBtn = MoreIcon(this).apply {
            setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        }
        header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), 0, dp(12), 0)
            addView(titleView, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(searchBox, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(searchBtn, LinearLayout.LayoutParams(dp(44), dp(44)))
            addView(moreBtn, LinearLayout.LayoutParams(dp(44), dp(44)))
        }
        drawer.addView(header, FrameLayout.LayoutParams(MATCH, dp(56), Gravity.TOP))

        removeZone = TextView(this).apply {
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

        folderScrim = View(this).apply {
            setBackgroundColor(0x88000000.toInt())
            alpha = 0f
        }
        folderTitle = TextView(this).apply {
            textSize = 20f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        folderAdapter = AppAdapter(
            true,
            { v, i -> launch(v, i) },
            { showFolderItemMenu(it) }
        )
        folderGrid = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, 3)
            adapter = folderAdapter
            overScrollMode = View.OVER_SCROLL_NEVER
            itemAnimator = null
            clipToPadding = false
        }
        folderCard = LinearLayout(this).apply {
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
            addView(folderTitle, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(folderGrid, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) })
        }
        folderLayer = FrameLayout(this).apply {
            visibility = View.GONE
            setOnClickListener { closeFolder() }
            addView(folderScrim, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(folderCard, FrameLayout.LayoutParams(dp(300), WRAP, Gravity.CENTER))
        }

        stage = FrameLayout(this)
        stage.addView(home, MATCH, MATCH)
        stage.addView(drawer, MATCH, MATCH)
        stage.addView(removeZone, FrameLayout.LayoutParams(MATCH, dp(56), Gravity.TOP).apply {
            setMargins(dp(16), dp(8), dp(16), 0)
        })
        root = GestureRoot(this, this)
        root.addView(stage, MATCH, MATCH)
        root.addView(folderLayer, MATCH, MATCH)

        overlay = LaunchOverlay(this).apply {
            visibility = View.GONE
            isClickable = true
        }
        root.addView(overlay, MATCH, MATCH)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            applyInsets(bars.top, bars.bottom, ime.bottom)
            insets
        }

        setContentView(root)
        setProgress(0f)
        applyClock()
        applyDockStyle()
        attachDrags()
        restoreWidgets()

        registerReceiver(userPresentReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))

        showCachedApps()
        loadApps()
    }

    private fun setupWindow() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
    }

    private fun applyInsets(top: Int, bottom: Int, ime: Int) {
        home.setPadding(0, top + dp(24), 0, bottom)
        (header.layoutParams as FrameLayout.LayoutParams).topMargin = top
        header.requestLayout()
        (removeZone.layoutParams as FrameLayout.LayoutParams).topMargin = top + dp(8)
        drawerList.setPadding(dp(8), top + dp(64), dp(8), ime)
        listBottomGap = bottom + dp(24)
        drawerList.invalidateItemDecorations()
    }

    @Suppress("DEPRECATION")
    private fun enableHighRefresh() {
        try {
            val disp = windowManager.defaultDisplay
            val cur = disp.mode
            val best = disp.supportedModes
                .filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
                .maxByOrNull { it.refreshRate }
            if (best != null) {
                val lp = window.attributes
                lp.preferredDisplayModeId = best.modeId
                lp.preferredRefreshRate = best.refreshRate
                window.attributes = lp
            }
        } catch (e: Exception) {
        }
    }

    // ---------- pages ----------
    private fun loadPages() {
        val count = prefs.getInt("home_pages", 1).coerceIn(1, MAX_PAGES)
        pageCount = count
        val saved = prefs.getString("home", "")!!.split("|")
        homeSlots.clear()
        for (i in 0 until count * PAGE_CELLS) {
            homeSlots.add(saved.getOrNull(i)?.takeIf { it.isNotEmpty() })
        }
    }

    private fun loadSlots(name: String, target: MutableList<String?>) {
        val parts = prefs.getString(name, "")!!.split("|")
        for (i in target.indices) {
            target[i] = parts.getOrNull(i)?.takeIf { it.isNotEmpty() }
        }
    }

    /** Pages ka UI banata hai (pageCount ke hisaab se). */
    private fun rebuildPages() {
        pageRow.removeAllViews()
        pageGrids.clear()
        pageAdapters.clear()
        pageGuides.clear()
        for (p in 0 until pageCount) {
            val adapter = AppAdapter(true, { v, i -> launch(v, i) }, null)
            val guide = GridGuide()
            val grid = RecyclerView(this).apply {
                layoutManager = GridLayoutManager(this@MainActivity, COLS)
                this.adapter = adapter
                overScrollMode = View.OVER_SCROLL_NEVER
                itemAnimator = null
                clipToPadding = false
                setPadding(dp(8), dp(16), dp(8), 0)
                addItemDecoration(guide)
            }
            pageGrids.add(grid)
            pageAdapters.add(adapter)
            pageGuides.add(guide)
            pageRow.addView(grid, LinearLayout.LayoutParams(0, MATCH, 1f))
        }
        pageRow.layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        // Har page ko width = pageHost width chahiye, isliye layout pass ke baad set karte hain
        pageHost.post { layoutPages() }
        pageDots.removeAllViews()
        for (p in 0 until pageCount) {
            pageDots.addView(View(this).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x66FFFFFF) }
            }, LinearLayout.LayoutParams(dp(6), dp(6)).apply { marginStart = dp(4); marginEnd = dp(4) })
        }
        pageDots.visibility = if (pageCount > 1) View.VISIBLE else View.GONE
        updateDots()
        attachDrags()
    }

    /** Har page ki width pageHost ke barabar, aur current page ka offset. */
    private fun layoutPages() {
        val w = pageHost.width
        if (w <= 0) return
        for (grid in pageGrids) {
            val lp = grid.layoutParams as LinearLayout.LayoutParams
            lp.width = w
            grid.layoutParams = lp
        }
        pageRow.requestLayout()
        setPageOffset(-currentPage * w.toFloat())
    }

    private fun pageWidth(): Float = pageHost.width.toFloat().coerceAtLeast(1f)

    private fun setPageOffset(x: Float) {
        pageOffset = x
        pageRow.translationX = x
    }

    private fun updateDots() {
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
    }

    /** Current page change. animated=true par slide, nahi to seedha jump. */
    private fun goToPage(p: Int, animated: Boolean) {
        val target = p.coerceIn(0, pageCount - 1)
        currentPage = target
        updateDots()
        val end = -target * pageWidth()
        pageAnimator?.cancel()
        if (animated && pageAnim) {
            val a = ValueAnimator.ofFloat(pageOffset, end)
            a.duration = 280
            a.interpolator = emphasized
            a.addUpdateListener { setPageOffset(it.animatedValue as Float) }
            pageAnimator = a
            a.start()
        } else {
            setPageOffset(end)
        }
    }

    /** Drag ke time edge par page badalta hai. */
    private fun checkEdgePage(rawX: Float) {
        if (!dragLock) return
        val w = root.width
        val now = System.currentTimeMillis()
        if (now - lastEdgeTime < 700) return
        if (rawX < dp(24)) {
            if (currentPage > 0) {
                lastEdgeTime = now
                goToPage(currentPage - 1, true)
            }
        } else if (rawX > w - dp(24)) {
            if (currentPage < pageCount - 1) {
                lastEdgeTime = now
                goToPage(currentPage + 1, true)
            } else if (pageCount < MAX_PAGES) {
                lastEdgeTime = now
                addPage()
                goToPage(currentPage + 1, true)
            }
        }
    }

    private var lastEdgeTime = 0L

    /** Naya khali page (home slots mein 24 aur cells). */
    private fun addPage() {
        pageCount++
        for (i in 0 until PAGE_CELLS) homeSlots.add(null)
        prefs.edit().putInt("home_pages", pageCount).apply()
        rebuildPages()
        refreshHome()
    }

    /** Khali pages ko end se hata deta hai (sirf bina items wale last pages). */
    private fun trimEmptyPages() {
        while (pageCount > 1) {
            val lastStart = (pageCount - 1) * PAGE_CELLS
            val empty = (lastStart until lastStart + PAGE_CELLS).all { homeSlots[it] == null }
            if (!empty) break
            repeat(PAGE_CELLS) { homeSlots.removeAt(homeSlots.size - 1) }
            pageCount--
        }
        if (currentPage >= pageCount) currentPage = pageCount - 1
        prefs.edit().putInt("home_pages", pageCount).apply()
    }

    // ---------- dock style (size + position) ----------
    private fun applyDockStyle() {
        val sp = getSharedPreferences("settings", MODE_PRIVATE)
        val size = sp.getInt("dock_size", 1)
        val pos = sp.getInt("dock_pos", 0)
        val pad = when (size) { 0 -> 4; 2 -> 10; else -> 6 }
        dock.setPadding(dp(pad), dp(pad), dp(pad), dp(pad))
        val lp = dockHolder.layoutParams as? LinearLayout.LayoutParams
        // Position: Above ho to dock ke neeche thodi jagah
        val bottomMargin = if (pos == 1) dp(40) else dp(14)
        dockHolder.setPadding(0, 0, 0, 0)
        (dock.layoutParams as LinearLayout.LayoutParams).setMargins(0, 0, 0, 0)
        lp?.bottomMargin = bottomMargin
        dockHolder.layoutParams = lp ?: LinearLayout.LayoutParams(MATCH, WRAP).apply {
            setMargins(dp(14), 0, dp(14), bottomMargin)
        }
    }

    /** Dock ke icon size ko settings se decide karta hai. */
    private fun dockIconDp(): Int = when (getSharedPreferences("settings", MODE_PRIVATE).getInt("dock_size", 1)) {
        0 -> 44
        2 -> 72
        else -> 58
    }

    private fun attachDrags() {
        for (i in pageGrids.indices) {
            dragHelper(pageAdapters[i], i, pageGrids[i], pageGuides[i], true)
                .attachToRecyclerView(pageGrids[i])
        }
        dragHelper(dockAdapter, -1, dock, null, false).attachToRecyclerView(dock)
    }

    // ---------- blur ----------
    @TargetApi(31)
    private fun blurEffect(r: Float): RenderEffect =
        RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP)

    private fun applyHomeBlur(p: Float) {
        if (Build.VERSION.SDK_INT < 31) return
        val step = if (!fxBlur) 0 else (p.coerceIn(0f, 1f) * 12f + 0.5f).toInt()
        if (step == lastHomeBlur) return
        lastHomeBlur = step
        home.setRenderEffect(homeBlurFx[step])
    }

    private fun applyStageBlur(f: Float) {
        if (Build.VERSION.SDK_INT < 31) return
        val step = if (!fxBlur) 0 else (f.coerceIn(0f, 1f) * 12f + 0.5f).toInt()
        if (step == lastStageBlur) return
        lastStageBlur = step
        stage.setRenderEffect(stageBlurFx[step])
    }

    @TargetApi(31)
    private fun setIconBlur(step: Int) {
        val fx = iconBlurFx[step]
        for (g in pageGrids) g.setRenderEffect(fx)
        dock.setRenderEffect(fx)
        drawerList.setRenderEffect(fx)
    }

    // ---------- unlock wave ----------
    private fun runIntroIfNeeded() {
        if (!introPending) return
        if (progress > 0.01f || pendingReturn || folderOpen) return
        introPending = false
        if (!fxIntro) return
        window.decorView.post { playIntro() }
    }

    /** Neeche se halka slide + fade, row ke hisab se wave (delay). */
    private fun playIntro() {
        val page = pageGrids.getOrNull(currentPage) ?: return
        val items = ArrayList<Pair<View, Int>>()
        for (i in 0 until page.childCount) {
            val v = page.getChildAt(i)
            val pos = page.getChildAdapterPosition(v)
            if (pos != RecyclerView.NO_POSITION) items.add(v to pos / COLS)
        }
        for (i in 0 until dock.childCount) items.add(dock.getChildAt(i) to 0)
        val offset = dp(48).toFloat()
        val ease = PathInterpolator(0.2f, 0.8f, 0.2f, 1f)
        for ((v, _) in items) {
            v.animate().cancel()
            v.translationY = offset
            v.alpha = 0f
            v.scaleX = 0.85f
            v.scaleY = 0.85f
        }
        for ((v, row) in items) {
            v.animate()
                .translationY(0f)
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(row * 45L)
                .setDuration(450)
                .setInterpolator(ease)
                .start()
        }
    }

    // ---------- drag and drop ----------
    private fun isFolder(k: String?) = k != null && k.startsWith(FOLDER_PREFIX)

    private fun folderIdOf(k: String) = k.removePrefix(FOLDER_PREFIX)

    /** Slots ka wo list jo is RecyclerView ke liye hai. Dock = dockSlots, page = homeSlots ka slice. */
    private fun globalIndex(pageIdx: Int, local: Int) = pageIdx * PAGE_CELLS + local

    private fun childPosAt(rv: RecyclerView, x: Float, y: Float): Int {
        val loc = IntArray(2)
        rv.getLocationOnScreen(loc)
        val child = rv.findChildViewUnder(x - loc[0], y - loc[1]) ?: return RecyclerView.NO_POSITION
        return rv.getChildAdapterPosition(child)
    }

    /**
     * Drop par decide. from/target dono ek hi list (slots) ke index hain.
     * Home par khali jagah par move, dusre icon par swap, app+app par folder.
     */
    private fun placeAt(slots: MutableList<String?>, from: Int, target: Int, allowFolders: Boolean) {
        val src = slots[from] ?: return
        val tgt = slots[target]
        if (tgt == null) {
            slots[target] = src
            slots[from] = null
            return
        }
        if (allowFolders && isFolder(tgt) && !isFolder(src)) {
            val f = folders[folderIdOf(tgt)]
            if (f != null && f.keys.size < MAX_FOLDER) {
                f.keys.add(src)
                slots[from] = null
            } else {
                Collections.swap(slots, from, target)
            }
            return
        }
        if (allowFolders && !isFolder(src) && !isFolder(tgt)) {
            val id = System.currentTimeMillis().toString()
            folders[id] = Folder("Folder", mutableListOf(tgt, src))
            slots[target] = FOLDER_PREFIX + id
            slots[from] = null
            return
        }
        if (!allowFolders && (isFolder(src) || isFolder(tgt))) return
        Collections.swap(slots, from, target)
    }

    /**
     * pageIdx = -1 matlab dock. Home ke liye local index ko global (pageIdx*24 + local) mein badal ke
     * homeSlots par kaam karta hai.
     */
    private fun dragHelper(
        adapter: AppAdapter,
        pageIdx: Int,
        rv: RecyclerView,
        guide: GridGuide?,
        allowFolders: Boolean
    ): ItemTouchHelper {
        val cb = object : ItemTouchHelper.Callback() {
            override fun isLongPressDragEnabled(): Boolean = true
            override fun isItemViewSwipeEnabled(): Boolean = false

            override fun getMovementFlags(recycler: RecyclerView, vh: RecyclerView.ViewHolder): Int {
                if (adapter.itemAt(vh.bindingAdapterPosition) == null) return 0
                return makeMovementFlags(
                    ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
                    0
                )
            }

            override fun onMove(
                recycler: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder
            ): Boolean = false

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
                    rv.invalidate()
                }
            }

            override fun clearView(recycler: RecyclerView, vh: RecyclerView.ViewHolder) {
                super.clearView(recycler, vh)
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
                        // Home: page ke local index ko global index mein badlo
                        val base = pageIdx * PAGE_CELLS
                        if (removing) {
                            val src = homeSlots.getOrNull(base + local)
                            if (src != null) {
                                homeSlots[base + local] = null
                                if (isFolder(src)) folders.remove(folderIdOf(src))
                            }
                        } else if (inside(rv, dropX, dropY)) {
                            val tLocal = childPosAt(rv, dropX, dropY)
                            if (tLocal != RecyclerView.NO_POSITION) {
                                val from = base + local
                                val to = base + tLocal
                                if (to != from && to < homeSlots.size) {
                                    placeAt(homeSlots, from, to, true)
                                }
                            }
                        }
                    }
                }
                trimEmptyPages()
                persist()
                rv.post { refreshHome() }
            }
        }
        return ItemTouchHelper(cb)
    }

    private fun handleDrop(slots: MutableList<String?>, local: Int, rv: RecyclerView, removing: Boolean, allowFolders: Boolean) {
        if (local !in slots.indices) return
        val src = slots[local] ?: return
        if (removing) {
            slots[local] = null
            if (isFolder(src)) folders.remove(folderIdOf(src))
        } else if (inside(rv, dropX, dropY)) {
            val target = childPosAt(rv, dropX, dropY)
            if (target != RecyclerView.NO_POSITION && target != local && target < slots.size) {
                placeAt(slots, local, target, allowFolders)
            }
        }
    }

    private fun applyIconStyle() {
        val s = readIconStyle(this)
        for (a in pageAdapters) a.applyStyle(s)
        dockAdapter.applyStyle(s.copy(sizeDp = dockIconDp()))
        drawerAdapter.applyStyle(s)
        folderAdapter.applyStyle(s)
        drawerGapH = s.gapH
        drawerGapV = s.gapV
        drawerList.invalidateItemDecorations()
    }

    private fun applyClock() {
        val on = prefs.getBoolean("clock", true)
        clock.visibility = if (on) View.VISIBLE else View.GONE
        date.visibility = if (on) View.VISIBLE else View.GONE
    }

    private fun loadFolders() {
        folders.clear()
        prefs.getString("folders", "")!!.split("\n").filter { it.isNotEmpty() }.forEach { line ->
            val p = line.split("\t")
            if (p.size < 3) return@forEach
            val keys = p[2].split("|").filter { it.isNotEmpty() }.toMutableList()
            folders[p[0]] = Folder(p[1], keys)
        }
    }

    private fun persist() {
        val foldersText = folders.entries.joinToString("\n") {
            it.key + "\t" + it.value.name + "\t" + it.value.keys.joinToString("|")
        }
        prefs.edit()
            .putString("home", homeSlots.joinToString("|") { it ?: "" })
            .putString("dock", dockSlots.joinToString("|") { it ?: "" })
            .putString("folders", foldersText)
            .putInt("home_pages", pageCount)
            .apply()
    }

    private fun currentPack(): String =
        getSharedPreferences("settings", MODE_PRIVATE).getString("icon_pack", "") ?: ""

    private fun makeIntent(cn: ComponentName): Intent =
        Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(cn)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    // ---------- icon cache (disk) ----------
    private fun cacheDir(): File = File(filesDir, "icons").apply { mkdirs() }

    private fun cacheFile(key: String): File = File(cacheDir(), key.hashCode().toString() + ".png")

    private fun cacheTag(): String = currentPack() + CACHE_VER

    private fun showCachedApps() {
        val c = getSharedPreferences("app_cache", MODE_PRIVATE)
        if (c.getString("pack", "") != cacheTag()) return
        val lines = c.getString("list", "")!!.split("\n").filter { it.isNotEmpty() }
        val list = lines.mapNotNull { line ->
            val parts = line.split("\t")
            if (parts.size < 2) return@mapNotNull null
            val cn = ComponentName.unflattenFromString(parts[0]) ?: return@mapNotNull null
            val bmp = BitmapFactory.decodeFile(cacheFile(parts[0]).path) ?: return@mapNotNull null
            AppItem(parts[1], parts[0], makeIntent(cn), bmp)
        }
        if (list.isEmpty()) return
        allApps = list
        refreshHome()
        filterApps("")
    }

    private fun saveCache(apps: List<AppItem>, tag: String) {
        try {
            for (a in apps) {
                val f = cacheFile(a.key)
                FileOutputStream(f).use { out -> a.icon.compress(Bitmap.CompressFormat.PNG, 100, out) }
            }
            getSharedPreferences("app_cache", MODE_PRIVATE).edit()
                .putString("pack", tag)
                .putString("list", apps.joinToString("\n") { it.key + "\t" + it.label })
                .apply()
        } catch (e: Exception) {
        }
    }

    private fun loadApps() {
        val pack = currentPack()
        val tag = pack + CACHE_VER
        bg.execute {
            try {
                val pm = packageManager
                val sizePx = dp(96)
                val packMap = if (pack.isNotEmpty()) IconPack.map(this, pack) else null
                val packCtx = if (pack.isNotEmpty()) IconPack.context(this, pack) else null
                val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val apps = pm.queryIntentActivities(main, 0).map { ri ->
                    val cn = ComponentName(ri.activityInfo.packageName, ri.activityInfo.name)
                    val packDraw = packMap?.get(cn.flattenToString())
                        ?.let { name -> packCtx?.let { IconPack.drawable(it, name) } }
                    val src = packDraw ?: ri.loadIcon(pm)
                    AppItem(
                        ri.loadLabel(pm).toString(),
                        cn.flattenToString(),
                        makeIntent(cn),
                        squareIcon(src, sizePx)
                    )
                }.sortedBy { it.label.lowercase() }
                saveCache(apps, tag)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    allApps = apps
                    appsLoaded = true
                    loadedPack = pack
                    filterApps(searchBox.text?.toString() ?: "")
                    refreshHome()
                }
            } catch (e: Exception) {
            }
        }
    }

    /** Adaptive icon ki background aur foreground alag draw hoti hain, system ka mask nahi lagta. */
    private fun squareIcon(dr: Drawable, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        try {
            if (dr is AdaptiveIconDrawable) {
                dr.background?.let {
                    it.setBounds(0, 0, size, size)
                    it.draw(c)
                }
                dr.foreground?.let {
                    it.setBounds(0, 0, size, size)
                    it.draw(c)
                }
            } else {
                dr.setBounds(0, 0, size, size)
                dr.draw(c)
            }
        } catch (e: Exception) {
        }
        return bmp
    }

    private fun buildMap(): Map<String, AppItem> {
        val m = HashMap<String, AppItem>()
        allApps.forEach { m[it.key] = it }
        for ((id, f) in folders) {
            val items = f.keys.mapNotNull { m[it] }
            m[FOLDER_PREFIX + id] = AppItem(
                f.name,
                FOLDER_PREFIX + id,
                Intent(Intent.ACTION_MAIN),
                makeFolderIcon(items)
            )
        }
        return m
    }

    private fun makeFolderIcon(items: List<AppItem>): Bitmap {
        val size = dp(96)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC2A2A30.toInt() }
        c.drawRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), bgPaint)
        val pad = size * 0.12f
        val gap = size * 0.06f
        val mini = (size - 2 * pad - gap) / 2f
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        items.take(4).forEachIndexed { i, item ->
            val x = pad + (i % 2) * (mini + gap)
            val y = pad + (i / 2) * (mini + gap)
            c.drawBitmap(item.icon, null, RectF(x, y, x + mini, y + mini), p)
        }
        return bmp
    }

    private fun refreshHome() {
        val map = buildMap()
        for (p in 0 until pageCount) {
            pageAdapters[p].submit(homeSlots.subList(p * PAGE_CELLS, (p + 1) * PAGE_CELLS).map { k -> k?.let { map[it] } })
        }
        dockAdapter.submit(dockSlots.map { k -> k?.let { map[it] } })
        hint.visibility = if (homeSlots.all { it == null }) View.VISIBLE else View.GONE
        updateDots()
        if (folderOpen) refreshFolderPopup()
    }

    private fun filterApps(q: String) {
        drawerAdapter.submit(
            if (q.isBlank()) allApps else allApps.filter { it.label.contains(q, ignoreCase = true) }
        )
    }

    private fun setSearchMode(on: Boolean) {
        searching = on
        titleView.visibility = if (on) View.GONE else View.VISIBLE
        searchBox.visibility = if (on) View.VISIBLE else View.GONE
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        if (on) {
            searchBox.requestFocus()
            imm.showSoftInput(searchBox, 0)
        } else {
            searchBox.setText("")
            imm.hideSoftInputFromWindow(searchBox.windowToken, 0)
        }
    }

    // ---------- folder ----------
    private fun openFolder(id: String) {
        val f = folders[id] ?: return
        currentFolderId = id
        folderOpen = true
        folderTitle.text = f.name
        refreshFolderPopup()
        folderLayer.visibility = View.VISIBLE
        animateFolder(1f)
    }

    private fun closeFolder() {
        if (!folderOpen) return
        folderOpen = false
        animateFolder(0f)
    }

    private fun closeFolderInstant() {
        folderAnim?.cancel()
        folderOpen = false
        currentFolderId = null
        folderF = 0f
        folderLayer.visibility = View.GONE
        folderScrim.alpha = 0f
        folderCard.alpha = 0f
        folderCard.scaleX = 0.8f
        folderCard.scaleY = 0.8f
        applyStageBlur(0f)
    }

    private fun animateFolder(target: Float) {
        folderAnim?.cancel()
        val a = ValueAnimator.ofFloat(folderF, target)
        a.duration = if (target > 0f) 340L else 240L
        a.interpolator = if (target > 0f) OvershootInterpolator(0.7f) else emphasized
        a.addUpdateListener {
            val f = it.animatedValue as Float
            folderF = f
            val c = f.coerceIn(0f, 1f)
            folderScrim.alpha = c
            folderCard.alpha = c
            val s = 0.8f + 0.2f * f
            folderCard.scaleX = s
            folderCard.scaleY = s
            applyStageBlur(c)
        }
        a.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (target == 0f && !folderOpen) {
                    folderLayer.visibility = View.GONE
                    currentFolderId = null
                }
            }
        })
        folderAnim = a
        a.start()
    }

    private fun refreshFolderPopup() {
        val id = currentFolderId ?: return
        val f = folders[id] ?: return
        folderTitle.text = f.name
        val map = buildMap()
        folderAdapter.submit(f.keys.map { map[it] })
    }

    private fun showFolderItemMenu(item: AppItem) {
        val id = currentFolderId ?: return
        val pkg = item.intent.component?.packageName ?: return
        showSheet(item.label, item.icon, listOf(
            "Remove from folder" to { removeFromFolder(id, item.key) },
            "App info" to { openAppInfo(pkg) }
        ))
    }

    private fun removeFromFolder(id: String, key: String) {
        val f = folders[id] ?: return
        val free = homeSlots.indexOf(null)
        if (free < 0) {
            Toast.makeText(this, "Home screen full hai", Toast.LENGTH_SHORT).show()
            return
        }
        f.keys.remove(key)
        homeSlots[free] = key
        if (f.keys.size <= 1) {
            val slot = homeSlots.indexOf(FOLDER_PREFIX + id)
            val left = f.keys.firstOrNull()
            folders.remove(id)
            if (slot >= 0) homeSlots[slot] = left
            closeFolderInstant()
        }
        persist()
        refreshHome()
    }

    // ---------- long-press (home menu) ----------
    private fun inside(v: View, x: Float, y: Float): Boolean {
        if (v.visibility != View.VISIBLE || v.height == 0) return false
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        return x >= loc[0] && x <= loc[0] + v.width && y >= loc[1] && y <= loc[1] + v.height
    }

    private fun onItem(rv: RecyclerView, x: Float, y: Float): Boolean {
        if (!inside(rv, x, y)) return false
        val loc = IntArray(2)
        rv.getLocationOnScreen(loc)
        val child = rv.findChildViewUnder(x - loc[0], y - loc[1]) ?: return false
        val vh = rv.getChildViewHolder(child)
        val pos = vh.bindingAdapterPosition
        val adapter = rv.adapter as? AppAdapter ?: return false
        return pos != RecyclerView.NO_POSITION && adapter.itemAt(pos) != null
    }

    private fun isOnAnyItem(x: Float, y: Float): Boolean {
        for (g in pageGrids) if (onItem(g, x, y)) return true
        return onItem(dock, x, y)
    }

    override fun onEmptyLongPress(rawX: Float, rawY: Float) {
        if (progress > 0.01f || pendingReturn || folderOpen) return
        if (isOnAnyItem(rawX, rawY)) return
        if (inside(widgetBox, rawX, rawY)) return
        window.decorView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        showHomeMenu()
    }

    private fun showHomeMenu() {
        val clockOn = prefs.getBoolean("clock", true)
        val actions = listOf<Pair<String, () -> Unit>>(
            "Add widget" to { pickWidget() },
            (if (clockOn) "Hide clock" else "Show clock") to {
                prefs.edit().putBoolean("clock", !clockOn).apply()
                applyClock()
            },
            "Launcher settings" to { startActivity(Intent(this, SettingsActivity::class.java)) }
        )
        showSheet("Home screen", null, actions)
    }

    // ---------- widgets ----------
    private fun pickWidget() {
        val pm = packageManager
        val providers = widgetMgr.installedProviders
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
        if (providers.isEmpty()) {
            Toast.makeText(this, "Koi widget installed nahi hai", Toast.LENGTH_SHORT).show()
            return
        }
        val actions = providers.map { info ->
            info.loadLabel(pm).toString() to { bindWidget(info) }
        }
        showSheet("Add widget", null, actions)
    }

    private fun bindWidget(info: AppWidgetProviderInfo) {
        val id = widgetHost.allocateAppWidgetId()
        if (widgetMgr.bindAppWidgetIdIfAllowed(id, info.provider)) {
            afterBind(id)
        } else {
            pendingWidgetId = id
            @Suppress("DEPRECATION")
            startActivityForResult(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                },
                REQ_BIND
            )
        }
    }

    private fun afterBind(id: Int) {
        val info = widgetMgr.getAppWidgetInfo(id)
        if (info == null) {
            widgetHost.deleteAppWidgetId(id)
            return
        }
        if (info.configure != null) {
            pendingWidgetId = id
            @Suppress("DEPRECATION")
            startActivityForResult(
                Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                    component = info.configure
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                },
                REQ_CONFIG
            )
        } else {
            finishAddWidget(id, info)
        }
    }

    private fun finishAddWidget(id: Int, info: AppWidgetProviderInfo) {
        addWidgetView(id, info)
        saveWidgetIds()
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_BIND -> {
                if (resultCode == RESULT_OK) afterBind(pendingWidgetId)
                else widgetHost.deleteAppWidgetId(pendingWidgetId)
            }
            REQ_CONFIG -> {
                if (resultCode == RESULT_OK) {
                    val info = widgetMgr.getAppWidgetInfo(pendingWidgetId)
                    if (info != null) finishAddWidget(pendingWidgetId, info)
                    else widgetHost.deleteAppWidgetId(pendingWidgetId)
                } else {
                    widgetHost.deleteAppWidgetId(pendingWidgetId)
                }
            }
        }
    }

    private fun addWidgetView(id: Int, info: AppWidgetProviderInfo) {
        val hv: AppWidgetHostView = widgetHost.createView(this, id, info)
        hv.tag = id
        hv.setOnLongClickListener {
            showSheet("Widget", null, listOf("Remove widget" to {
                widgetHost.deleteAppWidgetId(id)
                widgetBox.removeView(hv)
                saveWidgetIds()
            }))
            true
        }
        val h = dp(max(info.minHeight, 60))
        widgetBox.addView(hv, LinearLayout.LayoutParams(MATCH, h).apply {
            setMargins(dp(12), dp(8), dp(12), 0)
        })
    }

    private fun currentWidgetIds(): List<Int> =
        (0 until widgetBox.childCount).mapNotNull { widgetBox.getChildAt(it).tag as? Int }

    private fun saveWidgetIds() {
        prefs.edit().putString("widget_ids", currentWidgetIds().joinToString(",")).apply()
    }

    private fun restoreWidgets() {
        val ids = prefs.getString("widget_ids", "")!!
            .split(",").mapNotNull { it.toIntOrNull() }
        for (id in ids) {
            val info = widgetMgr.getAppWidgetInfo(id)
            if (info != null) addWidgetView(id, info) else widgetHost.deleteAppWidgetId(id)
        }
        saveWidgetIds()
    }

    // ---------- long-press menu (drawer) ----------
    private fun showMenu(item: AppItem, where: String) {
        val pkg = item.intent.component?.packageName ?: return
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        if (where == "drawer") {
            actions.add("Add to Home" to { addTo(homeSlots, item.key, "Home") })
            actions.add("Add to Dock" to { addTo(dockSlots, item.key, "Dock") })
        }
        actions.add("App info" to { openAppInfo(pkg) })
        if (!isSystemApp(pkg)) {
            actions.add("Uninstall" to { uninstallApp(pkg) })
        }
        showSheet(item.label, item.icon, actions)
    }

    private fun openAppInfo(pkg: String) {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
        } catch (e: Exception) {
        }
    }

    private fun isSystemApp(pkg: String): Boolean = try {
        val ai = packageManager.getApplicationInfo(pkg, 0)
        (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
    } catch (e: Exception) {
        true
    }

    private fun uninstallApp(pkg: String) {
        try {
            startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))
        } catch (e: Exception) {
            Toast.makeText(this, "Uninstall nahi ho paya", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSheet(heading: String, icon: Bitmap?, actions: List<Pair<String, () -> Unit>>) {
        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(16), dp(8), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat()
                setColor(0xF2202024.toInt())
            }
        }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), dp(8))
            if (icon != null) {
                addView(
                    ImageView(this@MainActivity).apply { setImageBitmap(icon) },
                    LinearLayout.LayoutParams(dp(40), dp(40))
                )
            }
            addView(TextView(this@MainActivity).apply {
                text = heading
                textSize = 18f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(if (icon != null) dp(12) else 0, 0, 0, 0)
            })
        }
        card.addView(head)
        for ((label, action) in actions) {
            card.addView(TextView(this).apply {
                text = label
                textSize = 16f
                setTextColor(Color.WHITE)
                setPadding(dp(16), dp(14), dp(16), dp(14))
                setOnClickListener {
                    d.dismiss()
                    action()
                }
            }, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        val scroll = ScrollView(this).apply {
            addView(card, MATCH, WRAP)
        }
        val wrap = FrameLayout(this).apply {
            setPadding(dp(12), 0, dp(12), dp(24))
            addView(scroll, MATCH, WRAP)
        }
        d.setContentView(wrap)
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.BOTTOM)
            setLayout(MATCH, WRAP)
            setDimAmount(0.4f)
            setWindowAnimations(android.R.style.Animation_InputMethod)
        }
        d.show()
    }

    /** Dock ya home (current page par khali cell) mein app rakhta hai. */
    private fun addTo(slots: MutableList<String?>, key: String, name: String) {
        if (key in slots || folders.values.any { key in it.keys } || key in homeSlots) {
            Toast.makeText(this, "Already in $name", Toast.LENGTH_SHORT).show()
            return
        }
        var idx = slots.indexOf(null)
        if (name == "Home" && idx == -1) {
            if (pageCount < MAX_PAGES) {
                addPage()
                idx = homeSlots.indexOf(null)
            }
        }
        if (idx == -1) {
            Toast.makeText(this, "$name is full", Toast.LENGTH_SHORT).show()
            return
        }
        slots[idx] = key
        persist()
        refreshHome()
        Toast.makeText(this, "Added to $name", Toast.LENGTH_SHORT).show()
    }

    // ---------- app open animation ----------
    private fun launch(view: View, item: AppItem) {
        if (item.key.startsWith(FOLDER_PREFIX)) {
            openFolder(folderIdOf(item.key))
            return
        }
        if (pendingReturn) return
        if (searching) setSearchMode(false)
        try {
            val s = getSharedPreferences("settings", MODE_PRIVATE)
            val ms = s.getInt("anim_ms", 300).toLong()
            val custom = s.getBoolean("custom_anim", true)
            iconBlurOn = s.getBoolean("icon_blur", true)

            val loc = IntArray(2)
            val rl = IntArray(2)
            view.getLocationInWindow(loc)
            root.getLocationInWindow(rl)
            val l = (loc[0] - rl[0]).toFloat()
            val t = (loc[1] - rl[1]).toFloat()
            pivX = l + view.width / 2f
            pivY = t + view.height / 2f
            stage.pivotX = pivX
            stage.pivotY = pivY
            pendingReturn = true

            if (custom) {
                overlay.prepare(l, t, l + view.width, t + view.height, avgColor(item.icon), item.icon)
                overlay.visibility = View.VISIBLE
                animateLaunch(1f, ms) { startApp(item, true, null) }
            } else {
                startApp(item, false, view)
                animateLaunch(1f, ms)
            }
        } catch (e: Exception) {
            resetLaunch()
        }
    }

    private fun startApp(item: AppItem, custom: Boolean, src: View?) {
        try {
            val opts = if (custom || src == null)
                ActivityOptions.makeCustomAnimation(this, 0, 0)
            else
                ActivityOptions.makeScaleUpAnimation(src, 0, 0, src.width, src.height)
            startActivity(item.intent, opts.toBundle())
            closeFolderInstant()
            stage.postDelayed({
                if (pendingReturn && stage.hasWindowFocus()) resetLaunch()
            }, 4000)
        } catch (e: Exception) {
            resetLaunch()
        }
    }

    private fun resetLaunch() {
        pendingReturn = false
        launchAnim?.cancel()
        applyDepth(0f)
        overlay.t = 0f
        overlay.visibility = View.GONE
        stage.setLayerType(View.LAYER_TYPE_NONE, null)
    }

    private fun animateLaunch(to: Float, duration: Long, onEnd: (() -> Unit)? = null) {
        launchAnim?.cancel()
        stage.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        val a = ValueAnimator.ofFloat(depth, to)
        a.duration = duration
        a.interpolator = emphasized
        a.addUpdateListener {
            val v = it.animatedValue as Float
            applyDepth(v)
            overlay.t = v
        }
        a.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false

            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (!cancelled) {
                    stage.setLayerType(View.LAYER_TYPE_NONE, null)
                    onEnd?.invoke()
                }
            }
        })
        launchAnim = a
        a.start()
    }

    private fun avgColor(b: Bitmap): Int =
        Bitmap.createScaledBitmap(b, 1, 1, true).getPixel(0, 0) or 0xFF000000.toInt()

    override fun onResume() {
        super.onResume()
        if (pendingReturn) resetLaunch()
        enableHighRefresh()
        val sp = getSharedPreferences("settings", MODE_PRIVATE)
        fxBlur = sp.getBoolean("fx_blur", true)
        fxIntro = sp.getBoolean("fx_intro", true)
        pageAnim = sp.getBoolean("page_anim", true)
        applyIconStyle()
        applyClock()
        applyDockStyle()
        if (!appsLoaded || currentPack() != loadedPack) loadApps()
        runIntroIfNeeded()
    }

    private fun applyDepth(d: Float) {
        depth = d
        val s = 1f + 0.10f * d
        stage.scaleX = s
        stage.scaleY = s
        stage.alpha = (1f - 0.5f * d).coerceIn(0f, 1f)
        if (Build.VERSION.SDK_INT >= 31 && iconBlurOn) {
            val step = (d.coerceIn(0f, 1f) * 8f + 0.5f).toInt()
            if (step != lastIconBlur) {
                lastIconBlur = step
                setIconBlur(step)
            }
        }
    }

    private fun dist() = maxOf(1f, root.height * 0.5f)

    // ---------- drawer gesture ----------
    override fun canStartDrag(dy: Float, rawStartY: Float): Boolean {
        if (dragLock || folderOpen) return false
        return if (progress > 0.5f) dy > 0 && !drawerList.canScrollVertically(-1) else dy < 0
    }

    override fun onDragStart() {
        anim?.cancel()
        dragBase = progress
        setLayers(true)
    }

    override fun onDrag(dy: Float) {
        setProgress((dragBase - dy / dist()).coerceIn(0f, 1f))
    }

    override fun onDragEnd(vy: Float) {
        val v = -vy / dist()
        val open = when {
            v > 0.6f -> true
            v < -0.6f -> false
            else -> progress > 0.5f
        }
        animateProgress(if (open) 1f else 0f, v)
    }

    private fun animateProgress(target: Float, velocity: Float) {
        anim?.cancel()
        setLayers(true)
        val a = SpringAnimation(FloatValueHolder(progress))
        a.spring = SpringForce(target).setStiffness(380f).setDampingRatio(0.92f)
        a.setStartVelocity(velocity)
        a.setMinimumVisibleChange(1f / 256f)
        a.addUpdateListener { _, value, _ -> setProgress(value.coerceIn(0f, 1f)) }
        a.addEndListener { _, canceled, _, _ ->
            if (!canceled) {
                setProgress(target)
                setLayers(false)
            }
        }
        anim = a
        a.start()
    }

    private fun setProgress(p: Float) {
        progress = p
        val h = root.height.toFloat()
        drawer.alpha = p
        drawer.translationY = (1f - p) * 0.25f * h
        home.alpha = 1f - p
        val s = 1f - 0.08f * p
        home.scaleX = s
        home.scaleY = s
        applyHomeBlur(p)
        val dv = if (p > 0.001f) View.VISIBLE else View.INVISIBLE
        if (drawer.visibility != dv) drawer.visibility = dv
        val hv = if (p > 0.999f) View.INVISIBLE else View.VISIBLE
        if (home.visibility != hv) home.visibility = hv
        if (p < 0.001f && searching) setSearchMode(false)
    }

    private fun setLayers(on: Boolean) {
        val t = if (on) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_NONE
        home.setLayerType(t, null)
        drawer.setLayerType(t, null)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (folderOpen) {
            closeFolder()
        } else if (pendingReturn) {
            resetLaunch()
        } else if (searching) {
            setSearchMode(false)
        } else if (progress > 0.5f) {
            animateProgress(0f, 0f)
        } else if (currentPage > 0) {
            goToPage(currentPage - 1, true)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (pendingReturn && launchAnim?.isRunning == true) resetLaunch()
        if (progress > 0.01f) animateProgress(0f, 0f)
    }

    override fun onStart() {
        super.onStart()
        widgetHost.startListening()
    }

    override fun onStop() {
        super.onStop()
        widgetHost.stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(userPresentReceiver)
        } catch (e: Exception) {
        }
        bg.shutdownNow()
    }
}

data class IconStyle(val sizeDp: Int, val shape: Int, val labels: Boolean, val gapH: Int, val gapV: Int)

fun readIconStyle(ctx: Context): IconStyle {
    val p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    return IconStyle(
        p.getInt("icon_size", 60),
        p.getInt("icon_shape", 0),
        p.getBoolean("icon_label", true),
        p.getInt("gap_h", 8),
        p.getInt("gap_v", 12)
    )
}

class AppAdapter(
    private val showLabel: Boolean,
    private val onClick: (View, AppItem) -> Unit,
    private val onLong: ((AppItem) -> Unit)?
) : RecyclerView.Adapter<AppAdapter.VH>() {

    private var items: MutableList<AppItem?> = mutableListOf()
    private var style = IconStyle(60, 0, true, 8, 12)

    init { setHasStableIds(true) }

    fun submit(list: List<AppItem?>) {
        items = list.toMutableList()
        notifyDataSetChanged()
    }

    fun applyStyle(s: IconStyle) {
        style = s
        notifyDataSetChanged()
    }

    fun itemAt(pos: Int): AppItem? = items.getOrNull(pos)

    class VH(val root: LinearLayout, val icon: ShapeIcon, val label: TextView) :
        RecyclerView.ViewHolder(root)

    override fun getItemId(position: Int): Long =
        items.getOrNull(position)?.key?.hashCode()?.toLong() ?: (1_000_000_000L + position)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        val d = ctx.resources.displayMetrics.density
        val icon = ShapeIcon(ctx).apply {
            layoutParams = LinearLayout.LayoutParams((56 * d).toInt(), (56 * d).toInt())
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val label = TextView(ctx).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 11f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            maxLines = 1
            gravity = Gravity.CENTER
            setPadding(0, (6 * d).toInt(), 0, 0)
        }
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = RecyclerView.LayoutParams(MATCH, WRAP)
            addView(icon)
            addView(label)
        }
        return VH(root, icon, label)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val d = h.root.resources.displayMetrics.density
        val px = (style.sizeDp * d).toInt()
        val lp = h.icon.layoutParams
        if (lp.width != px || lp.height != px) {
            lp.width = px
            lp.height = px
            h.icon.layoutParams = lp
        }
        h.icon.shape = style.shape

        val item = items.getOrNull(position)
        if (item == null) {
            h.icon.setImageDrawable(null)
            h.icon.visibility = View.INVISIBLE
            h.label.visibility = View.GONE
            h.root.setPadding(0, (8 * d).toInt(), 0, (8 * d).toInt())
            h.root.setOnClickListener(null)
            h.root.setOnLongClickListener(null)
            h.root.setOnTouchListener(null)
            return
        }

        h.icon.visibility = View.VISIBLE
        h.icon.setImageBitmap(item.icon)

        val showText = showLabel && style.labels
        h.label.visibility = if (showText) View.VISIBLE else View.GONE
        h.label.text = item.label

        val vPad = if (showText) 12 else 8
        h.root.setPadding(0, (vPad * d).toInt(), 0, (vPad * d).toInt())

        h.root.setOnClickListener { onClick(h.icon, item) }
        if (onLong != null) {
            h.root.setOnLongClickListener {
                onLong.invoke(item)
                true
            }
        } else {
            h.root.setOnLongClickListener(null)
        }
        h.root.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN ->
                    v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80)
                        .setInterpolator(DecelerateInterpolator()).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(220)
                        .setInterpolator(OvershootInterpolator(2f)).start()
            }
            false
        }
    }

    override fun getItemCount() = items.size
}
