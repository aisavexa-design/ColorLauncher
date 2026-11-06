package com.colorlauncher

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

private const val FILL = ViewGroup.LayoutParams.MATCH_PARENT
private const val HUG = ViewGroup.LayoutParams.WRAP_CONTENT

private class Pal(night: Boolean) {
    val bg = if (night) 0xFF0C0C0F.toInt() else 0xFFF2F3F7.toInt()
    val card = if (night) 0xFF1B1B20.toInt() else 0xFFFFFFFF.toInt()
    val text = if (night) 0xFFFFFFFF.toInt() else 0xFF111114.toInt()
    val sub = if (night) 0xFF9A9AA2.toInt() else 0xFF7A7A82.toInt()
    val off = if (night) 0xFF3A3A42.toInt() else 0xFFD5D6DC.toInt()
    val accent = 0xFF22C55E.toInt()
}

class ToggleView(ctx: Context, private val accent: Int, private val off: Int, checked: Boolean) : View(ctx) {
    var checked = checked
        private set
    var onChange: ((Boolean) -> Unit)? = null
    private var pos = if (checked) 1f else 0f
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumb = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val rect = RectF()

    init {
        setOnClickListener {
            this.checked = !this.checked
            animatePos()
            onChange?.invoke(this.checked)
        }
    }

    private fun animatePos() {
        val target = if (this.checked) 1f else 0f
        val a = ValueAnimator.ofFloat(pos, target)
        a.duration = 180
        a.addUpdateListener {
            pos = it.animatedValue as Float
            invalidate()
        }
        a.start()
    }

    override fun onMeasure(w: Int, h: Int) {
        val d = resources.displayMetrics.density
        setMeasuredDimension((52 * d).toInt(), (32 * d).toInt())
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        track.color = ColorUtils.blendARGB(off, accent, pos)
        rect.set(0f, 0f, w, h)
        c.drawRoundRect(rect, h / 2, h / 2, track)
        val r = h / 2 - h * 0.14f
        val cx = h / 2 + (w - h) * pos
        c.drawCircle(cx, h / 2, r, thumb)
    }
}

class SliderView(
    ctx: Context,
    private val accent: Int,
    private val trackColor: Int,
    private val min: Int,
    private val max: Int,
    start: Int,
    private val step: Int = 1
) : View(ctx) {
    var value = start.coerceIn(min, max)
        private set
    var onChange: ((Int) -> Unit)? = null
    private val d = resources.displayMetrics.density
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumb = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), (40 * d).toInt())
    }

    private fun setFromX(x: Float) {
        val pad = 14 * d
        val f = ((x - pad) / (width - 2 * pad)).coerceIn(0f, 1f)
        val raw = min + f * (max - min)
        val v = (Math.round(raw / step) * step).coerceIn(min, max)
        if (v != value) {
            value = v
            invalidate()
            onChange?.invoke(v)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                setFromX(e.x)
            }
        }
        return true
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val pad = 14 * d
        val f = (value - min).toFloat() / (max - min)
        val x = pad + (w - 2 * pad) * f
        val th = 6 * d
        track.color = trackColor
        rect.set(pad, h / 2 - th / 2, w - pad, h / 2 + th / 2)
        c.drawRoundRect(rect, th / 2, th / 2, track)
        track.color = accent
        rect.set(pad, h / 2 - th / 2, x, h / 2 + th / 2)
        c.drawRoundRect(rect, th / 2, th / 2, track)
        c.drawCircle(x, h / 2, 11 * d, thumb)
        ring.color = accent
        ring.strokeWidth = 3 * d
        c.drawCircle(x, h / 2, 11 * d - 1.5f * d, ring)
    }
}

class SettingsActivity : Activity() {

