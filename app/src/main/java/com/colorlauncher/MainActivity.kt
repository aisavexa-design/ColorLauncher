package com.colorlauncher

import android.app.Activity
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Intent
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

data class AppItem(val label: String, val intent: Intent, val icon: Drawable)

class MainActivity : Activity() {

    private val bg = Executors.newSingleThreadExecutor()
    private lateinit var recycler: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        recycler = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, 4)
            setHasFixedSize(true)
            setItemViewCacheSize(20)
            clipToPadding = false
            setPadding((8 * d).toInt(), (48 * d).toInt(), (8 * d).toInt(), (32 * d).toInt())
        }
        setContentView(recycler)
        loadApps()
    }

    private fun loadApps() {
        bg.execute {
            val pm = packageManager
            val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(main, 0).map { ri ->
                val intent = Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(ComponentName(ri.activityInfo.packageName, ri.activityInfo.name))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                AppItem(ri.loadLabel(pm).toString(), intent, ri.loadIcon(pm))
            }.sortedBy { it.label.lowercase() }
            runOnUiThread {
                recycler.adapter = AppAdapter(apps) { view, item -> launch(view, item) }
            }
        }
    }

    private fun launch(view: View, item: AppItem) {
        try {
            val opts = ActivityOptions.makeScaleUpAnimation(view, 0, 0, view.width, view.height)
            startActivity(item.intent, opts.toBundle())
        } catch (e: Exception) {
            // app missing/disabled: ignore instead of crashing
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        recycler.smoothScrollToPosition(0)
    }
}

class AppAdapter(
    private val items: List<AppItem>,
    private val onClick: (View, AppItem) -> Unit
) : RecyclerView.Adapter<AppAdapter.VH>() {

    class VH(val root: LinearLayout, val icon: ImageView, val label: TextView) :
        RecyclerView.ViewHolder(root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        val d = ctx.resources.displayMetrics.density
        val icon = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams((56 * d).toInt(), (56 * d).toInt())
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) {
                    o.setRoundRect(0, 0, v.width, v.height, v.width * 0.28f)
                }
            }
            clipToOutline = true
        }
        val label = TextView(ctx).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 11f
            maxLines = 1
            gravity = Gravity.CENTER
            setShadowLayer(4f, 0f, 1f, 0x99000000.toInt())
            setPadding(0, (6 * d).toInt(), 0, 0)
        }
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, (12 * d).toInt(), 0, (12 * d).toInt())
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addView(icon)
            addView(label)
        }
        return VH(root, icon, label)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = items[position]
        h.icon.setImageDrawable(item.icon)
        h.label.text = item.label
        h.root.setOnClickListener { onClick(h.icon, item) }
        h.root.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> spring(v, 0.9f)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> spring(v, 1f)
            }
            false
        }
    }

    override fun getItemCount() = items.size

    private fun spring(v: View, target: Float) {
        listOf(DynamicAnimation.SCALE_X, DynamicAnimation.SCALE_Y).forEach { prop ->
            SpringAnimation(v, prop).apply {
                spring = SpringForce(target)
                    .setStiffness(SpringForce.STIFFNESS_MEDIUM)
                    .setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY)
                start()
            }
        }
    }
}
