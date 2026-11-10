package com.colorlauncher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.TargetApi
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
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.util.LruCache
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewOutlineProvider
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
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max

private const val TAG = "CL"
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

class ShapeIcon(ctx: Context) : ImageView(ctx) {
    var shape = 0
        set(v) {
            if (field == v) return
            field = v
            invalidateOutline()
        }

    init {
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val w = view.width
                val h = view.height
                if (w <= 0 || h <= 0) return
                when (shape) {
                    1 -> outline.setOval(0, 0, w, h)
                    2 -> outline.setRect(0, 0, w, h)
                    else -> outline.setRoundRect(0, 0, w, h, w * 0.3f)
                }
            }
        }
    }
}

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
        sheet.alpha = 255
        val rad = iconCorner + (screenCorner - iconCorner) * tt
        canvas.drawRoundRect(cur, rad, rad, sheet)
        val bmp = icon ?: return
        val half = from.width() / 2f
        val cx = cur.centerX()
        val cy = cur.centerY()
        dst.set(cx - half, cy - half, cx + half, cy + half)
        canvas.drawBitmap(bmp, null, dst, iconPaint)
    }
}

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

class GestureRoot(ctx: Context, private val host: Host) : FrameLayout(ctx) {

    interface Host {
        fun canStartDrag(dy: Float, rawStartY: Float): Boolean
        fun onDragStart()
        fun onDrag(dy: Float)
        fun onDragEnd(vy: Float)
        fun onEmptyLongPress(rawX: Float, rawY: Float)
        fun canStartPageSwipe(dx: Float): Boolean
        fun onPageDrag(dx: Float)
        fun onPageDragEnd(vx: Float)
        fun isBusy(): Boolean
    }

    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private val edgePx = 24 * ctx.resources.displayMetrics.density
    private var downX = 0f
    private var downY = 0f
    private var rawDownX = 0f
    private var rawDownY = 0f
    private var dragging = false
    private var pageDragging = false
    private var longFired = false
    private var ignoring = false
    private var vt: VelocityTracker? = null

    private val longPressRunnable = Runnable {
        try {
            if (!dragging && !pageDragging && !longFired && !ignoring) {
                longFired = true
                host.onEmptyLongPress(rawDownX, rawDownY)
            }
        } catch (e: Exception) {
            Log.e(TAG, "longPress", e)
        }
    }

    private fun cancelLong() = removeCallbacks(longPressRunnable)

    override fun onDetachedFromWindow() {
        removeCallbacks(longPressRunnable)
        vt?.recycle()
        vt = null
        super.onDetachedFromWindow()
    }

    private fun inEdge(x: Float, y: Float): Boolean =
        x < edgePx || x > width - edgePx || y > height - edgePx

    private fun track(e: MotionEvent) {
        try {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.x
                    downY = e.y
                    rawDownX = e.rawX
                    rawDownY = e.rawY
                    dragging = false
                    pageDragging = false
                    longFired = false
                    ignoring = host.isBusy() || inEdge(e.x, e.y)
                    cancelLong()
                    if (!ignoring) {
                        postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                    }
                    vt?.recycle()
                    vt = VelocityTracker.obtain()
                    vt?.addMovement(e)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (ignoring) return
                    vt?.addMovement(e)
                    val dy = e.y - downY
                    val dx = e.x - downX
                    if (!dragging && !pageDragging) {
                        if (abs(dy) > slop || abs(dx) > slop) cancelLong()
                        if (abs(dy) > slop && abs(dy) > abs(dx) * 1.5f && host.canStartDrag(dy, rawDownY)) {
                            cancelLong()
                            dragging = true
                            host.onDragStart()
                            host.onDrag(dy)
                            parent?.requestDisallowInterceptTouchEvent(true)
                        } else if (abs(dx) > slop && abs(dx) > abs(dy) * 1.5f && host.canStartPageSwipe(dx)) {
                            cancelLong()
                            pageDragging = true
                            host.onPageDrag(dx)
                            parent?.requestDisallowInterceptTouchEvent(true)
                        }
                    } else if (dragging) {
                        host.onDrag(dy)
                    } else if (pageDragging) {
                        host.onPageDrag(dx)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    cancelLong()
                    if (ignoring) {
                        ignoring = false
                        return
                    }
                    val cancel = e.actionMasked == MotionEvent.ACTION_CANCEL
                    if (dragging) {
                        vt?.addMovement(e)
                        vt?.computeCurrentVelocity(1000)
                        host.onDragEnd(if (cancel) 0f else (vt?.yVelocity ?: 0f))
                    }
                    if (pageDragging) {
                        vt?.addMovement(e)
                        vt?.computeCurrentVelocity(1000)
                        host.onPageDragEnd(if (cancel) 0f else (vt?.xVelocity ?: 0f))
                    }
                    dragging = false
                    pageDragging = false
                    vt?.recycle()
                    vt = null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "gesture", e)
            dragging = false
            pageDragging = false
        }
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        track(e)
        return dragging || pageDragging
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        track(e)
        return true
    }
}

