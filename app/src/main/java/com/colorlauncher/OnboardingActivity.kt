package com.colorlauncher

import android.animation.ValueAnimator
import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Floating glow particle (background ke liye). */
private class Particle(val x: Float, val y: Float, val r: Float, val speed: Float, val alpha: Int)

/** Background: AMOLED black par floating red particles. */
private class ParticleView(ctx: Context) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val particles = mutableListOf<Particle>()
    private var phase = 0f

    init {
        val rnd = java.util.Random(7)
        repeat(18) {
            particles.add(
                Particle(
                    x = rnd.nextFloat(), y = rnd.nextFloat(),
                    r = 2f + rnd.nextFloat() * 6f,
                    speed = 0.02f + rnd.nextFloat() * 0.05f,
                    alpha = 40 + rnd.nextInt(90)
                )
            )
        }
    }

    fun tick(p: Float) {
        phase = p
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        for (pt in particles) {
            val y = ((pt.y - phase * pt.speed * 4f) % 1f + 1f) % 1f
            paint.color = Color.argb(pt.alpha, 255, 82, 82)
            canvas.drawCircle(pt.x * w, y * h, pt.r * resources.displayMetrics.density, paint)
        }
    }
}

/**
 * Premium English onboarding. AMOLED black, red glow orbs, floating particles,
 * liquid glass cards, page-wise icon animation, smooth transitions.
 * Last page par "Choose default launcher" aur "Created by Pratik".
 */
class OnboardingActivity : Activity() {

    private data class Page(val title: String, val subtitle: String, val body: String, val icon: String)

    private val pages = listOf(
        Page("Welcome", "Your new home screen", "Fast, fluid and built for a premium feel.", "\u2302"),
        Page("Smart drawer", "Simple A-Z or category view", "Pick the drawer style that suits you.", "\u2630"),
        Page("Liquid glass", "Glass on dock, folders and drawer", "Turn it on or off anytime in Settings.", "\u25C8"),
        Page("Ready", "Let's get started", "Choose ColorLauncher as your default home.", "\u2713")
    )

    private var index = 0
    private lateinit var root: FrameLayout
    private lateinit var particles: ParticleView
    private lateinit var orbA: View
    private lateinit var orbB: View
    private lateinit var card: LinearLayout
    private lateinit var iconView: TextView
    private lateinit var titleView: TextView
    private lateinit var subView: TextView
    private lateinit var bodyView: TextView
    private lateinit var dots: LinearLayout
    private lateinit var nextBtn: TextView
    private lateinit var skipBtn: TextView
    private var floatAnim: ValueAnimator? = null

