package com.colorlauncher

import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

private const val AA_TAG = "CL"
private const val AA_MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val AA_WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

class AppAdapter(
    private val showLabel: Boolean,
    private val onClick: (View, AppItem) -> Unit,
    private val onLong: ((AppItem) -> Unit)?
) : RecyclerView.Adapter<AppAdapter.VH>() {

    private var items: List<AppItem?> = emptyList()
    private var style = IconStyle(60, 0, true, 8, 12)

    init {
        setHasStableIds(true)
    }

    class VH(val root: LinearLayout, val icon: ShapeIcon, val label: TextView) : RecyclerView.ViewHolder(root)

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

    override fun getItemId(position: Int): Long =
        items.getOrNull(position)?.key?.hashCode()?.toLong() ?: (1_000_000_000L + position)

    override fun getItemCount(): Int = items.size

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
            layoutParams = RecyclerView.LayoutParams(AA_MATCH, AA_WRAP)
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
                h.root.setOnTouchListener(null)
                return
            }

            h.icon.visibility = View.VISIBLE
            val current = (h.icon.drawable as? BitmapDrawable)?.bitmap
            if (current !== item.icon) h.icon.setImageBitmap(item.icon)

            val showText = showLabel && style.labels
            h.label.visibility = if (showText) View.VISIBLE else View.GONE
            h.label.text = item.label

            val vPad = if (showText) 12 else 8
            h.root.setPadding(0, (vPad * d).toInt(), 0, (vPad * d).toInt())

            h.root.setOnClickListener { onClick(h.icon, item) }
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
            Log.e(AA_TAG, "bind $position", e)
        }
    }
}
