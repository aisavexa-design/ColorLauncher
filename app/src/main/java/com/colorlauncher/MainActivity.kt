package com.colorlauncher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.ActivityOptions
import android.app.admin.DevicePolicyManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAG = "CL"
private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
private const val MAX_PAGES = 10
private const val FOLDER_PREFIX = "folder:"
private const val LAUNCH_RESET_MS = 4000L
private const val SNAP_FILE = "snap.png"

class AppItem(val label: String, val key: String, val intent: Intent, val icon: Bitmap)

class Folder(var name: String, val keys: MutableList<String>)

data class IconStyle(val sizeDp: Int, val shape: Int, val labels: Boolean, val gapH: Int, val gapV: Int)

fun readIconStyle(ctx: Context): IconStyle {
    val p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    return IconStyle(
        p.getInt("icon_size", 63),
        p.getInt("icon_shape", 0),
        p.getBoolean("icon_label", false),
        p.getInt("gap_h", 6),
        p.getInt("gap_v", 0)
    )
}

class MainActivity : ComponentActivity(), HomeCallbacks, GestureRoot.Host {

    private lateinit var root: GestureRoot
    private lateinit var stage: FrameLayout
    private lateinit var overlay: LaunchOverlay
    private lateinit var home: HomeScreen
    private lateinit var drawerView: DrawerView
    private var categoryDrawer: CategoryDrawer? = null
    private lateinit var folderView: FolderView
    private lateinit var widgets: WidgetManager
    private lateinit var repo: AppRepository
    private lateinit var popup: PopupMenu
    private lateinit var sharedPool: RecyclerView.RecycledViewPool
    private lateinit var searchBar: SearchBar

    private val folders = LinkedHashMap<String, Folder>()
    private var allApps: List<AppItem> = emptyList()
    private var appsLoaded = false
    private var loadedPack = ""

    private var fxBlur = false
    private var iconBlurOn = false
    private var fxIntro = false
    private var pageAnim = true
    private var customAnim = true
    private var closeAnimOn = true
    private var animMs = 497L
    private var lastTop = -1
    private var lastBottom = -1
    private var lastIme = -1
    private var dragBase = 0f
    private var lastDrawerScrollAt = 0L

    private var pendingReturn = false
    private var returningFromApp = false
    private var launchToken: Any? = null
    private var launchAnim: ValueAnimator? = null
    private var depth = 0f
    private var closeAnimator: ValueAnimator? = null
    private var lastHomeStep = -1
    private var lastStageStep = -1
    private var lastIconStep = -1
    private val emphasized = PathInterpolator(0.4f, 0f, 0.2f, 1f)
    private var folderIconCache: Bitmap? = null
    private var folderIconKey = ""

    private val prefs by lazy { getSharedPreferences("home", MODE_PRIVATE) }

