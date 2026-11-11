package com.colorlauncher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

private const val CAT_TAG = "CL-Cat"
private const val CAT_MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val CAT_WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/** Ek category ka data: naam, label, aur us mein aane wale apps ki keys. */
data class CategoryGroup(
    val id: String,
    val title: String,
    val apps: List<AppItem>
)

/**
 * iOS jaisa category drawer. Do screens:
 * 1. Category grid (har category ek glass card).
 * 2. Category tap par us category ke apps ki list (AppAdapter se).
 * Glass ka background abhi solid translucent hai; GlassBackground.kt aane par wahan swap hoga.
 */
class CategoryDrawer(
    private val ctx: Context,
    private val onLaunch: (View, AppItem) -> Unit,
    private val onLongApp: (AppItem, Float, Float) -> Unit
) {
    /** Root view jo MainActivity drawer container mein add karega. */
    val view: FrameLayout = FrameLayout(ctx)

    private val cardsGrid: RecyclerView
    private val appsGrid: RecyclerView
    private val backRow: LinearLayout
    private val titleText: TextView

    private val cardAdapter = CategoryCardAdapter { group -> showCategory(group) }
    private val appAdapter = AppAdapter(true, { v, i -> onLaunch(v, i) }, null)

    private var groups: List<CategoryGroup> = emptyList()
    private var openId: String? = null

    init {
        val d = ctx.resources.displayMetrics.density

        titleText = TextView(ctx).apply {
            text = "Categories"
            textSize = 24f
            setTextColor(Color.WHITE)
            setPadding((20 * d).toInt(), 0, 0, 0)
        }

        backRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding((12 * d).toInt(), (8 * d).toInt(), (12 * d).toInt(), (8 * d).toInt())
            addView(TextView(ctx).apply {
                text = "‹ Back"
                textSize = 18f
                setTextColor(Color.WHITE)
                setPadding((8 * d).toInt(), (8 * d).toInt(), (16 * d).toInt(), (8 * d).toInt())
                setOnClickListener { showCategories() }
            })
        }

        cardsGrid = RecyclerView(ctx).apply {
            layoutManager = GridLayoutManager(ctx, 2)
            adapter = cardAdapter
            overScrollMode = View.OVER_SCROLL_NEVER
            itemAnimator = null
            clipToPadding = false
            setPadding((12 * d).toInt(), (80 * d).toInt(), (12 * d).toInt(), (24 * d).toInt())
        }

        appsGrid = RecyclerView(ctx).apply {
            layoutManager = GridLayoutManager(ctx, 4)
            adapter = appAdapter
            overScrollMode = View.OVER_SCROLL_NEVER
            itemAnimator = null
            clipToPadding = false
            visibility = View.GONE
            setPadding((8 * d).toInt(), (80 * d).toInt(), (8 * d).toInt(), (24 * d).toInt())
        }

        val column = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(titleText, LinearLayout.LayoutParams(CAT_MATCH, CAT_WRAP))
            addView(backRow, LinearLayout.LayoutParams(CAT_MATCH, CAT_WRAP))
        }

        view.addView(cardsGrid, FrameLayout.LayoutParams(CAT_MATCH, CAT_MATCH))
        view.addView(appsGrid, FrameLayout.LayoutParams(CAT_MATCH, CAT_MATCH))
        view.addView(column, FrameLayout.LayoutParams(CAT_MATCH, CAT_WRAP, Gravity.TOP).apply {
            topMargin = (16 * d).toInt()
        })
    }

    /** Apps ki list aate hi groups banao. MainActivity allApps load hone par ye call karega. */
    fun submitApps(apps: List<AppItem>) {
        try {
            groups = AppCategoryMapper.group(apps)
            cardAdapter.submit(groups)
            if (openId != null) {
                val g = groups.firstOrNull { it.id == openId }
                if (g != null) appAdapter.submitDiff(g.apps) else showCategories()
            }
        } catch (e: Exception) {
            Log.e(CAT_TAG, "submitApps", e)
        }
    }

    /** Drawer band hone ya reset par category grid par wapas. */
    fun showCategories() {
        openId = null
        appsGrid.visibility = View.GONE
        cardsGrid.visibility = View.VISIBLE
        backRow.visibility = View.GONE
        titleText.text = "Categories"
    }

    private fun showCategory(group: CategoryGroup) {
        openId = group.id
        appAdapter.submitDiff(group.apps)
        cardsGrid.visibility = View.GONE
        appsGrid.visibility = View.VISIBLE
        backRow.visibility = View.VISIBLE
        titleText.text = group.title
        appsGrid.scrollToPosition(0)
    }

    fun applyStyle(s: IconStyle) {
        appAdapter.applyStyle(s)
        cardAdapter.notifyDataSetChanged()
    }

    /** Category card adapter: ek card mein category naam aur app count. */
    private class CategoryCardAdapter(
        private val onClick: (CategoryGroup) -> Unit
    ) : RecyclerView.Adapter<CategoryCardAdapter.VH>() {

        private var items: List<CategoryGroup> = emptyList()

        class VH(val card: LinearLayout, val title: TextView, val count: TextView) :
            RecyclerView.ViewHolder(card)

        fun submit(list: List<CategoryGroup>) {
            items = list
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val c = parent.context
            val d = c.resources.displayMetrics.density
            val title = TextView(c).apply {
                textSize = 17f
                setTextColor(Color.WHITE)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            val count = TextView(c).apply {
                textSize = 13f
                setTextColor(0xB3FFFFFF.toInt())
                setPadding(0, (4 * d).toInt(), 0, 0)
            }
            val card = LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((16 * d).toInt(), (18 * d).toInt(), (16 * d).toInt(), (18 * d).toInt())
                background = GradientDrawable().apply {
                    cornerRadius = 22 * d
                    setColor(0x33FFFFFF)
                }
                layoutParams = RecyclerView.LayoutParams(CAT_MATCH, CAT_WRAP).apply {
                    setMargins((6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt())
                }
                addView(title)
                addView(count)
            }
            return VH(card, title, count)
        }

        override fun onBindViewHolder(h: VH, position: Int) {
            val g = items[position]
            h.title.text = g.title
            h.count.text = "${g.apps.size} apps"
            h.card.setOnClickListener { onClick(g) }
        }
    }
}