    private lateinit var pal: Pal
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        pal = Pal(night)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.setBackgroundDrawable(ColorDrawable(pal.bg))
        val ctrl = WindowCompat.getInsetsController(window, window.decorView)
        ctrl.isAppearanceLightStatusBars = !night
        ctrl.isAppearanceLightNavigationBars = !night

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(32))
        }
        content.addView(TextView(this).apply {
            text = "Launcher settings"
            textSize = 32f
            setTextColor(pal.text)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8), dp(28), dp(8), dp(16))
        })

        content.addView(sectionTitle("Animation"))
        content.addView(card().apply {
            addView(
                switchRow(
                    "Launcher animation",
                    "Opens from the app icon. Turn off to use the system animation.",
                    prefs.getBoolean("custom_anim", true)
                ) { prefs.edit().putBoolean("custom_anim", it).apply() }
            )
            addView(divider())
            addView(
                sliderRow(
                    "App open speed", 150, 600, prefs.getInt("anim_ms", 300), "ms",
                    "Lower value = faster animation"
                ) { prefs.edit().putInt("anim_ms", it).apply() }
            )
        })

        content.addView(sectionTitle("Effects"))
        content.addView(card().apply {
            addView(
                switchRow(
                    "Icon blur",
                    "Blurs only the icons while an app opens or closes (Android 12+).",
                    prefs.getBoolean("icon_blur", true)
                ) { prefs.edit().putBoolean("icon_blur", it).apply() }
            )
        })

        content.addView(sectionTitle("Icons"))
        content.addView(card().apply {
            addView(
                sliderRow(
                    "Icon size", 40, 80, prefs.getInt("icon_size", 60), "dp",
                    "Home, dock and app drawer icons"
                ) { prefs.edit().putInt("icon_size", it).apply() }
            )
            addView(divider())
            addView(shapeRow())
            addView(divider())
            addView(
                switchRow(
                    "Icon labels",
                    "Show app names under icons",
                    prefs.getBoolean("icon_label", true)
                ) { prefs.edit().putBoolean("icon_label", it).apply() }
            )
        })

        content.addView(sectionTitle("Icon pack"))
        content.addView(card().apply {
            addView(packRow())
        })

        content.addView(sectionTitle("App drawer spacing"))
        content.addView(card().apply {
            addView(
                sliderRow(
                    "Horizontal gap", 0, 40, prefs.getInt("gap_h", 8), "dp",
                    "Space between icons left and right"
                ) { prefs.edit().putInt("gap_h", it).apply() }
            )
            addView(divider())
            addView(
                sliderRow(
                    "Vertical gap", 0, 40, prefs.getInt("gap_v", 12), "dp",
                    "Space between icon rows"
                ) { prefs.edit().putInt("gap_v", it).apply() }
            )
        })

        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            addView(content)
        }
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { _, insets ->
            val b = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            scroll.setPadding(0, b.top, 0, b.bottom)
            insets
        }
        setContentView(scroll)
    }

    private fun sectionTitle(t: String): TextView = TextView(this).apply {
        text = t
        textSize = 13f
        setTextColor(pal.sub)
        setPadding(dp(12), dp(16), dp(12), dp(8))
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            cornerRadius = dp(24).toFloat()
            setColor(pal.card)
        }
        setPadding(dp(4), dp(4), dp(4), dp(4))
    }

    private fun divider(): View = View(this).apply {
        setBackgroundColor(ColorUtils.setAlphaComponent(pal.sub, 50))
        layoutParams = LinearLayout.LayoutParams(FILL, 1).apply {
            setMargins(dp(16), 0, dp(16), 0)
        }
    }

    private fun texts(heading: String, desc: String): LinearLayout {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.addView(TextView(this).apply {
            text = heading
            textSize = 16f
            setTextColor(pal.text)
        })
        if (desc.isNotEmpty()) {
            col.addView(TextView(this).apply {
                text = desc
                textSize = 13f
                setTextColor(pal.sub)
                setPadding(0, dp(2), 0, 0)
            })
        }
        return col
    }

    private fun switchRow(heading: String, desc: String, on: Boolean, onChange: (Boolean) -> Unit): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(16), dp(14), dp(16), dp(14))
        row.addView(texts(heading, desc), LinearLayout.LayoutParams(0, HUG, 1f))
        val t = ToggleView(this, pal.accent, pal.off, on)
        t.onChange = onChange
        row.addView(t, LinearLayout.LayoutParams(HUG, HUG).apply { marginStart = dp(12) })
        row.setOnClickListener { t.performClick() }
        return row
    }

    private fun sliderRow(
        heading: String, min: Int, max: Int, start: Int, unit: String, hint: String,
        step: Int = 1,
        onChange: (Int) -> Unit
    ): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(14), dp(16), dp(10))
        val valueText = TextView(this).apply {
            text = "$start $unit"
            textSize = 15f
            setTextColor(pal.accent)
            typeface = Typeface.DEFAULT_BOLD
        }
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.addView(TextView(this).apply {
            text = heading
            textSize = 16f
            setTextColor(pal.text)
        }, LinearLayout.LayoutParams(0, HUG, 1f))
        top.addView(valueText)
        val s = SliderView(this, pal.accent, pal.off, min, max, start, step)
        s.onChange = {
            valueText.text = "$it $unit"
            onChange(it)
        }
        col.addView(top)
        col.addView(s, LinearLayout.LayoutParams(FILL, HUG))
        col.addView(TextView(this).apply {
            text = hint
            textSize = 13f
            setTextColor(pal.sub)
        })
        return col
    }

    private fun shapeRow(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        col.addView(texts("Icon shape", "Clip style for home, dock and drawer icons"))
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }
        val names = listOf("Squircle", "Circle", "Square")
        val chips = mutableListOf<TextView>()
        fun paint() {
            val sel = prefs.getInt("icon_shape", 0)
            chips.forEachIndexed { i, c ->
                c.setTextColor(if (i == sel) Color.WHITE else pal.text)
                c.background = GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    setColor(if (i == sel) pal.accent else pal.off)
                }
            }
        }
        names.forEachIndexed { i, n ->
            val c = TextView(this).apply {
                text = n
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setOnClickListener {
                    prefs.edit().putInt("icon_shape", i).apply()
                    paint()
                }
            }
            chips.add(c)
            row.addView(c, LinearLayout.LayoutParams(0, HUG, 1f).apply { marginEnd = dp(8) })
        }
        paint()
        col.addView(row)
        return col
    }

    private fun packRow(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        col.addView(texts("Icon pack", "Tap to apply. Icon packs (Nova, Lawnchair, Action) are detected automatically."))
        val options = mutableListOf("" to "Default icons")
        options.addAll(IconPack.installed(this))
        val labels = mutableListOf<TextView>()
        fun paint() {
            val cur = prefs.getString("icon_pack", "") ?: ""
            labels.forEachIndexed { i, t ->
                val on = options[i].first == cur
                t.text = (if (on) "\u25CF  " else "\u25CB  ") + options[i].second
                t.setTextColor(if (on) pal.accent else pal.text)
            }
        }
        options.forEachIndexed { i, _ ->
            val t = TextView(this).apply {
                textSize = 15f
                setPadding(0, dp(10), 0, dp(4))
                setOnClickListener {
                    prefs.edit().putString("icon_pack", options[i].first).apply()
                    paint()
                }
            }
            labels.add(t)
            col.addView(t)
        }
        if (options.size == 1) {
            col.addView(TextView(this).apply {
                text = "No icon pack installed. Install one from Play Store, then reopen this screen."
                textSize = 13f
                setTextColor(pal.sub)
                setPadding(0, dp(8), 0, 0)
            })
        }
        paint()
        return col
    }
}