    private val userPresentReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            try {
                if (i.action == Intent.ACTION_USER_PRESENT) home.playIntro(fxIntro)
            } catch (e: Exception) {
                Log.e(TAG, "userPresent", e)
            }
        }
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            try { loadApps() } catch (e: Exception) { Log.e(TAG, "pkg", e) }
        }
    }

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() { handleBack() }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ---------------- Lifecycle ----------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!getSharedPreferences("home", MODE_PRIVATE).getBoolean("first_run_done", false)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        try {
            setupWindow()
            enableHighRefresh()
            loadSettings()
            repo = AppRepository(this, dp(96))
            popup = PopupMenu(this, POPUP_MS)
            sharedPool = RecyclerView.RecycledViewPool()
            loadFolders()
            buildViews()
            setContentView(root)
            showSnapshot()
            onBackPressedDispatcher.addCallback(this, backCallback)
            setProgress(0f)
            applyClock()
            applyDockStyle()
            widgets.restore()
            if (savedInstanceState != null) {
                home.currentPage = savedInstanceState.getInt("state_page", 0).coerceIn(0, max(0, home.pageCount - 1))
                savedInstanceState.getString("state_folder")?.let { if (folders.containsKey(it)) openFolder(it) }
            }
            registerReceiver(userPresentReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))
            registerReceiver(packageReceiver, IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            })
            repo.loadCached()?.let { applyApps(it) }
        } catch (e: Exception) {
            Log.e(TAG, "onCreate", e)
        }
    }

    override fun onStart() {
        super.onStart()
        if (::widgets.isInitialized) widgets.startListening()
    }

    override fun onResume() {
        super.onResume()
        try {
            if (!::home.isInitialized) return
            val wasReturning = pendingReturn || returningFromApp
            returningFromApp = false
            if (pendingReturn) resetLaunch()
            enableHighRefresh()
            loadSettings()
            applyIconStyle()
            applyClock()
            applyDockStyle()
            if (!appsLoaded || currentPack() != loadedPack) loadApps()
            if (wasReturning) {
                root.post {
                    playCloseAnim()
                    home.playIntro(fxIntro)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "onResume", e)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            if (!::home.isInitialized) return
            home.cancelPageAnim()
            folderView.close()
            popup.dismiss()
        } catch (e: Exception) {
            Log.e(TAG, "onPause", e)
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            if (!::home.isInitialized) return
            if (pendingReturn) returningFromApp = true else captureSnapshot()
            drawerView.cancelAnim()
            widgets.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "onStop", e)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        try {
            if (!::home.isInitialized) return
            outState.putInt("state_page", home.currentPage)
            outState.putString("state_folder", if (folderView.isOpen) folderView.openId else null)
        } catch (e: Exception) {
            Log.e(TAG, "save", e)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        try {
            if (level >= TRIM_MEMORY_RUNNING_LOW) {
                if (::repo.isInitialized) repo.trim()
                folderIconCache = null
                folderIconKey = ""
            }
            if (level >= TRIM_MEMORY_UI_HIDDEN && ::sharedPool.isInitialized) sharedPool.clear()
        } catch (e: Exception) {
            Log.e(TAG, "trim", e)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        try {
            if (!::home.isInitialized) return
            if (pendingReturn) resetLaunch()
            if (folderView.isOpen) folderView.closeInstant()
            if (drawerView.searching) drawerView.setSearch(false, imm())
            if (::searchBar.isInitialized && searchBar.isShown()) searchBar.hide()
            if (drawerView.progress > 0.01f) animateDrawer(0f, 0f)
        } catch (e: Exception) {
            Log.e(TAG, "newIntent", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(userPresentReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(packageReceiver) } catch (_: Exception) {}
        try {
            if (::home.isInitialized) {
                home.cancelPageAnim()
                drawerView.cancelAnim()
            }
            launchAnim?.cancel()
            closeAnimator?.cancel()
            if (::popup.isInitialized) popup.dismiss()
        } catch (e: Exception) {
            Log.e(TAG, "destroy", e)
        }
        if (::repo.isInitialized) repo.shutdown()
        if (::sharedPool.isInitialized) sharedPool.clear()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (::widgets.isInitialized) widgets.onActivityResult(requestCode, resultCode)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        handleBack()
    }

    private fun handleBack() {
        try {
            when {
                ::searchBar.isInitialized && searchBar.isShown() -> searchBar.hide()
                folderView.isOpen -> folderView.close()
                pendingReturn -> resetLaunch()
                drawerView.searching -> drawerView.setSearch(false, imm())
                drawerView.progress > 0.5f -> animateDrawer(0f, 0f)
                home.currentPage > 0 -> home.goToPage(home.currentPage - 1, true, pageAnim)
                else -> moveTaskToBack(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "back", e)
        }
    }

    private fun imm() = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager

    private fun loadSettings() {
        val s = getSharedPreferences("settings", MODE_PRIVATE)
        fxBlur = s.getBoolean("fx_blur", false)
        iconBlurOn = s.getBoolean("icon_blur", false)
        fxIntro = s.getBoolean("fx_intro", false)
        pageAnim = s.getBoolean("page_anim", true)
        customAnim = s.getBoolean("custom_anim", true)
        closeAnimOn = s.getBoolean("close_anim", true)
        animMs = s.getInt("anim_ms", 497).toLong()
    }

    // ---------------- Snapshot ----------------

    private fun captureSnapshot() {
        try {
            val w = root.width
            val h = root.height
            if (w <= 0 || h <= 0) return
            val s = 0.5f
            val bmp = Bitmap.createBitmap((w * s).toInt(), (h * s).toInt(), Bitmap.Config.RGB_565)
            val c = Canvas(bmp)
            c.scale(s, s)
            val ov = overlay.visibility
            val ss = stage.scaleX
            val sa = stage.alpha
            try {
                overlay.visibility = View.GONE
                stage.scaleX = 1f
                stage.scaleY = 1f
                stage.alpha = 1f
                root.draw(c)
            } finally {
                stage.scaleX = ss
                stage.scaleY = ss
                stage.alpha = sa
                overlay.visibility = ov
            }
            val out = File(filesDir, SNAP_FILE)
            Thread {
                try { FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) } }
                catch (e: Exception) { Log.e(TAG, "snap", e) }
            }.start()
        } catch (e: Exception) {
            Log.e(TAG, "captureSnapshot", e)
        }
    }

    private fun showSnapshot() {
        try {
            val f = File(filesDir, SNAP_FILE)
            if (!f.exists()) return
            val bmp = BitmapFactory.decodeFile(f.path) ?: return
            val iv = ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_XY
                setImageBitmap(bmp)
            }
            root.addView(iv, FrameLayout.LayoutParams(MATCH, MATCH))
            root.post {
                iv.animate().alpha(0f).setDuration(220).withEndAction {
                    try { root.removeView(iv) } catch (_: Exception) {}
                }.start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "showSnapshot", e)
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
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
    }

    private fun buildViews() {
        home = HomeScreen(this, sharedPool, { v, i -> launch(v, i) }, { dp(it) }, this)
        home.setPageCount(loadPageCount())
        loadSlots()
        home.rebuildPages { resolve(it) }

        drawerView = DrawerView(
            this, emphasized,
            { v, i -> launch(v, i) },
            { drawerView.setSearch(!drawerView.searching, imm()) },
            { startActivity(Intent(this, SettingsActivity::class.java)) },
            sharedPool,
            { dp(it) },
            { p -> onDrawerProgress(p) }
        )

        drawerView.list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                lastDrawerScrollAt = System.currentTimeMillis()
                if (newState != RecyclerView.SCROLL_STATE_IDLE) root.cancelLongTimer()
            }

            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                lastDrawerScrollAt = System.currentTimeMillis()
                root.cancelLongTimer()
            }
        })

        if (FeatureFlags.isCategoryDrawer(this)) {
            val cat = CategoryDrawer(this, { v, i -> launch(v, i) }, { _, _, _ -> })
            drawerView.list.visibility = View.GONE
            drawerView.layer.addView(cat.view, 0, FrameLayout.LayoutParams(MATCH, MATCH))
            categoryDrawer = cat
        }
        if (FeatureFlags.glassOn(this, FeatureFlags.KEY_GLASS_DRAWER)) {
            drawerView.layer.background = GlassBackground.drawerScreen(this)
        }

        folderView = FolderView(
            this, emphasized,
            { v, i -> launch(v, i) },
            { showFolderItemMenu(it) },
            { applyStageBlur(it) },
            sharedPool
        ) { dp(it) }

        widgets = WidgetManager(this, home.widgetBox, prefs, HOST_ID, REQ_BIND, REQ_CONFIG, { dp(it) }) { remove ->
            val c = center()
            popup.show(c.first, c.second, "Widget", listOf("Remove widget" to remove))
        }

        overlay = LaunchOverlay(this).apply {
            visibility = View.GONE
            isClickable = true
        }

        stage = FrameLayout(this)
        stage.addView(home.layer, FrameLayout.LayoutParams(MATCH, MATCH))
        stage.addView(drawerView.layer, FrameLayout.LayoutParams(MATCH, MATCH))
        stage.addView(home.removeZone, FrameLayout.LayoutParams(MATCH, dp(56), android.view.Gravity.TOP).apply {
            setMargins(dp(16), dp(8), dp(16), 0)
        })

        root = GestureRoot(this, this)
        root.addView(stage, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(folderView.layer, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(overlay, FrameLayout.LayoutParams(MATCH, MATCH))

        searchBar = SearchBar(this, { dp(it) }, { v, i -> launch(v, i) }) { p ->
    home.layer.alpha = 1f - p
    home.layer.visibility = if (p > 0.999f) View.INVISIBLE else View.VISIBLE
        }
        root.addView(searchBar.layer, FrameLayout.LayoutParams(MATCH, MATCH))

        // Home par double tap: screen lock (Device Admin permission ke baad)
        val doubleTap = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (drawerView.progress < 0.01f && !folderView.isOpen) {
                    if (!LockAdmin.lockNow(this@MainActivity)) requestLockAdmin()
                }
                return true
            }
        })
        home.layer.setOnTouchListener { _, e -> doubleTap.onTouchEvent(e); false }

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            applyInsets(bars.top, bars.bottom, ime.bottom)
            insets
        }

        applyIconStyle()
    }

    private fun requestLockAdmin() {
        val i = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, LockAdmin.component(this))
        i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Double tap se screen lock karne ke liye.")
        startActivity(i)
    }

    private fun center(): Pair<Float, Float> =
        Pair(resources.displayMetrics.widthPixels / 2f, resources.displayMetrics.heightPixels / 2f)

    private fun applyInsets(top: Int, bottom: Int, ime: Int) {
        try {
            if (top == lastTop && bottom == lastBottom && ime == lastIme) return
            lastTop = top
            lastBottom = bottom
            lastIme = ime
            home.layer.setPadding(0, top + dp(24), 0, bottom)
            (home.removeZone.layoutParams as FrameLayout.LayoutParams).topMargin = top + dp(8)
            drawerView.setInsets(top, bottom, ime)
         if (::searchBar.isInitialized) searchBar.setTopInset(top)
        } catch (e: Exception) {
            Log.e(TAG, "insets", e)
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
            Log.e(TAG, "refresh", e)
        }
    }

    // ---------------- Blur ----------------

    private fun blurFx(radius: Float): RenderEffect? =
        if (radius < 0.5f || Build.VERSION.SDK_INT < 31) null
        else RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)

    private fun applyHomeBlur(p: Float) {
        if (Build.VERSION.SDK_INT < 31) return
        val step = if (!fxBlur) 0 else (p.coerceIn(0f, 1f) * 12f).roundToInt()
        if (step == lastHomeStep) return
        lastHomeStep = step
        home.layer.setRenderEffect(blurFx(dp(16).toFloat() * step / 12f))
    }

    private fun applyStageBlur(f: Float) {
        if (Build.VERSION.SDK_INT < 31) return
        val step = if (!fxBlur) 0 else (f.coerceIn(0f, 1f) * 12f).roundToInt()
        if (step == lastStageStep) return
        lastStageStep = step
        stage.setRenderEffect(blurFx(dp(18).toFloat() * step / 12f))
    }

    private fun applyIconBlur(p: Float) {
        if (Build.VERSION.SDK_INT < 31) return
        val step = if (!iconBlurOn) 0 else (p.coerceIn(0f, 1f) * 8f).roundToInt()
        if (step == lastIconStep) return
        lastIconStep = step
        val fx = blurFx(dp(6).toFloat() * step / 8f)
        for (g in home.pageGrids) g.setRenderEffect(fx)
        home.dock.setRenderEffect(fx)
    }

    // ---------------- Drawer progress ----------------

    private fun onDrawerProgress(p: Float) {
        if (p < 0.001f) categoryDrawer?.showCategories()
        applyHomeBlur(p)
        val s = 1f - 0.06f * p
        home.layer.scaleX = s
        home.layer.scaleY = s
        home.layer.alpha = 1f - p
        val hv = if (p > 0.999f) View.INVISIBLE else View.VISIBLE
        if (home.layer.visibility != hv) home.layer.visibility = hv
        if (p < 0.001f && drawerView.searching) drawerView.setSearch(false, imm())
    }

    private fun setProgress(p: Float) {
        drawerView.setProgress(p)
    }

    private fun animateDrawer(target: Float, velocity: Float) {
        setLayers(true)
        drawerView.animateTo(target, velocity) { setLayers(false) }
    }

    private fun setLayers(on: Boolean) {
        val t = if (on) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_NONE
        home.layer.setLayerType(t, null)
        drawerView.layer.setLayerType(t, null)
    }

    // ---------------- GestureRoot.Host ----------------

    override fun canStartDrag(dy: Float): Boolean {
        if (home.dragLock || folderView.isOpen || drawerView.searching || pendingReturn) return false
        return if (drawerView.progress <= 0.5f) dy < 0
        else dy > 0 && !drawerView.list.canScrollVertically(-1)
    }

    override fun onDragStart() {
        drawerView.cancelAnim()
        dragBase = drawerView.progress
        setLayers(true)
    }

    override fun onDrag(dy: Float) {
        drawerView.setProgress((dragBase - dy / dist()).coerceIn(0f, 1f))
    }

    override fun onDragEnd(vy: Float) {
        val v = -vy / dist()
        val open = when {
            v > 0.6f -> true
            v < -0.6f -> false
            else -> drawerView.progress > 0.5f
        }
        animateDrawer(if (open) 1f else 0f, v)
    }

    private fun dist() = maxOf(1f, root.height * 0.5f)

    override fun canStartPageSwipe(dx: Float): Boolean {
        if (home.dragLock || folderView.isOpen || drawerView.progress > 0.01f || pendingReturn) return false
        return if (dx < 0) home.currentPage < home.pageCount - 1 else home.currentPage > 0
    }

    override fun onPageDrag(dx: Float) {
        try {
            home.cancelPageAnim()
            val base = -home.currentPage * home.pageWidth()
            var target = base + dx
            val minX = -(home.pageCount - 1) * home.pageWidth()
            if (target > 0f) target *= 0.3f
            if (target < minX) target = minX + (target - minX) * 0.3f
            home.setPageOffset(target)
        } catch (e: Exception) {
            Log.e(TAG, "pageDrag", e)
        }
    }

    override fun onPageDragEnd(vx: Float) {
        try {
            val cur = -home.pageOffset / home.pageWidth()
            val next = when {
                vx < -800f -> home.currentPage + 1
                vx > 800f -> home.currentPage - 1
                else -> cur.roundToInt()
            }
            home.goToPage(next.coerceIn(0, max(0, home.pageCount - 1)), true, pageAnim)
        } catch (e: Exception) {
            Log.e(TAG, "pageDragEnd", e)
        }
    }

    override fun isBusy(): Boolean =
        launchAnim?.isRunning == true || drawerView.isAnimating() ||
            home.isPageAnimating() || closeAnimator?.isRunning == true

    override fun canLongPress(): Boolean {
        if (folderView.isOpen || pendingReturn || home.dragLock) return false
        if (drawerView.list.scrollState != RecyclerView.SCROLL_STATE_IDLE) return false
        if (drawerView.list.isComputingLayout) return false
        if (home.isPageAnimating()) return false
        if (System.currentTimeMillis() - lastDrawerScrollAt < 400L) return false
        return true
    }

    override fun onLongPress(rawX: Float, rawY: Float) {
        try {
            if (drawerView.progress > 0.5f) {
                val item = drawerItemAt(rawX, rawY) ?: return
                window.decorView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                showAppMenu(item, rawX, rawY)
                return
            }
            if (drawerView.progress > 0.01f) return
            if (home.isOnAnyItem(rawX, rawY)) {
                home.pageGrids.getOrNull(home.currentPage)?.let { waveFrom(it, rawX, rawY) }
                return
            }
            if (home.inside(home.widgetBox, rawX, rawY)) return
            window.decorView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            showHomeMenu(rawX, rawY)
        } catch (e: Exception) {
            Log.e(TAG, "longPress", e)
        }
    }

    override fun onSearchSwipe() {
        if (home.dragLock || folderView.isOpen || drawerView.progress > 0.01f || pendingReturn) return
        if (::searchBar.isInitialized) searchBar.show()
    }

    private fun drawerItemAt(rawX: Float, rawY: Float): AppItem? {
        try {
            if (folderView.isOpen || drawerView.progress < 0.5f) return null
            val rv = drawerView.list
            val loc = IntArray(2)
            rv.getLocationOnScreen(loc)
            val child = rv.findChildViewUnder(rawX - loc[0], rawY - loc[1]) ?: return null
            val pos = rv.getChildAdapterPosition(child)
            if (pos == RecyclerView.NO_POSITION) return null
            return drawerView.adapter.itemAt(pos)
        } catch (e: Exception) {
            Log.e(TAG, "drawerItemAt", e)
            return null
        }
    }

    private fun waveFrom(rv: RecyclerView, rawX: Float, rawY: Float) {
        try {
            val loc = IntArray(2)
            for (i in 0 until rv.childCount) {
                val v = rv.getChildAt(i)
                v.getLocationOnScreen(loc)
                val cx = loc[0] + v.width / 2f
                val cy = loc[1] + v.height / 2f
                val d = Math.hypot((cx - rawX).toDouble(), (cy - rawY).toDouble()).toFloat()
                val delay = (d * 0.35f).toLong().coerceAtMost(420L)
                v.animate().cancel()
                v.animate().scaleX(0.9f).scaleY(0.9f).setStartDelay(delay).setDuration(120)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(320)
                            .setInterpolator(OvershootInterpolator(2f)).start()
                    }.start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "wave", e)
        }
    }

    // ---------------- HomeCallbacks ----------------

    override fun onPagesChanged() {
        try {
            home.rebuildPages { resolve(it) }
        } catch (e: Exception) {
            Log.e(TAG, "pagesChanged", e)
        }
    }

    override fun onPersist() = persist()

    override fun onToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onFolderCreated(id: String) {}

    override fun onRemoveFolder(id: String) {
        folders.remove(id)
    }

    override fun resolve(key: String): AppItem? {
        if (key.startsWith(FOLDER_PREFIX)) {
            val id = key.removePrefix(FOLDER_PREFIX)
            val f = folders[id] ?: return null
            return AppItem(f.name, key, Intent(Intent.ACTION_MAIN), folderIcon(f))
        }
        return allApps.firstOrNull { it.key == key }
    }

    override fun folderName(id: String): String? = folders[id]?.name

    override fun folderKeys(id: String): MutableList<String>? = folders[id]?.keys

    override fun createFolder(id: String, a: String, b: String) {
        folders[id] = Folder("Folder", mutableListOf(a, b))
    }

    override fun deleteFolder(id: String) {
        folders.remove(id)
    }

    override fun pageIsOpen(): Boolean = folderView.isOpen

    private fun folderIcon(f: Folder): Bitmap {
        val items = f.keys.mapNotNull { k -> allApps.firstOrNull { it.key == k } }
        val sig = f.name + "|" + items.joinToString(",") { it.key }
        folderIconCache?.let { if (folderIconKey == sig) return it }
        val bmp = makeFolderIcon(items)
        folderIconKey = sig
        folderIconCache = bmp
        return bmp
    }

    private fun makeFolderIcon(items: List<AppItem>): Bitmap {
        val size = dp(96)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        try {
            c.drawRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), Paint().apply { color = 0xCC2A2A30.toInt() })
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

    // ---------------- Folder ----------------

    private fun openFolder(id: String) {
        val f = folders[id] ?: return
        val apps = f.keys.mapNotNull { k -> allApps.firstOrNull { it.key == k } }
        folderView.open(id, f.name, apps)
    }

    private fun showFolderItemMenu(item: AppItem) {
        val id = folderView.openId ?: return
        val pkg = item.intent.component?.packageName ?: return
        val c = center()
        popup.show(c.first, c.second, item.label, listOf(
            "Remove from folder" to { removeFromFolder(id, item.key) },
            "App info" to { openAppInfo(pkg) }
        ))
    }

    private fun removeFromFolder(id: String, key: String) {
        val f = folders[id] ?: return
        val free = home.homeSlots.indexOf(null)
        if (free < 0) {
            onToast("Home screen full hai")
            return
        }
        f.keys.remove(key)
        home.homeSlots[free] = key
        if (f.keys.size <= 1) {
            val slot = home.homeSlots.indexOf(FOLDER_PREFIX + id)
            val left = f.keys.firstOrNull()
            folders.remove(id)
            if (slot >= 0) home.homeSlots[slot] = left
            folderView.closeInstant()
        }
        persist()
        onPagesChanged()
    }

    // ---------------- Popup menus ----------------

    private fun showAppMenu(item: AppItem, rawX: Float, rawY: Float) {
        val pkg = item.intent.component?.packageName ?: return
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        actions.add("Add to Home" to { addTo(home.homeSlots, item.key, "Home") })
        actions.add("Add to Dock" to { addTo(home.dockSlots, item.key, "Dock") })
        actions.add("App info" to { openAppInfo(pkg) })
        if (isUserApp(pkg)) actions.add("Uninstall" to { uninstallApp(pkg) })
        popup.show(rawX, rawY, item.label, actions)
    }

    private fun showHomeMenu(rawX: Float, rawY: Float) {
        val clockOn = prefs.getBoolean("clock", true)
        popup.show(rawX, rawY, "Home screen", listOf(
            "Add widget" to { pickWidget() },
            (if (clockOn) "Hide clock" else "Show clock") to {
                prefs.edit().putBoolean("clock", !clockOn).apply()
                applyClock()
            },
            "Launcher settings" to { startActivity(Intent(this, SettingsActivity::class.java)) }
        ))
    }

    private fun pickWidget() {
        val providers = widgets.installedProviders()
        if (providers.isEmpty()) {
            onToast("Koi widget installed nahi hai")
            return
        }
        val c = center()
        popup.show(c.first, c.second, "Add widget",
            providers.map { info: AppWidgetProviderInfo -> info.loadLabel(packageManager).toString() to { widgets.bind(info) } })
    }

    private fun isUserApp(pkg: String): Boolean = try {
        val ai = packageManager.getApplicationInfo(pkg, 0)
        (ai.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
            (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    } catch (e: Exception) {
        false
    }

    private fun openAppInfo(pkg: String) {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
        } catch (e: Exception) {
            Log.e(TAG, "appInfo", e)
        }
    }

    private fun uninstallApp(pkg: String) {
        try {
            val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg"))
            if (intent.resolveActivity(packageManager) != null) startActivity(intent) else openAppInfo(pkg)
        } catch (e: Exception) {
            Log.e(TAG, "uninstall", e)
            openAppInfo(pkg)
        }
    }

    private fun addTo(slots: MutableList<String?>, key: String, name: String) {
        if (key in slots || folders.values.any { key in it.keys } || key in home.homeSlots) {
            onToast("Already in $name")
            return
        }
        var idx = slots.indexOf(null)
        if (name == "Home" && idx == -1 && home.pageCount < MAX_PAGES) {
            home.addPage()
            idx = home.homeSlots.indexOf(null)
        }
        if (idx == -1) {
            onToast("$name is full")
            return
        }
        slots[idx] = key
        persist()
        onPagesChanged()
        onToast("Added to $name")
    }

    // ---------------- Launch and close ----------------

    private fun launch(view: View, item: AppItem) {
        try {
            if (item.key.startsWith(FOLDER_PREFIX)) {
                openFolder(item.key.removePrefix(FOLDER_PREFIX))
                return
            }
            if (pendingReturn) return
            if (drawerView.searching) drawerView.setSearch(false, imm())
            if (::searchBar.isInitialized && searchBar.isShown()) searchBar.hide()
            popup.dismiss()

            val loc = IntArray(2)
            val rl = IntArray(2)
            view.getLocationInWindow(loc)
            root.getLocationInWindow(rl)
            val l = (loc[0] - rl[0]).toFloat()
            val t = (loc[1] - rl[1]).toFloat()
            stage.pivotX = l + view.width / 2f
            stage.pivotY = t + view.height / 2f
            pendingReturn = true

            if (customAnim) {
                overlay.prepare(l, t, l + view.width, t + view.height, avgColor(item.icon), item.icon)
                overlay.visibility = View.VISIBLE
                animateLaunch(1f, animMs) { startApp(item) }
            } else {
                startApp(item)
            }
        } catch (e: Exception) {
            Log.e(TAG, "launch", e)
            resetLaunch()
        }
    }

    private fun startApp(item: AppItem) {
        try {
            drawerView.setSearch(false, imm())
            val opts = ActivityOptions.makeCustomAnimation(this, 0, 0)
            startActivity(item.intent, opts.toBundle())
            folderView.closeInstant()
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
            applyIconBlur(0f)
        } catch (e: Exception) {
            Log.e(TAG, "resetLaunch", e)
        }
    }

    private fun playCloseAnim() {
        try {
            if (!closeAnimOn) {
                stage.alpha = 1f
                stage.scaleX = 1f
                stage.scaleY = 1f
                return
            }
            closeAnimator?.cancel()
            stage.alpha = 0f
            stage.scaleX = 0.96f
            stage.scaleY = 0.96f
            val a = ValueAnimator.ofFloat(0f, 1f)
            a.duration = 260
            a.interpolator = emphasized
            a.addUpdateListener {
                val v = it.animatedValue as Float
                stage.alpha = v
                val s = 0.96f + 0.04f * v
                stage.scaleX = s
                stage.scaleY = s
                applyIconBlur(1f - v)
            }
            closeAnimator = a
            a.start()
        } catch (e: Exception) {
            Log.e(TAG, "closeAnim", e)
        }
    }

    private fun applyDepth(d: Float) {
        depth = d
        stage.scaleX = 1f + 0.14f * d
        stage.scaleY = 1f + 0.14f * d
        stage.alpha = (1f - 0.6f * d.coerceIn(0f, 1f)).coerceIn(0f, 1f)
    }

    private fun animateLaunch(to: Float, duration: Long, onEnd: () -> Unit) {
        launchAnim?.cancel()
        stage.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        val a = ValueAnimator.ofFloat(depth, to)
        a.duration = duration
        a.interpolator = emphasized
        a.addUpdateListener {
            val v = it.animatedValue as Float
            applyDepth(v)
            overlay.t = v
            applyIconBlur(v)
        }
        a.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: Animator) { cancelled = true }
            override fun onAnimationEnd(animation: Animator) {
                if (!cancelled) {
                    stage.setLayerType(View.LAYER_TYPE_NONE, null)
                    onEnd()
                }
            }
        })
        launchAnim = a
        a.start()
    }

    private fun avgColor(b: Bitmap): Int =
        Bitmap.createScaledBitmap(b, 1, 1, true).getPixel(0, 0) or 0xFF000000.toInt()

    // ---------------- Apps, prefs, persist ----------------

    private fun currentPack(): String =
        getSharedPreferences("settings", MODE_PRIVATE).getString("icon_pack", "") ?: ""

    private fun loadApps() {
        repo.scan { apps ->
            runOnUiThread {
                try {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    applyApps(apps)
                    appsLoaded = true
                    loadedPack = currentPack()
                } catch (e: Exception) {
                    Log.e(TAG, "appsUi", e)
                }
            }
        }
    }

    private fun applyApps(apps: List<AppItem>) {
        allApps = apps
        drawerView.submitApps(apps)
        categoryDrawer?.submitApps(apps)
        if (::searchBar.isInitialized) searchBar.submitApps(apps)
        onPagesChanged()
    }

    private fun applyClock() {
        val on = prefs.getBoolean("clock", true)
        home.clock.visibility = if (on) View.VISIBLE else View.GONE
        home.date.visibility = if (on) View.VISIBLE else View.GONE
    }

    private fun applyIconStyle() {
        try {
            val s = readIconStyle(this)
            drawerView.setStyle(s)
            categoryDrawer?.applyStyle(s)
            home.setDockIconDp(dockIconDp())
            for (a in home.pageAdapters) a.applyStyle(s.copy(labels = false))
            home.dockAdapter.applyStyle(s.copy(sizeDp = dockIconDp(), labels = false))
        } catch (e: Exception) {
            Log.e(TAG, "iconStyle", e)
        }
    }

    private fun applyDockStyle() {
        try {
            val sp = getSharedPreferences("settings", MODE_PRIVATE)
            val size = sp.getInt("dock_size", 1)
            val pos = sp.getInt("dock_pos", 0)
            val pad = when (size) { 0 -> 4; 2 -> 10; else -> 6 }
            home.dock.setPadding(dp(pad), dp(pad), dp(pad), dp(pad))
            val bottomMargin = if (pos == 1) dp(40) else dp(14)
            home.dockHolder.layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
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

    private fun loadPageCount(): Int = prefs.getInt("home_pages", 1).coerceIn(1, MAX_PAGES)

    private fun loadSlots() {
        val h = (prefs.getString("home", "") ?: "").split("|")
        for (i in home.homeSlots.indices) home.homeSlots[i] = h.getOrNull(i)?.takeIf { it.isNotEmpty() }
        val d = (prefs.getString("dock", "") ?: "").split("|")
        for (i in home.dockSlots.indices) home.dockSlots[i] = d.getOrNull(i)?.takeIf { it.isNotEmpty() }
    }

    private fun loadFolders() {
        try {
            folders.clear()
            (prefs.getString("folders", "") ?: "").split("\n").filter { it.isNotEmpty() }.forEach { line ->
                val p = line.split("\t")
                if (p.size < 3) return@forEach
                folders[p[0]] = Folder(p[1], p[2].split("|").filter { it.isNotEmpty() }.toMutableList())
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadFolders", e)
        }
    }

    private fun persist() {
        try {
            val foldersText = folders.entries.joinToString("\n") {
                it.key + "\t" + it.value.name + "\t" + it.value.keys.joinToString("|")
            }
            prefs.edit()
                .putString("home", home.homeSlots.joinToString("|") { it ?: "" })
                .putString("dock", home.dockSlots.joinToString("|") { it ?: "" })
                .putString("folders", foldersText)
                .putInt("home_pages", home.pageCount)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "persist", e)
        }
    }

    companion object {
        private const val HOST_ID = 1024
        private const val REQ_BIND = 11
        private const val REQ_CONFIG = 12
        private const val POPUP_MS = 260L
    }
}