    private val easeOut = PathInterpolator(0.2f, 0.8f, 0.2f, 1f)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        buildUi()
        showPage(0, animate = false)
        playEntrance()
        startFloat()
    }

    override fun onDestroy() {
        floatAnim?.cancel()
        super.onDestroy()
    }

    private fun orb(color: Int): View = View(this).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
        alpha = 0f
    }

    private fun buildUi() {
        root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            clipChildren = true
        }

        particles = ParticleView(this)
        root.addView(particles, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        orbA = orb(0x55E53935)
        orbB = orb(0x33FF5252)
        root.addView(orbA, FrameLayout.LayoutParams(dp(300), dp(300), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = dp(40)
        })
        root.addView(orbB, FrameLayout.LayoutParams(dp(220), dp(220), Gravity.BOTTOM or Gravity.START).apply {
            bottomMargin = dp(120)
            marginStart = -dp(60)
        })

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(56), dp(24), dp(28))
        }

        column.addView(TextView(this).apply {
            text = "COLORLAUNCHER"
            textSize = 12f
            letterSpacing = 0.3f
            setTextColor(0x99FFFFFF.toInt())
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(26), dp(36), dp(26), dp(36))
            background = GlassBackground.make(this@OnboardingActivity, 32f)
        }
        iconView = TextView(this).apply {
            textSize = 44f
            setTextColor(0xFFE53935.toInt())
            gravity = Gravity.CENTER
        }
        titleView = TextView(this).apply {
            textSize = 30f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
        }
        subView = TextView(this).apply {
            textSize = 18f
            setTextColor(0xFFF2D7D7.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        }
        bodyView = TextView(this).apply {
            textSize = 15f
            setTextColor(0xB3FFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        }
        card.addView(iconView)
        card.addView(titleView)
        card.addView(subView)
        card.addView(bodyView)

        column.addView(View(this), LinearLayout.LayoutParams(1, dp(36)))
        column.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        dots = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        column.addView(dots, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(26)
        })

        column.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))

        nextBtn = TextView(this).apply {
            textSize = 17f
            setTextColor(Color.BLACK)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(18))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(0xFFFFFFFF.toInt())
            }
            setOnClickListener { onNext() }
        }
        column.addView(nextBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        skipBtn = TextView(this).apply {
            text = "Skip"
            textSize = 14f
            setTextColor(0x99FFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
            setOnClickListener { finishOnboarding() }
        }
        column.addView(skipBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        column.addView(TextView(this).apply {
            text = "Created by Pratik"
            textSize = 13f
            letterSpacing = 0.1f
            setTextColor(0x80FFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, 0)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)
    }

    private fun showPage(i: Int, animate: Boolean) {
        index = i
        val p = pages[i]
        val isLast = i == pages.size - 1
        nextBtn.text = if (isLast) "Choose default launcher" else "Next"
        skipBtn.visibility = if (isLast) View.GONE else View.VISIBLE

        if (animate) {
            card.animate().alpha(0f).translationX(-dp(60).toFloat()).scaleX(0.92f).scaleY(0.92f)
                .setDuration(180).setInterpolator(easeOut).withEndAction {
                    setText(p)
                    card.translationX = dp(60).toFloat()
                    card.animate().alpha(1f).translationX(0f).scaleX(1f).scaleY(1f)
                        .setDuration(420).setInterpolator(OvershootInterpolator(0.6f)).start()
                    animateIcon()
                }.start()
        } else {
            setText(p)
            animateIcon()
        }
        drawDots()
    }

    /** Har page ka icon pop-in karke ghoomta hai. */
    private fun animateIcon() {
        iconView.scaleX = 0.4f
        iconView.scaleY = 0.4f
        iconView.rotation = -25f
        iconView.animate().scaleX(1f).scaleY(1f).rotation(0f)
            .setDuration(550).setStartDelay(120)
            .setInterpolator(OvershootInterpolator(1.4f)).start()
    }

    private fun setText(p: Page) {
        iconView.text = p.icon
        titleView.text = p.title
        subView.text = p.subtitle
        bodyView.text = p.body
    }

    private fun drawDots() {
        dots.removeAllViews()
        for (i in pages.indices) {
            val active = i == index
            dots.addView(View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (active) 0xFFE53935.toInt() else 0x44FFFFFF)
                }
            }, LinearLayout.LayoutParams(dp(if (active) 12 else 7), dp(if (active) 12 else 7)).apply {
                marginStart = dp(5); marginEnd = dp(5)
            })
        }
    }

    private fun playEntrance() {
        card.alpha = 0f
        card.translationY = dp(60).toFloat()
        card.animate().alpha(1f).translationY(0f).setDuration(700)
            .setInterpolator(DecelerateInterpolator()).start()
        orbA.animate().alpha(1f).setDuration(1000).start()
        orbB.animate().alpha(1f).setStartDelay(200).setDuration(1100).start()
        animateIcon()
    }

    /** Orbs float, particles drift, aur ek loop jo sab ko ek saath chalata hai. */
    private fun startFloat() {
        floatAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 4000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener {
                val t = it.animatedValue as Float
                orbA.translationY = dp(18) * t
                orbB.translationX = dp(14) * (1f - t)
                orbA.scaleX = 1f + 0.06f * t
                orbA.scaleY = 1f + 0.06f * t
                particles.tick(t)
            }
            start()
        }
    }

    private fun onNext() {
        if (index < pages.size - 1) {
            showPage(index + 1, animate = true)
        } else {
            requestDefaultHome()
        }
    }

    private fun requestDefaultHome() {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val rm = getSystemService(RoleManager::class.java)
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                    @Suppress("DEPRECATION")
                    startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), 77)
                    return
                }
            }
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        }
        finishOnboarding()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        finishOnboarding()
    }

    private fun finishOnboarding() {
        getSharedPreferences("home", MODE_PRIVATE).edit().putBoolean("first_run_done", true).apply()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