class MainActivity : ComponentActivity(), GestureRoot.Host {

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
        private const val CACHE_VER = "v6"
        private const val STATE_PAGE = "state_page"
        private const val STATE_FOLDER = "state_folder"
        private const val LAUNCH_RESET_MS = 4000L
    }

    private val bg = Executors.newSingleThreadExecutor()

    private val iconLru = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 24).toInt().coerceIn(6 * 1024 * 1024, 20 * 1024 * 1024)
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val sharedPool = RecyclerView.RecycledViewPool()

    private var folderIconCache: Bitmap? = null
    private var folderIconKey = ""
    private var launchToken: Any? = null
    private var returningFromApp = false

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
    private lateinit var folderLayer: FrameLayout
    private lateinit var folderScrim: View
    private lateinit var folderCard: LinearLayout
    private lateinit var folderTitle: TextView
    private lateinit var folderGrid: RecyclerView
    private lateinit var folderAdapter: AppAdapter

    private val pageGrids = mutableListOf<RecyclerView>()
    private val pageAdapters = mutableListOf<AppAdapter>()
    private val pageGuides = mutableListOf<GridGuide>()

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

    private var lastTop = -1
    private var lastBottom = -1
    private var lastIme = -1

    private var dropX = 0f
    private var dropY = 0f
    private var dragStartX = 0f
    private var dragStartY = 0f

    private val homeSlots = MutableList<String?>(PAGE_CELLS) { null }
    private val dockSlots = MutableList<String?>(DOCK_SLOTS) { null }
    private val folders = LinkedHashMap<String, Folder>()
    private val prefs by lazy { getSharedPreferences("home", MODE_PRIVATE) }

    private var pageCount = 1
    private var currentPage = 0
    private var pageOffset = 0f
    private var pageAnimator: ValueAnimator? = null
    private var lastEdgeTime = 0L
    private var builtPageCount = -1

    private var folderOpen = false
    private var currentFolderId: String? = null
    private var folderF = 0f
    private var folderAnim: ValueAnimator? = null

    private var progress = 0f
    private var dragBase = 0f
    private var anim: SpringAnimation? = null
    private var pendingReturn = false

    private var searching = false
    private var depth = 0f
    private var launchAnim: ValueAnimator? = null
    private var closeAnim: ValueAnimator? = null
    private var lastIconBlur = -1
    private var iconBlurOn = true
    private var listBottomGap = 0
    private val emphasized = PathInterpolator(0.4f, 0f, 0.2f, 1f)

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
            try {
                if (i.action == Intent.ACTION_USER_PRESENT) {
                    introPending = true
                    runIntroIfNeeded()
                }
            } catch (e: Exception) {
                Log.e(TAG, "userPresent", e)
            }
        }
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            try {
                Log.i(TAG, "package change: ${i.action} ${i.data}")
                loadApps()
            } catch (e: Exception) {
                Log.e(TAG, "packageReceiver", e)
            }
        }
    }

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            handleBack()
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun closeAnimEnabled(): Boolean =
        getSharedPreferences("settings", MODE_PRIVATE).getBoolean("close_anim", true)

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
            try {
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
            } catch (e: Exception) {
                Log.e(TAG, "guide draw", e)
            }
        }
    }

    // ---------------- Lifecycle ----------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setupWindow()
            enableHighRefresh()
            loadPages()
            loadSlots("dock", dockSlots)
            loadFolders()
            buildViews()
            setContentView(root)
            onBackPressedDispatcher.addCallback(this, backCallback)
            setProgress(0f)
            applyClock()
            applyDockStyle()
            restoreWidgets()
            if (savedInstanceState != null) {
                val pg = savedInstanceState.getInt(STATE_PAGE, 0)
                currentPage = pg.coerceIn(0, max(0, pageCount - 1))
                val fid = savedInstanceState.getString(STATE_FOLDER)
                if (fid != null && folders.containsKey(fid)) openFolder(fid)
            }
            registerReceiver(userPresentReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))
            val pkgFilter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            }
            registerReceiver(packageReceiver, pkgFilter)
            showCachedApps()
        } catch (e: Exception) {
            Log.e(TAG, "onCreate failed", e)
        }
    }

    override fun onStart() {
        super.onStart()
        try {
            widgetHost.startListening()
        } catch (e: Exception) {
            Log.e(TAG, "widget start", e)
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            val wasReturning = pendingReturn || returningFromApp
            returningFromApp = false
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
            if (wasReturning) playCloseAnim()
        } catch (e: Exception) {
            Log.e(TAG, "onResume", e)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            lastHomeBlur = -1
            lastStageBlur = -1
            home.setRenderEffect(null)
            stage.setRenderEffect(null)
            pageAnimator?.cancel()
            folderAnim?.cancel()
            closeAnim?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "onPause", e)
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            if (pendingReturn) returningFromApp = true
            anim?.cancel()
            widgetHost.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "onStop", e)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        try {
            outState.putInt(STATE_PAGE, currentPage)
            outState.putString(STATE_FOLDER, currentFolderId)
        } catch (e: Exception) {
            Log.e(TAG, "saveState", e)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        try {
            if (level >= TRIM_MEMORY_RUNNING_LOW) {
                iconLru.evictAll()
                folderIconCache = null
                folderIconKey = ""
            }
            if (level >= TRIM_MEMORY_UI_HIDDEN) {
                sharedPool.clear()
            }
        } catch (e: Exception) {
            Log.e(TAG, "trim", e)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        try {
            if (pendingReturn) resetLaunch()
            if (folderOpen) closeFolderInstant()
            if (searching) setSearchMode(false)
            if (progress > 0.01f) animateProgress(0f, 0f)
        } catch (e: Exception) {
            Log.e(TAG, "newIntent", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(userPresentReceiver)
        } catch (e: Exception) {
        }
        try {
            unregisterReceiver(packageReceiver)
        } catch (e: Exception) {
        }
        try {
            stage.removeCallbacks(null)
            pageAnimator?.cancel()
            anim?.cancel()
            folderAnim?.cancel()
            launchAnim?.cancel()
            closeAnim?.cancel()
            pageAnimator = null
            anim = null
            folderAnim = null
            launchAnim = null
            closeAnim = null
        } catch (e: Exception) {
            Log.e(TAG, "destroyCancel", e)
        }
        bg.shutdownNow()
        iconLru.evictAll()
        folderIconCache = null
        sharedPool.clear()
    }

    override fun onBackPressed() {
        handleBack()
    }

    private fun handleBack() {
        try {
            when {
                folderOpen -> closeFolder()
                pendingReturn -> resetLaunch()
                searching -> setSearchMode(false)
                progress > 0.5f -> animateProgress(0f, 0f)
                currentPage > 0 -> goToPage(currentPage - 1, true)
                else -> moveTaskToBack(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "back", e)
        }
    }

    // ---------------- Views ----------------

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

    private fun buildViews() {
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
        widgetBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        hint = TextView(this).apply {
            text = "Long-press an app to add it here."
            textSize = 13f
            setTextColor(0xB3FFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(32), dp(24), 0)
        }

        pageRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        pageHost = FrameLayout(this).apply {
            clipChildren = true
            addView(pageRow, FrameLayout.LayoutParams(MATCH, MATCH))
        }
        pageDots = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        dock = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, DOCK_SLOTS)
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
            addView(dockHolder, LinearLayout.LayoutParams(MATCH, WRAP))
        }

        drawerList = FadeRecycler(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, COLS)
            adapter = drawerAdapter
            setRecycledViewPool(sharedPool)
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
        folderAdapter = AppAdapter(true, { v, i -> launch(v, i) }, { showFolderItemMenu(it) })
        folderGrid = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, 3)
            adapter = folderAdapter
            setRecycledViewPool(sharedPool)
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

        rebuildPages()

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
    }

    private fun applyInsets(top: Int, bottom: Int, ime: Int) {
        try {
            if (top == lastTop && bottom == lastBottom && ime == lastIme) return
            lastTop = top
            lastBottom = bottom
            lastIme = ime
            home.setPadding(0, top + dp(24), 0, bottom)
            (header.layoutParams as FrameLayout.LayoutParams).topMargin = top
            header.requestLayout()
            (removeZone.layoutParams as FrameLayout.LayoutParams).topMargin = top + dp(8)
            drawerList.setPadding(dp(8), top + dp(64), dp(8), ime)
            listBottomGap = bottom + dp(24)
            drawerList.invalidateItemDecorations()
        } catch (e: Exception) {
            Log.e(TAG, "applyInsets", e)
        }
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
            Log.e(TAG, "refresh rate", e)
        }
    }

    // ---------------- Pages ----------------

    private fun loadPages() {
        val count = prefs.getInt("home_pages", 1).coerceIn(1, MAX_PAGES)
        pageCount = count
        val saved = (prefs.getString("home", "") ?: "").split("|")
        homeSlots.clear()
        for (i in 0 until count * PAGE_CELLS) {
            homeSlots.add(saved.getOrNull(i)?.takeIf { it.isNotEmpty() })
        }
    }

    private fun loadSlots(name: String, target: MutableList<String?>) {
        val parts = (prefs.getString(name, "") ?: "").split("|")
        for (i in target.indices) {
            target[i] = parts.getOrNull(i)?.takeIf { it.isNotEmpty() }
        }
    }

    private fun rebuildPages() {
        try {
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
                pageRow.addView(grid, LinearLayout.LayoutParams(MATCH, MATCH))
            }
            pageRow.layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            pageHost.post { layoutPages() }
            pageDots.removeAllViews()
            for (p in 0 until pageCount) {
                pageDots.addView(View(this).apply {
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x66FFFFFF) }
                }, LinearLayout.LayoutParams(dp(6), dp(6)).apply { marginStart = dp(4); marginEnd = dp(4) })
            }
            pageDots.visibility = if (pageCount > 1) View.VISIBLE else View.GONE
            if (currentPage >= pageCount) currentPage = pageCount - 1
            builtPageCount = pageCount
            updateDots()
            attachDrags()
            refreshHome()
        } catch (e: Exception) {
            Log.e(TAG, "rebuildPages", e)
        }
    }

    private fun layoutPages() {
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
            Log.e(TAG, "layoutPages", e)
        }
    }

    private fun pageWidth(): Float = pageHost.width.toFloat().coerceAtLeast(1f)

    private fun setPageOffset(x: Float) {
        pageOffset = x
        pageRow.translationX = x
    }

    private fun updateDots() {
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
            Log.e(TAG, "updateDots", e)
        }
    }

    private fun goToPage(p: Int, animated: Boolean) {
        try {
            val target = p.coerceIn(0, max(0, pageCount - 1))
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
        } catch (e: Exception) {
            Log.e(TAG, "goToPage", e)
        }
    }

    private fun addPage() {
        if (pageCount >= MAX_PAGES) return
        pageCount++
        for (i in 0 until PAGE_CELLS) homeSlots.add(null)
        prefs.edit().putInt("home_pages", pageCount).apply()
        rebuildPages()
    }

    private fun trimEmptyPages() {
        try {
            while (pageCount > 1) {
                val lastStart = (pageCount - 1) * PAGE_CELLS
                if (lastStart + PAGE_CELLS > homeSlots.size) break
                val empty = (lastStart until lastStart + PAGE_CELLS).all { homeSlots.getOrNull(it) == null }
                if (!empty) break
                if (currentPage == pageCount - 1) break
                repeat(PAGE_CELLS) { homeSlots.removeAt(homeSlots.size - 1) }
                pageCount--
            }
            if (currentPage >= pageCount) currentPage = pageCount - 1
            prefs.edit().putInt("home_pages", pageCount).apply()
        } catch (e: Exception) {
            Log.e(TAG, "trimEmptyPages", e)
        }
    }

    private fun checkEdgePage(rawX: Float) {
        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "edgePage", e)
        }
    }

    private fun applyDockStyle() {
        try {
            val sp = getSharedPreferences("settings", MODE_PRIVATE)
            val size = sp.getInt("dock_size", 1)
            val pos = sp.getInt("dock_pos", 0)
            val pad = when (size) { 0 -> 4; 2 -> 10; else -> 6 }
            dock.setPadding(dp(pad), dp(pad), dp(pad), dp(pad))
            val bottomMargin = if (pos == 1) dp(40) else dp(14)
            dockHolder.layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
                setMargins(dp(14), 0, dp(14), bottomMargin)
            }
        } catch (e: Exception) {
            Log.e(TAG, "dockStyle", e)
        }
    }

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

    // ---------------- Blur ----------------

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

    // ---------------- Intro ----------------

    private fun runIntroIfNeeded() {
        if (!introPending) return
        if (progress > 0.01f || pendingReturn || folderOpen) return
        introPending = false
        if (!fxIntro) return
        window.decorView.post { playIntro() }
    }

    private fun playIntro() {
        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "playIntro", e)
        }
    }

    // ---------------- Drag and drop ----------------

    private fun isFolder(k: String?) = k != null && k.startsWith(FOLDER_PREFIX)

    private fun folderIdOf(k: String) = k.removePrefix(FOLDER_PREFIX)

    private fun childPosAt(rv: RecyclerView, x: Float, y: Float): Int {
        val loc = IntArray(2)
        rv.getLocationOnScreen(loc)
        val child = rv.findChildViewUnder(x - loc[0], y - loc[1]) ?: return RecyclerView.NO_POSITION
        return rv.getChildAdapterPosition(child)
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
                    Log.e(TAG, "dragStart", e)
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
                            val base = pageIdx * PAGE_CELLS
                            if (removing) {
                                val idx = base + local
                                val src = homeSlots.getOrNull(idx)
                                if (src != null) {
                                    homeSlots[idx] = null
                                    if (isFolder(src)) folders.remove(folderIdOf(src))
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
                    persist()
                    rv.post { rebuildPagesIfCountChanged() }
                } catch (e: Exception) {
                    Log.e(TAG, "clearView drop", e)
                    refreshHomeSafe()
                }
            }
        }
        return ItemTouchHelper(cb)
    }

    private fun rebuildPagesIfCountChanged() {
        if (builtPageCount != pageCount || pageGrids.size != pageCount) {
            rebuildPages()
        } else {
            refreshHomeSafe()
        }
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

    // ---------------- Drawer progress (Host) ----------------

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

    private fun dist() = maxOf(1f, root.height * 0.5f)

    private fun animateProgress(target: Float, velocity: Float) {
        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "animateProgress", e)
        }
    }

    private fun setProgress(p: Float) {
        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "setProgress", e)
        }
    }

    private fun setLayers(on: Boolean) {
        val t = if (on) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_NONE
        home.setLayerType(t, null)
        drawer.setLayerType(t, null)
    }

    private fun applyDepth(d: Float) {
        depth = d
        val s = 1f + 0.14f * d
        stage.scaleX = s
        stage.scaleY = s
        stage.alpha = (1f - 0.6f * d.coerceIn(0f, 1f)).coerceIn(0f, 1f)
        if (Build.VERSION.SDK_INT >= 31 && iconBlurOn) {
            val step = if (d > 0.5f) 8 else 0
            if (step != lastIconBlur) {
                lastIconBlur = step
                setIconBlur(step)
            }
        }
    }

    // ---------------- Search ----------------

    private fun filterApps(q: String) {
        try {
            drawerAdapter.submitDiff(
                if (q.isBlank()) allApps else allApps.filter { it.label.contains(q, ignoreCase = true) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "filterApps", e)
        }
    }

    private fun setSearchMode(on: Boolean) {
        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "search", e)
        }
    }

    // ---------------- Folder ----------------

    private fun openFolder(id: String) {
        try {
            val f = folders[id] ?: return
            currentFolderId = id
            folderOpen = true
            folderTitle.text = f.name
            refreshFolderPopup()
            folderLayer.visibility = View.VISIBLE
            animateFolder(1f)
        } catch (e: Exception) {
            Log.e(TAG, "openFolder", e)
        }
    }

    private fun closeFolder() {
        if (!folderOpen) return
        folderOpen = false
        animateFolder(0f)
    }

    private fun closeFolderInstant() {
        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "closeFolderInstant", e)
        }
    }

    private fun animateFolder(target: Float) {
        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "animateFolder", e)
        }
    }

    private fun refreshFolderPopup() {
        try {
            val id = currentFolderId ?: return
            val f = folders[id] ?: return
            folderTitle.text = f.name
            val map = buildMap()
            folderAdapter.submitDiff(f.keys.map { map[it] })
        } catch (e: Exception) {
            Log.e(TAG, "folderPopup", e)
        }
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
        try {
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
            refreshHomeSafe()
        } catch (e: Exception) {
            Log.e(TAG, "removeFromFolder", e)
        }
    }

    // ---------------- Data: persist and apps ----------------

    private fun persist() {
        try {
            val foldersText = folders.entries.joinToString("\n") {
                it.key + "\t" + it.value.name + "\t" + it.value.keys.joinToString("|")
            }
            prefs.edit()
                .putString("home", homeSlots.joinToString("|") { it ?: "" })
                .putString("dock", dockSlots.joinToString("|") { it ?: "" })
                .putString("folders", foldersText)
                .putInt("home_pages", pageCount)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "persist", e)
        }
    }

    private fun loadFolders() {
        try {
            folders.clear()
            (prefs.getString("folders", "") ?: "").split("\n").filter { it.isNotEmpty() }.forEach { line ->
                val p = line.split("\t")
                if (p.size < 3) return@forEach
                val keys = p[2].split("|").filter { it.isNotEmpty() }.toMutableList()
                folders[p[0]] = Folder(p[1], keys)
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadFolders", e)
        }
    }

    private fun currentPack(): String =
        getSharedPreferences("settings", MODE_PRIVATE).getString("icon_pack", "") ?: ""

    private fun makeIntent(cn: ComponentName): Intent =
        Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(cn)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    private fun cacheDir(): File = File(filesDir, "icons").apply { mkdirs() }

    private fun cacheFile(pack: String, key: String): File {
        val dir = File(cacheDir(), if (pack.isEmpty()) "default" else pack).apply { mkdirs() }
        return File(dir, key.hashCode().toString() + ".png")
    }

    private fun loadIconFromDisk(pack: String, key: String): Bitmap? {
        val mk = pack + "|" + key
        iconLru.get(mk)?.let { return it }
        return try {
            val f = cacheFile(pack, key)
            if (!f.exists() || f.length() == 0L) return null
            BitmapFactory.decodeFile(f.path)?.also { iconLru.put(mk, it) }
        } catch (e: Exception) {
            Log.e(TAG, "icon decode $key", e)
            null
        }
    }

    private fun showCachedApps() {
        try {
            val pack = currentPack()
            val c = getSharedPreferences("app_cache", MODE_PRIVATE)
            if (c.getString("tag", "") != pack + "|" + CACHE_VER) return
            val lines = (c.getString("list", "") ?: "").split("\n").filter { it.isNotEmpty() }
            val list = lines.mapNotNull { line ->
                try {
                    val parts = line.split("\t")
                    if (parts.size < 2) return@mapNotNull null
                    val cn = ComponentName.unflattenFromString(parts[0]) ?: return@mapNotNull null
                    val bmp = loadIconFromDisk(pack, parts[0]) ?: return@mapNotNull null
                    AppItem(parts[1], parts[0], makeIntent(cn), bmp)
                } catch (e: Exception) {
                    Log.e(TAG, "cache line", e)
                    null
                }
            }
            if (list.isEmpty()) return
            allApps = list
            filterApps("")
            refreshHomeSafe()
        } catch (e: Exception) {
            Log.e(TAG, "showCachedApps", e)
        }
    }

    private fun saveCache(apps: List<AppItem>, pack: String) {
        try {
            for (a in apps) {
                val f = cacheFile(pack, a.key)
                if (f.exists() && f.length() > 0) continue
                FileOutputStream(f).use { out -> a.icon.compress(Bitmap.CompressFormat.PNG, 100, out) }
            }
            getSharedPreferences("app_cache", MODE_PRIVATE).edit()
                .putString("tag", pack + "|" + CACHE_VER)
                .putString("list", apps.joinToString("\n") { it.key + "\t" + it.label })
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "saveCache", e)
        }
    }

    private fun loadApps() {
        if (bg.isShutdown) return
        val pack = currentPack()
        bg.execute {
            try {
                val pm = packageManager
                val sizePx = dp(96)
                val packMap = if (pack.isNotEmpty()) IconPack.map(this, pack) else null
                val packCtx = if (pack.isNotEmpty()) IconPack.context(this, pack) else null
                val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val apps = pm.queryIntentActivities(main, 0).mapNotNull { ri ->
                    try {
                        val cn = ComponentName(ri.activityInfo.packageName, ri.activityInfo.name)
                        val key = cn.flattenToString()
                        val icon = loadIconFromDisk(pack, key)
                            ?: makeIcon(pack, packMap, packCtx, ri, pm, sizePx).also {
                                iconLru.put(pack + "|" + key, it)
                            }
                        AppItem(ri.loadLabel(pm).toString(), key, makeIntent(cn), icon)
                    } catch (e: Exception) {
                        Log.e(TAG, "app item", e)
                        null
                    }
                }.sortedBy { it.label.lowercase() }
                saveCache(apps, pack)
                runOnUiThread {
                    try {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        allApps = apps
                        appsLoaded = true
                        loadedPack = pack
                        filterApps(searchBox.text?.toString() ?: "")
                        refreshHomeSafe()
                    } catch (e: Exception) {
                        Log.e(TAG, "apps ui", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadApps", e)
            }
        }
    }

    private fun makeIcon(
        pack: String,
        packMap: Map<String, String>?,
        packCtx: Context?,
        ri: ResolveInfo,
        pm: PackageManager,
        size: Int
    ): Bitmap {
        val key = ComponentName(ri.activityInfo.packageName, ri.activityInfo.name).flattenToString()
        val packDraw = if (pack.isNotEmpty() && packMap != null && packCtx != null) {
            packMap[key]?.let { IconPack.drawable(packCtx, it) }
        } else null
        val src: Drawable = packDraw ?: ri.loadIcon(pm)
        return squareIcon(src, size)
    }

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
            Log.e(TAG, "squareIcon", e)
        }
        return bmp
    }

    private fun buildMap(): Map<String, AppItem> {
        val m = HashMap<String, AppItem>()
        allApps.forEach { m[it.key] = it }
        for ((id, f) in folders) {
            val items = f.keys.mapNotNull { m[it] }
            val sig = id + "|" + f.name + "|" + items.joinToString(",") { it.key }
            val icon = if (folderIconKey == sig && folderIconCache != null) {
                folderIconCache!!
            } else {
                makeFolderIcon(items).also {
                    folderIconKey = sig
                    folderIconCache = it
                }
            }
            m[FOLDER_PREFIX + id] = AppItem(f.name, FOLDER_PREFIX + id, Intent(Intent.ACTION_MAIN), icon)
        }
        return m
    }

    private fun makeFolderIcon(items: List<AppItem>): Bitmap {
        val size = dp(96)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "folderIcon", e)
        }
        return bmp
    }

    private fun refreshHomeSafe() {
        try {
            refreshHome()
        } catch (e: Exception) {
            Log.e(TAG, "refreshHome", e)
        }
    }

    private fun refreshHome() {
        val map = buildMap()
        for (p in 0 until pageAdapters.size) {
            val from = p * PAGE_CELLS
            val to = minOf(from + PAGE_CELLS, homeSlots.size)
            if (from >= to) break
            pageAdapters[p].submitDiff(homeSlots.subList(from, to).map { k -> k?.let { map[it] } })
        }
        dockAdapter.submitDiff(dockSlots.map { k -> k?.let { map[it] } })
        hint.visibility = if (homeSlots.all { it == null }) View.VISIBLE else View.GONE
        updateDots()
        if (folderOpen) refreshFolderPopup()
    }

    // ---------------- Launch and close animation ----------------

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
            Log.e(TAG, "appInfo", e)
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
            Log.e(TAG, "uninstall", e)
            Toast.makeText(this, "Uninstall nahi ho paya", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSheet(heading: String, icon: Bitmap?, actions: List<Pair<String, () -> Unit>>) {
        try {
            if (isFinishing || isDestroyed) return
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
                        try { action() } catch (e: Exception) { Log.e(TAG, "action $label", e) }
                    }
                }, LinearLayout.LayoutParams(MATCH, WRAP))
            }
            val scroll = ScrollView(this).apply { addView(card, MATCH, WRAP) }
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
        } catch (e: Exception) {
            Log.e(TAG, "showSheet", e)
        }
    }

    private fun addTo(slots: MutableList<String?>, key: String, name: String) {
        try {
            if (key in slots || folders.values.any { key in it.keys } || key in homeSlots) {
                Toast.makeText(this, "Already in $name", Toast.LENGTH_SHORT).show()
                return
            }
            var idx = slots.indexOf(null)
            if (name == "Home" && idx == -1 && pageCount < MAX_PAGES) {
                addPage()
                idx = homeSlots.indexOf(null)
            }
            if (idx == -1) {
                Toast.makeText(this, "$name is full", Toast.LENGTH_SHORT).show()
                return
            }
            slots[idx] = key
            persist()
            refreshHomeSafe()
            Toast.makeText(this, "Added to $name", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "addTo", e)
        }
    }

    private fun launch(view: View, item: AppItem) {
        try {
            if (item.key.startsWith(FOLDER_PREFIX)) {
                openFolder(folderIdOf(item.key))
                return
            }
            if (pendingReturn) return
            if (searching) setSearchMode(false)

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
            stage.pivotX = l + view.width / 2f
            stage.pivotY = t + view.height / 2f
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
            Log.e(TAG, "launch", e)
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
            val token = Any()
            launchToken = token
            stage.postDelayed({
                if (launchToken === token && pendingReturn) resetLaunch()
            }, LAUNCH_RESET_MS)
        } catch (e: Exception) {
            Log.e(TAG, "startApp", e)
            resetLaunch()
        }
    }

    private fun resetLaunch() {
        try {
            pendingReturn = false
            launchAnim?.cancel()
            applyDepth(0f)
            overlay.t = 0f
            overlay.visibility = View.GONE
            stage.setLayerType(View.LAYER_TYPE_NONE, null)
        } catch (e: Exception) {
            Log.e(TAG, "resetLaunch", e)
        }
    }

    /** App se wapas aane par chhota fade+scale-in (Settings: close_anim). */
    private fun playCloseAnim() {
        try {
            if (!closeAnimEnabled()) return
            closeAnim?.cancel()
            stage.alpha = 0f
            stage.scaleX = 0.96f
            stage.scaleY = 0.96f
            val a = ValueAnimator.ofFloat(0f, 1f)
            a.duration = 220
            a.interpolator = emphasized
            a.addUpdateListener {
                val v = it.animatedValue as Float
                stage.alpha = v
                val s = 0.96f + 0.04f * v
                stage.scaleX = s
                stage.scaleY = s
            }
            closeAnim = a
            a.start()
        } catch (e: Exception) {
            Log.e(TAG, "closeAnim", e)
        }
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

    private var items: List<AppItem?> = emptyList()
    private var style = IconStyle(60, 0, true, 8, 12)

    init { setHasStableIds(true) }

    fun submit(list: List<AppItem?>) {
        items = list.toList()
        notifyDataSetChanged()
    }

    fun submitDiff(list: List<AppItem?>) {
        val old = items
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = list.size
            override fun areItemsTheSame(o: Int, n: Int) = old[o]?.key == list[n]?.key
            override fun areContentsTheSame(o: Int, n: Int): Boolean {
                val a = old[o]
                val b = list[n]
                return a?.key == b?.key && a?.label == b?.label && a?.icon === b?.icon
            }
        })
        items = list.toList()
        diff.dispatchUpdatesTo(this)
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
        try {
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
            val current = (h.icon.drawable as? BitmapDrawable)?.bitmap
            if (current !== item.icon) {
                h.icon.setImageBitmap(item.icon)
            }

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
            h.root.setOnTouchListener { _, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN ->
                        h.root.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80)
                            .setInterpolator(DecelerateInterpolator()).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        h.root.animate().scaleX(1f).scaleY(1f).setDuration(220)
                            .setInterpolator(OvershootInterpolator(2f)).start()
                }
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "bind $position", e)
        }
    }

    override fun getItemCount() = items.size
}
