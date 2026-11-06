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
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
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
import android.view.ViewOutlineProvider
import android.view.Window
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.view.inputmethod.InputMethodManager
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
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

class AppItem(val label: String, val key: String, val intent: Intent, val icon: Bitmap)

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
            2 -> path.addRoundRect(rect, w * 0.05f, h * 0.05f, Path.Direction.CW)
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

class GestureRoot(ctx: Context, private val host: Host) : FrameLayout(ctx) {

    interface Host {
        fun canStartDrag(dy: Float): Boolean
        fun onDragStart()
        fun onDrag(dy: Float)
        fun onDragEnd(vy: Float)
    }

    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var vt: VelocityTracker? = null

    private fun track(e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x
                downY = e.y
                dragging = false
                vt?.recycle()
                vt = VelocityTracker.obtain()
                vt?.addMovement(e)
            }
            MotionEvent.ACTION_MOVE -> {
                vt?.addMovement(e)
                val dy = e.y - downY
                val dx = e.x - downX
                if (!dragging) {
                    if (abs(dy) > slop && abs(dy) > abs(dx) * 1.5f && host.canStartDrag(dy)) {
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
        private const val HOST_ID = 1024
        private const val REQ_BIND = 11
        private const val REQ_CONFIG = 12
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
    private lateinit var homeGrid: RecyclerView
    private lateinit var dock: RecyclerView
    private lateinit var hint: TextView
    private lateinit var header: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var searchBox: EditText
    private lateinit var homeAdapter: AppAdapter
    private lateinit var dockAdapter: AppAdapter
    private lateinit var drawerAdapter: AppAdapter

    private val homeGuide = GridGuide()
    private val widgetHost by lazy { AppWidgetHost(this, HOST_ID) }
    private val widgetMgr by lazy { AppWidgetManager.getInstance(this) }
    private var pendingWidgetId = -1

    private var allApps: List<AppItem> = emptyList()
    private var appsLoaded = false
    private var loadedPack = ""
    private var dragLock = false
    private var drawerGapH = 8
    private var drawerGapV = 12
    private val homeKeys = mutableListOf<String>()
    private val dockKeys = mutableListOf<String>()
    private val prefs by lazy { getSharedPreferences("home", MODE_PRIVATE) }

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
    private val iconBlurMax by lazy { dp(8) }
    private var iconBlurOn = true
    private var listBottomGap = 0
    private val emphasized = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /** Home grid par drag ke time dashed grid cells dikhata hai. */
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
            val lm = parent.layoutManager as? GridLayoutManager ?: return
            val cols = lm.spanCount
            val child = parent.getChildAt(0) ?: return
            val rowH = child.height.toFloat()
            if (rowH <= 0f) return
            val left = parent.paddingLeft.toFloat()
            val right = (parent.width - parent.paddingRight).toFloat()
            val cw = (right - left) / cols
            val rows = ((parent.height - parent.paddingTop) / rowH).toInt() + 1
            val inset = dp(4).toFloat()
            for (row in 0 until rows) {
                for (col in 0 until cols) {
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

        homeKeys.addAll(loadKeys("home"))
        dockKeys.addAll(loadKeys("dock"))

        homeAdapter = AppAdapter(true, { v, i -> launch(v, i) }, null)
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
            text = "Swipe up for apps. Long-press an app to add it here."
            textSize = 13f
            setTextColor(0xB3FFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(32), dp(24), 0)
        }
        homeGrid = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, 4)
            adapter = homeAdapter
            overScrollMode = View.OVER_SCROLL_NEVER
            itemAnimator = null
            clipToPadding = false
            setPadding(dp(8), dp(16), dp(8), 0)
            setOnLongClickListener {
                showHomeMenu()
                true
            }
        }
        homeGrid.addItemDecoration(homeGuide)
        dock = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, 1)
            adapter = dockAdapter
            overScrollMode = View.OVER_SCROLL_NEVER
            itemAnimator = null
            background = GradientDrawable().apply {
                cornerRadius = dp(30).toFloat()
                setColor(0x33FFFFFF)
            }
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        home = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setOnLongClickListener {
                showHomeMenu()
                true
            }
            addView(clock, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(date, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(widgetBox, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(hint, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(homeGrid, LinearLayout.LayoutParams(MATCH, 0, 1f))
            addView(dock, LinearLayout.LayoutParams(MATCH, WRAP).apply {
                setMargins(dp(14), 0, dp(14), dp(14))
            })
        }

        drawerList = FadeRecycler(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, 4)
            adapter = drawerAdapter
            setHasFixedSize(true)
            setItemViewCacheSize(24)
            itemAnimator = null
            clipToPadding = true
            // Rubber-band overscroll: edge par khinchne par stretch hota hai
            overScrollMode = View.OVER_SCROLL_ALWAYS
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
                val rem = n % 4
                val lastRowStart = n - (if (rem == 0) 4 else rem)
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

        stage = FrameLayout(this)
        stage.addView(home, MATCH, MATCH)
        stage.addView(drawer, MATCH, MATCH)
        root = GestureRoot(this, this)
        root.addView(stage, MATCH, MATCH)

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
        dragHelper(homeAdapter, homeKeys, homeGrid, homeGuide).attachToRecyclerView(homeGrid)
        dragHelper(dockAdapter, dockKeys, dock, null).attachToRecyclerView(dock)
        restoreWidgets()

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
        drawerList.setPadding(dp(8), top + dp(64), dp(8), ime)
        listBottomGap = bottom + dp(24)
        drawerList.invalidateItemDecorations()
    }

    /** Display ka sabse tez refresh rate (jaise 120Hz) window ke liye maangta hai. */
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

    // ---------- drag and drop ----------
    /** Drag app ki key se order badalta hai, taaki drop ke baad icon wahin rahe. */
    private fun dragHelper(
        adapter: AppAdapter,
        keys: MutableList<String>,
        rv: RecyclerView,
        guide: GridGuide?
    ) = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
        ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
    ) {
        override fun onMove(
            recycler: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder
        ): Boolean {
            val fromPos = vh.bindingAdapterPosition
            val toPos = target.bindingAdapterPosition
            if (fromPos == RecyclerView.NO_POSITION || toPos == RecyclerView.NO_POSITION) return false
            val fromKey = adapter.keyAt(fromPos) ?: return false
            val toKey = adapter.keyAt(toPos) ?: return false
            adapter.move(fromPos, toPos)
            val fromIdx = keys.indexOf(fromKey)
            val toIdx = keys.indexOf(toKey)
            if (fromIdx >= 0 && toIdx >= 0) {
                keys.removeAt(fromIdx)
                keys.add(toIdx, fromKey)
            }
            return true
        }

        override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
            val key = adapter.keyAt(vh.bindingAdapterPosition) ?: return
            keys.remove(key)
            save()
        }

        override fun onSelectedChanged(vh: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(vh, actionState)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                dragLock = true
                guide?.visible = true
                vh?.itemView?.alpha = 0.7f
                rv.invalidate()
            }
        }

        override fun clearView(recycler: RecyclerView, vh: RecyclerView.ViewHolder) {
            super.clearView(recycler, vh)
            vh.itemView.alpha = 1f
            dragLock = false
            guide?.visible = false
            rv.invalidate()
            save()
        }
    })

    private fun applyIconStyle() {
        val s = readIconStyle(this)
        homeAdapter.applyStyle(s)
        dockAdapter.applyStyle(s)
        drawerAdapter.applyStyle(s)
        drawerGapH = s.gapH
        drawerGapV = s.gapV
        drawerList.invalidateItemDecorations()
    }

    private fun applyClock() {
        val on = prefs.getBoolean("clock", true)
        clock.visibility = if (on) View.VISIBLE else View.GONE
        date.visibility = if (on) View.VISIBLE else View.GONE
    }

    private fun loadKeys(name: String): List<String> =
        prefs.getString(name, "")!!.split("|").filter { it.isNotEmpty() }

    private fun persist() {
        prefs.edit()
            .putString("home", homeKeys.joinToString("|"))
            .putString("dock", dockKeys.joinToString("|"))
            .apply()
    }

    private fun save() {
        persist()
        refreshHome()
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

    private fun showCachedApps() {
        val c = getSharedPreferences("app_cache", MODE_PRIVATE)
        if (c.getString("pack", "") != currentPack()) return
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

    private fun saveCache(apps: List<AppItem>, pack: String) {
        try {
            for (a in apps) {
                val f = cacheFile(a.key)
                FileOutputStream(f).use { out -> a.icon.compress(Bitmap.CompressFormat.PNG, 100, out) }
            }
            getSharedPreferences("app_cache", MODE_PRIVATE).edit()
                .putString("pack", pack)
                .putString("list", apps.joinToString("\n") { it.key + "\t" + it.label })
                .apply()
        } catch (e: Exception) {
        }
    }

    private fun loadApps() {
        val pack = currentPack()
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
                        toBitmap(src, sizePx)
                    )
                }.sortedBy { it.label.lowercase() }
                saveCache(apps, pack)
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

    private fun refreshHome() {
        val map = allApps.associateBy { it.key }
        homeAdapter.submit(homeKeys.mapNotNull { map[it] })
        val dockItems = dockKeys.mapNotNull { map[it] }
        dockAdapter.submit(dockItems)
        (dock.layoutManager as GridLayoutManager).spanCount = maxOf(1, dockItems.size)
        dock.visibility = if (dockItems.isEmpty()) View.GONE else View.VISIBLE
        hint.visibility = if (homeKeys.isEmpty() && dockKeys.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun toBitmap(dr: Drawable, size: Int): Bitmap {
        return try {
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            dr.setBounds(0, 0, size, size)
            dr.draw(Canvas(bmp))
            bmp
        } catch (e: Exception) {
            Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        }
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

    // ---------- home screen menu (khali jagah long-press) ----------
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
    override fun onStart() {
        super.onStart()
        widgetHost.startListening()
    }

    override fun onStop() {
        super.onStop()
        widgetHost.stopListening()
    }

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

    // ---------- long-press menu (app icons) ----------
    private fun showMenu(item: AppItem, where: String) {
        val pkg = item.intent.component?.packageName ?: return
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        if (where == "drawer") {
            actions.add("Add to Home" to { addTo(homeKeys, item.key, Int.MAX_VALUE, "Home") })
            actions.add("Add to Dock" to { addTo(dockKeys, item.key, 4, "Dock") })
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

    private fun addTo(list: MutableList<String>, key: String, max: Int, name: String) {
        if (key in list) {
            Toast.makeText(this, "Already in $name", Toast.LENGTH_SHORT).show()
            return
        }
        if (list.size >= max) {
            Toast.makeText(this, "$name is full", Toast.LENGTH_SHORT).show()
            return
        }
        list.add(key)
        save()
        Toast.makeText(this, "Added to $name", Toast.LENGTH_SHORT).show()
    }

    // ---------- app open animation ----------
    private fun launch(view: View, item: AppItem) {
        if (pendingReturn) return
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
        applyIconStyle()
        applyClock()
        if (!appsLoaded || currentPack() != loadedPack) loadApps()
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

    @TargetApi(31)
    private fun setIconBlur(step: Int) {
        val fx = if (step == 0) null else {
            val r = iconBlurMax * step / 8f
            RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP)
        }
        homeGrid.setRenderEffect(fx)
        dock.setRenderEffect(fx)
        drawerList.setRenderEffect(fx)
    }

    private fun dist() = maxOf(1f, root.height * 0.5f)

    override fun canStartDrag(dy: Float): Boolean {
        if (dragLock) return false
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
        if (pendingReturn) {
            resetLaunch()
        } else if (searching) {
            setSearchMode(false)
        } else if (progress > 0.5f) {
            animateProgress(0f, 0f)
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

    private var items: MutableList<AppItem> = mutableListOf()
    private var style = IconStyle(60, 0, true, 8, 12)

    init { setHasStableIds(true) }

    fun submit(list: List<AppItem>) {
        items = list.toMutableList()
        notifyDataSetChanged()
    }

    fun applyStyle(s: IconStyle) {
        style = s
        notifyDataSetChanged()
    }

    fun keyAt(pos: Int): String? = items.getOrNull(pos)?.key

    fun move(from: Int, to: Int) {
        items.add(to, items.removeAt(from))
        notifyItemMoved(from, to)
    }

    class VH(val root: LinearLayout, val icon: ShapeIcon, val label: TextView) :
        RecyclerView.ViewHolder(root)

    override fun getItemId(position: Int) = items[position].key.hashCode().toLong()

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
        val item = items[position]
        val d = h.root.resources.displayMetrics.density

        val px = (style.sizeDp * d).toInt()
        val lp = h.icon.layoutParams
        if (lp.width != px || lp.height != px) {
            lp.width = px
            lp.height = px
            h.icon.layoutParams = lp
        }
        h.icon.shape = style.shape
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
