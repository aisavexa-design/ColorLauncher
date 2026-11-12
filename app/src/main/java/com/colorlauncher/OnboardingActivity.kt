package com.colorlauncher

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.app.role.RoleManager

/**
 * Pehli baar launch par animated intro. Pages: welcome, features, glass, finish.
 * Last page par "Choose default launcher" aur "Created by Pratik".
 */
class OnboardingActivity : Activity() {

    private val pages = listOf(
        Triple("Welcome", "Aapka naya home screen", "Smooth, fast aur premium look."),
        Triple("Smart drawer", "Simple A-Z ya category view", "Apni pasand se drawer chuno."),
        Triple("Liquid glass", "Dock, folder aur drawer par glass", "Settings se on/off karo."),
        Triple("Ready", "Chalo shuru karte hain", "Default launcher chuno aur start karo.")
    )

    private var index = 0
    private lateinit var root: FrameLayout
    private lateinit var cardTitle: TextView
    private lateinit var cardSub: TextView
    private lateinit var cardBody: TextView
    private lateinit var card: LinearLayout
    private lateinit var dots: LinearLayout
    private lateinit var nextBtn: TextView
    private lateinit var skipBtn: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        buildUi()
        showPage(0, animate = false)
        animateIn()
    }

    private fun buildUi() {
        root = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF0B0B12.toInt(), 0xFF1A1030.toInt(), 0xFF0B1A2A.toInt())
            )
        }

        val glowTop = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x557B5CFF)
            }
            alpha = 0f
        }
        root.addView(glowTop, FrameLayout.LayoutParams(dp(260), dp(260), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = dp(80)
        })
        glowTop.animate().alpha(1f).setDuration(900).start()

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(60), dp(24), dp(32))
        }

        column.addView(TextView(this).apply {
            text = "ColorLauncher"
            textSize = 16f
            setTextColor(0xB3FFFFFF.toInt())
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(32), dp(24), dp(32))
            background = GlassBackground.make(this@OnboardingActivity, 28f)
        }
        cardTitle = TextView(this).apply {
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        cardSub = TextView(this).apply {
            textSize = 18f
            setTextColor(0xFFDDDDE8.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        }
        cardBody = TextView(this).apply {
            textSize = 15f
            setTextColor(0xB3FFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        }
        card.addView(cardTitle)
        card.addView(cardSub)
        card.addView(cardBody)

        column.addView(View(this), LinearLayout.LayoutParams(1, dp(40)))
        column.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        dots = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        column.addView(dots, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(24)
        })

        column.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))

        nextBtn = TextView(this).apply {
            textSize = 17f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(16))
            background = GlassBackground.make(this@OnboardingActivity, 22f)
            setOnClickListener { onNext() }
        }
        column.addView(nextBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        skipBtn = TextView(this).apply {
            text = "Skip"
            textSize = 14f
            setTextColor(0xB3FFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, 0)
            setOnClickListener { finishOnboarding() }
        }
        column.addView(skipBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        column.addView(TextView(this).apply {
            text = "Created by Pratik"
            textSize = 13f
            setTextColor(0x80FFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, 0)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)
    }

    private fun showPage(i: Int, animate: Boolean) {
        index = i
        val (t, s, b) = pages[i]
        val isLast = i == pages.size - 1
        nextBtn.text = if (isLast) "Choose default launcher" else "Next"
        skipBtn.visibility = if (isLast) View.GONE else View.VISIBLE

        if (animate) {
            card.animate().alpha(0f).translationX(-dp(30).toFloat()).setDuration(160).withEndAction {
                setText(t, s, b)
                card.translationX = dp(30).toFloat()
                card.animate().alpha(1f).translationX(0f).setDuration(320)
                    .setInterpolator(OvershootInterpolator(0.8f)).start()
            }.start()
        } else {
            setText(t, s, b)
        }
        drawDots()
    }

    private fun setText(t: String, s: String, b: String) {
        cardTitle.text = t
        cardSub.text = s
        cardBody.text = b
    }

    private fun drawDots() {
        dots.removeAllViews()
        for (i in pages.indices) {
            dots.addView(View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (i == index) Color.WHITE else 0x55FFFFFF)
                }
            }, LinearLayout.LayoutParams(dp(if (i == index) 10 else 7), dp(if (i == index) 10 else 7)).apply {
                marginStart = dp(4); marginEnd = dp(4)
            })
        }
    }

    private fun animateIn() {
        card.alpha = 0f
        card.translationY = dp(40).toFloat()
        card.animate().alpha(1f).translationY(0f).setDuration(600)
            .setInterpolator(DecelerateInterpolator()).start()
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

    @Deprecated("Deprecated in Java")
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