/** Apps ko categories mein baantne ka logic. Package name ke keywords se guess karta hai. */
object AppCategoryMapper {

    private val rules: List<Pair<String, List<String>>> = listOf(
        "Social" to listOf("whatsapp", "telegram", "instagram", "facebook", "snapchat", "twitter", "threads", "reddit", "messenger"),
        "Communication" to listOf("dialer", "contacts", "mms", "messaging", "gm", "mail", "outlook", "phone", "call"),
        "Media" to listOf("youtube", "spotify", "music", "player", "video", "gallery", "photos", "netflix", "prime", "hotstar"),
        "Games" to listOf("game", "play", "candy", "pubg", "bgmi", "minecraft"),
        "Shopping" to listOf("amazon", "flipkart", "meesho", "myntra", "shop", "store"),
        "Finance" to listOf("pay", "bank", "wallet", "upi", "bhim", "paytm", "wise"),
        "Tools" to listOf("calculator", "clock", "files", "file", "settings", "notes", "note", "camera", "weather", "scanner", "drive"),
        "Health" to listOf("fit", "health", "step", "yoga")
    )

    private const val OTHER_ID = "other"
    private const val OTHER_TITLE = "Other"

    /** Har app ko pehli matching category mein daalta hai, na mile to Other. */
    fun group(apps: List<AppItem>): List<CategoryGroup> {
        val buckets = LinkedHashMap<String, MutableList<AppItem>>()
        for ((title, _) in rules) buckets[title] = mutableListOf()
        buckets[OTHER_TITLE] = mutableListOf()

        for (app in apps) {
            val pkg = app.intent.component?.packageName?.lowercase() ?: ""
            val label = app.label.lowercase()
            val hit = rules.firstOrNull { (_, keys) ->
                keys.any { k -> pkg.contains(k) || label.contains(k) }
            }?.first ?: OTHER_TITLE
            buckets.getValue(hit).add(app)
        }

        return buckets
            .filter { it.value.isNotEmpty() }
            .map { (title, list) ->
                CategoryGroup(
                    id = if (title == OTHER_TITLE) OTHER_ID else title.lowercase(),
                    title = title,
                    apps = list
                )
            }
    }
}
