package com.colorlauncher

import android.app.Activity
import android.app.ActivityOptions
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors
import kotlin.math.abs

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

class AppItem(val label: String, val key: String, val icon: Bitmap, val intent: Intent)

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
        canvas.drawLine(cx + a, cy + a, cx + a + s * 0.18f, cy + a + s * 0.18f, paint)
    }
}

/** RecyclerView jiska top/bottom fade scroll ke hisaab se aata hai */
class FadeRecycler(ctx: Context) : RecyclerView(ctx) {
    override fun getTopFadingEdgeStrength(): Float {
        val len = maxOf(1, verticalFadingEdgeLength)
        return (computeVerticalScrollOffset().toFloat() / len).coerceIn(0f, 1f)
    }

    override fun getBottomFadingEdgeStrength(): Float {
        val len = maxOf(1, verticalFadingEdgeLength)
        val rest = computeVerticalScrollRange() - computeVerticalScrollOffset() - computeVerticalScrollExtent()
        return (rest.toFloat() / len).coerceIn(0f, 1f)
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
                    if (abs(dy) > slop && abs(dy) > abs(dx) && host.canStartDrag(dy)) {
                        dragging = true
                        host.onDragStart()
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }
                if (dragging) host.onDrag(dy)
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
    private val bg = Executors.newSingleThreadExecutor()
    private lateinit var root: GestureRoot
    private lateinit var stage: FrameLayout
    private lateinit var home: LinearLayout
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

    private var allApps: List<AppItem> = emptyList()
    private val homeKeys = mutableListOf<String>()
    private val dockKeys = mutableListOf<String>()
    private val prefs by lazy { getSharedPreferences("launcher", MODE_PRIVATE) }

    private var progress = 0f
    private var dragBase = 0f
    private var anim: SpringAnimation? = null
    private var searching = false

    // app open animation state
    private val openAnims = ArrayList<SpringAnimation>()
    private var opening = false
    private var resumed = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupWindow()
        enableHighRefresh()

        homeKeys.addAll(loadKeys("home"))
        dockKeys.addAll(loadKeys("dock"))

        homeAdapter = AppAdapter(true, { v, i -> launch(v, i) }, { i -> showMenu(i, "home") })
        dockAdapter = AppAdapter(false, { v, i -> launch(v, i) }, { i -> showMenu(i, "dock") })
        drawerAdapter = AppAdapter(true, { v, i -> launch(v, i) }, { i -> showMenu(i, "drawer") })

        val clock = TextClock(this).apply {
            format12Hour = "h:mm"
            format24Hour = "HH:mm"
            textSize = 56f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            gravity = Gravity.CENTER
        }
        val date = TextClock(this).apply {
            format12Hour = "EEE, d MMM"
            format24Hour = "EEE, d MMM"
            textSize = 15f
            setTextColor(0xCCFFFFFF.toInt())
            gravity = Gravity.CENTER
        }
        hint = TextView(this).apply {
            text = "Swipe up for apps. Long-press an app in the drawer to add it here."
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
        }
        dock = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, 4)
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
            addView(clock, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(date, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(hint, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(homeGrid, LinearLayout.LayoutParams(MATCH, 0, 1f))
            addView(dock, LinearLayout.LayoutParams(MATCH, WRAP).apply {
                setMargins(dp(14), 0, dp(14), dp(14))
            })
        }

        // drawer: background transparent (no blur, no scrim)
        drawerList = FadeRecycler(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, 4)
            adapter = drawerAdapter
            setHasFixedSize(true)
            setItemViewCacheSize(24)
            itemAnimator = null
            clipToPadding = true
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(56))
            setPadding(dp(8), dp(72), dp(8), dp(24))
        }
        titleView = TextView(this).apply {
            text = "All apps"
            textSize = 26f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
        }
        searchBox = EditText(this).apply {
            hint = "Search apps"
            setHintTextColor(0x80FFFFFF.toInt())
            setTextColor(Color.WHITE)
            textSize = 18f
            setBackgroundColor(Color.TRANSPARENT)
            isSingleLine = true
            visibility = View.GONE
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    filterApps(s?.toString() ?: "")
                }
            })
        }
        val searchBtn = SearchIcon(this).apply {
            setOnClickListener { setSearchMode(!searching) }
        }
        header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), 0, dp(16), 0)
            addView(titleView, LinearLayout.LayoutParams(0, MATCH, 1f))
            addView(searchBox, LinearLayout.LayoutParams(0, MATCH, 1f))
            addView(searchBtn, LinearLayout.LayoutParams(dp(40), dp(40)))
        }
        drawer = FrameLayout(this).apply {
            visibility = View.INVISIBLE
            alpha = 0f
        }
        drawer.addView(drawerList, MATCH, MATCH)
        drawer.addView(header, FrameLayout.LayoutParams(MATCH, dp(56)).apply { topMargin = dp(32) })

        stage = FrameLayout(this)
        stage.addView(home, MATCH, MATCH)
        stage.addView(drawer, MATCH, MATCH)
        root = GestureRoot(this, this)
        root.addView(stage, MATCH, MATCH)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            applyInsets(bars.top, bars.bottom, ime.bottom)
            insets
        }
        setContentView(root)
        WindowCompat.getInsetsController(window, root)?.apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        root.post { setProgress(progress) }
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
        home.setPadding(0, top + dp(24), 0, bottom + dp(8))
        (header.layoutParams as FrameLayout.LayoutParams).topMargin = top + dp(8)
        header.requestLayout()
        drawerList.setPadding(dp(8), top + dp(72), dp(8), maxOf(bottom, ime) + dp(24))
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
            // unsupported device: keep default refresh rate
        }
    }

    private fun loadKeys(name: String): List<String> =
        prefs.getString(name, "")!!.split("|").filter { it.isNotEmpty() }

    private fun save() {
        prefs.edit()
            .putString("home", homeKeys.joinToString("|"))
            .putString("dock", dockKeys.joinToString("|"))
            .apply()
    }

    private fun loadApps() {
        val sizePx = dp(56)
        bg.execute {
            val pm = packageManager
            val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(main, 0)
                .filter { it.activityInfo.packageName != packageName }
                .map { ri ->
                    val cn = ComponentName(ri.activityInfo.packageName, ri.activityInfo.name)
                    val intent = Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LAUNCHER)
                        .setComponent(cn)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    AppItem(
                        ri.loadLabel(pm).toString(),
                        cn.flattenToString(),
                        toBitmap(ri.loadIcon(pm), sizePx),
                        intent
                    )
                }
                .sortedBy { it.label.lowercase() }
            runOnUiThread {
                allApps = apps
                drawerAdapter.submit(apps)
                refreshHome()
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
        hint.visibility = if (homeKeys.isEmpty() && dockItems.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun toBitmap(dr: Drawable, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        dr.setBounds(0, 0, size, size)
        dr.draw(Canvas(bmp))
        return bmp
    }

    // ---------- search ----------
    private fun filterApps(q: String) {
        drawerAdapter.submit(
            if (q.isBlank()) allApps else allApps.filter { it.label.contains(q, ignoreCase = true) }
        )
    }

    private fun setSearchMode(on: Boolean) {
        searching = on
        titleView.visibility = if (on) View.GONE else View.VISIBLE
        searchBox.visibility = if (on) View.VISIBLE else View.GONE
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (on) {
            searchBox.requestFocus()
            imm.showSoftInput(searchBox, 0)
        } else {
            searchBox.setText("")
            imm.hideSoftInputFromWindow(searchBox.windowToken, 0)
        }
    }

    // ---------- long-press menu ----------
    private fun showMenu(item: AppItem, where: String) {
        val pkg = item.intent.component?.packageName ?: return
        val options = if (where == "drawer")
            arrayOf("Add to Home", "Add to Dock", "App info")
        else
            arrayOf("Remove", "App info")
        AlertDialog.Builder(this)
            .setTitle(item.label)
            .setItems(options) { _, which ->
                when (options[which]) {
                    "Add to Home" -> addTo(homeKeys, item.key, 20, "Home")
                    "Add to Dock" -> addTo(dockKeys, item.key, 4, "Dock")
                    "Remove" -> {
                        if (where == "home") homeKeys.remove(item.key) else dockKeys.remove(item.key)
                        save()
                        refreshHome()
                    }
                    "App info" -> try {
                        startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                        )
                    } catch (e: Exception) {
                    }
                }
            }
            .show()
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
        refreshHome()
        Toast.makeText(this, "Added to $name", Toast.LENGTH_SHORT).show()
    }

    // ---------- app open animation (no blur, no close animation) ----------
    private fun launch(view: View, item: AppItem) {
        try {
            val loc = IntArray(2)
            val sl = IntArray(2)
            view.getLocationInWindow(loc)
            stage.getLocationInWindow(sl)
            val px = (loc[0] - sl[0]) + view.width / 2f
            val py = (loc[1] - sl[1]) + view.height / 2f
            val opts = ActivityOptions.makeScaleUpAnimation(view, 0, 0, view.width, view.height)
            startActivity(item.intent, opts.toBundle())
            playOpen(px, py)
            // safety: agar app nahi khula to launcher normal ho jaye
            stage.postDelayed({
                if (opening && resumed) resetStage()
            }, 1500)
        } catch (e: Exception) {
            resetStage()
            Toast.makeText(this, "Can't open ${item.label}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playOpen(px: Float, py: Float) {
        openAnims.forEach { it.cancel() }
        openAnims.clear()
        opening = true
        stage.pivotX = px
        stage.pivotY = py
        stage.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        val sx = SpringAnimation(stage, DynamicAnimation.SCALE_X, 1.15f)
        val sy = SpringAnimation(stage, DynamicAnimation.SCALE_Y, 1.15f)
        val al = SpringAnimation(stage, DynamicAnimation.ALPHA, 0f)
        for (a in listOf(sx, sy)) {
            a.spring.setStiffness(240f)
            a.spring.setDampingRatio(1f)
            a.setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE)
        }
        al.spring.setStiffness(420f)
        al.spring.setDampingRatio(1f)
        al.setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_ALPHA)
        openAnims.add(sx)
        openAnims.add(sy)
        openAnims.add(al)
        sx.start()
        sy.start()
        al.start()
    }

    /** Bina kisi animation ke turant normal state */
    private fun resetStage() {
        opening = false
        openAnims.forEach { it.cancel() }
        openAnims.clear()
        stage.scaleX = 1f
        stage.scaleY = 1f
        stage.alpha = 1f
        stage.pivotX = stage.width / 2f
        stage.pivotY = stage.height / 2f
        stage.setLayerType(View.LAYER_TYPE_NONE, null)
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (opening) resetStage()
    }

    override fun onPause() {
        super.onPause()
        resumed = false
    }

    override fun onStop() {
        super.onStop()
        if (opening) resetStage()
    }

    // ---------- drawer gesture ----------
    private fun dist() = maxOf(1f, root.height * 0.5f)

    override fun canStartDrag(dy: Float): Boolean {
        if (searching) return false
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
        a.setSpring(SpringForce(target).setStiffness(380f).setDampingRatio(0.9f))
        a.setStartVelocity(velocity)
        a.setMinimumVisibleChange(1f / 256f)
        a.addUpdateListener { _, v, _ -> setProgress(v.coerceIn(0f, 1f)) }
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
        val hv = if (p < 0.999f) View.VISIBLE else View.INVISIBLE
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
        if (searching) setSearchMode(false)
        else if (progress > 0.5f) animateProgress(0f, 0f)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (searching) setSearchMode(false)
        if (progress > 0.01f) animateProgress(0f, 0f)
    }

    override fun onDestroy() {
        super.onDestroy()
        bg.shutdownNow()
    }
}

class AppAdapter(
    private val showLabel: Boolean,
    private val onClick: (View, AppItem) -> Unit,
    private val onLong: (AppItem) -> Unit
) : RecyclerView.Adapter<AppAdapter.VH>() {

    private var items: List<AppItem> = emptyList()

    init {
        setHasStableIds(true)
    }

    fun submit(list: List<AppItem>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(val root: LinearLayout, val icon: ImageView, val label: TextView) :
        RecyclerView.ViewHolder(root)

    override fun getItemId(position: Int) = items[position].key.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        val d = ctx.resources.displayMetrics.density
        val icon = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams((56 * d).toInt(), (56 * d).toInt())
        }
        val label = TextView(ctx).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 11f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setPadding(0, (6 * d).toInt(), 0, 0)
            visibility = if (showLabel) View.VISIBLE else View.GONE
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, (12 * d).toInt(), 0, (12 * d).toInt())
            layoutParams = RecyclerView.LayoutParams(MATCH, WRAP)
            addView(icon)
            addView(label)
        }
        return VH(root, icon, label)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = items[position]
        h.icon.setImageBitmap(item.icon)
        h.label.text = item.label
        h.root.setOnClickListener { onClick(h.icon, item) }
        h.root.setOnLongClickListener {
            onLong(item)
            true
        }
        h.root.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN ->
                    v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(90)
                        .setInterpolator(DecelerateInterpolator()).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(220)
                        .setInterpolator(OvershootInterpolator()).start()
            }
            false
        }
    }

    override fun getItemCount() = items.size
}
