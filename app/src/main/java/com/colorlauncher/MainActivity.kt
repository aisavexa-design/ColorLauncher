package com.colorlauncher

import android.app.Activity
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

class AppItem(val label: String, val intent: Intent, val icon: Bitmap)

class MainActivity : Activity() {

    private val bg = Executors.newSingleThreadExecutor()
    private lateinit var recycler: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        recycler = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, 4)
            setHasFixedSize(true)
            setItemViewCacheSize(24)
            itemAnimator = null
            clipToPadding = false
            setPadding((8 * d).toInt(), (48 * d).toInt(), (8 * d).toInt(), (32 * d).toInt())
        }
        setContentView(recycler)
        loadApps()
    }

    private fun loadApps() {
        bg.execute {
            val pm = packageManager
            val sizePx = (56 * resources.displayMetrics.density).toInt()
            val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(main, 0).map { ri ->
                val intent = Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(ComponentName(ri.activityInfo.packageName, ri.activityInfo.name))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                AppItem(ri.loadLabel(pm).toString(), intent, toBitmap(ri.loadIcon(pm), sizePx))
            }.sortedBy { it.label.lowercase() }
            runOnUiThread {
                recycler.adapter = AppAdapter(apps) { view, item -> launch(view, item) }
            }
        }
    }

    private fun toBitmap(dr: Drawable, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        dr.setBounds(0, 0, size, size)
        dr.draw(Canvas(bmp))
        return bmp
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

    init { setHasStableIds(true) }

    class VH(val root: LinearLayout, val icon: ImageView, val label: TextView) :
        RecyclerView.ViewHolder(root)

    override fun getItemId(position: Int) = position.toLong()

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
            gravity = Gravity.CENTER
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
        h.icon.setImageBitmap(item.icon)
        h.label.text = item.label
        h.root.setOnClickListener { onClick(h.icon, item) }
        h.root.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN ->
                    v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).setInterpolator(null).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(220)
                        .setInterpolator(OvershootInterpolator(2f)).start()
            }
            false
        }
    }

    override fun getItemCount() = items.size
}
