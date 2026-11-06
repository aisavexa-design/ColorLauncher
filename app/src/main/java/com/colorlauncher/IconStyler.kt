package com.colorlauncher

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

object IconStyler {
    val shapes = listOf(
        "original" to "Original",
        "circle" to "Circle",
        "squircle" to "Squircle",
        "rounded" to "Rounded",
        "teardrop" to "Teardrop"
    )

    private fun shapePath(shape: String, s: Float): Path? {
        val p = Path()
        when (shape) {
            "circle" -> p.addCircle(s / 2f, s / 2f, s / 2f, Path.Direction.CW)
            "rounded" -> p.addRoundRect(RectF(0f, 0f, s, s), s * 0.24f, s * 0.24f, Path.Direction.CW)
            "teardrop" -> {
                val r = s / 2f
                val small = s * 0.1f
                p.addRoundRect(
                    RectF(0f, 0f, s, s),
                    floatArrayOf(r, r, r, r, small, small, r, r),
                    Path.Direction.CW
                )
            }
            "squircle" -> {
                val h = s / 2.0
                val steps = 120
                for (i in 0..steps) {
                    val a = 2.0 * Math.PI * i / steps
                    val c = cos(a)
                    val sn = sin(a)
                    val x = (h + h * sign(c) * sqrt(abs(c))).toFloat()
                    val y = (h + h * sign(sn) * sqrt(abs(sn))).toFloat()
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                p.close()
            }
            else -> return null
        }
        return p
    }

    /** fullBleed = icon pack ka icon (poora square), baaki normal app icons */
    fun render(dr: Drawable, size: Int, shape: String, fullBleed: Boolean): Bitmap {
        val path = shapePath(shape, size.toFloat())
        val content = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(content)
        if (path == null || fullBleed) {
            dr.setBounds(0, 0, size, size)
            dr.draw(c)
        } else if (Build.VERSION.SDK_INT >= 26 && dr is AdaptiveIconDrawable) {
            val o = (size * 0.25f).toInt()
            dr.background?.let {
                it.setBounds(-o, -o, size + o, size + o)
                it.draw(c)
            }
            dr.foreground?.let {
                it.setBounds(-o, -o, size + o, size + o)
                it.draw(c)
            }
        } else {
            c.drawColor(0xFFF2F2F2.toInt())
            val inset = (size * 0.16f).toInt()
            dr.setBounds(inset, inset, size - inset, size - inset)
            dr.draw(c)
        }
        if (path == null) return content

        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val oc = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        oc.drawPath(path, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        oc.drawBitmap(content, 0f, 0f, paint)
        return out
    }
}

class IconPackInfo(val pkg: String, val label: String)

class LoadedPack(
    private val res: Resources,
    private val pkg: String,
    private val map: Map<String, String>
) {
    fun drawableFor(key: String): Drawable? {
        val name = map[key] ?: return null
        return try {
            val id = res.getIdentifier(name, "drawable", pkg)
            if (id == 0) null else res.getDrawable(id, null)
        } catch (e: Exception) {
            null
        }
    }
}

object IconPacks {
    private val actions = listOf(
        "org.adw.launcher.THEMES",
        "com.novalauncher.THEME",
        "com.gau.go.launcherex.theme",
        "com.anddoes.launcher.THEME"
    )

    fun find(pm: PackageManager): List<IconPackInfo> {
        val seen = LinkedHashMap<String, IconPackInfo>()
        for (a in actions) {
            try {
                for (ri in pm.queryIntentActivities(Intent(a), 0)) {
                    val p = ri.activityInfo.packageName
                    if (p !in seen) seen[p] = IconPackInfo(p, ri.loadLabel(pm).toString())
                }
            } catch (e: Exception) {
            }
        }
        return seen.values.toList()
    }

    fun load(pm: PackageManager, pkg: String): LoadedPack? {
        return try {
            val res = pm.getResourcesForApplication(pkg)
            val parser: XmlPullParser
            val id = res.getIdentifier("appfilter", "xml", pkg)
            if (id != 0) {
                parser = res.getXml(id)
            } else {
                parser = Xml.newPullParser()
                parser.setInput(res.assets.open("appfilter.xml"), null)
            }
            val map = HashMap<String, String>()
            var ev = parser.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && parser.name == "item") {
                    val comp = parser.getAttributeValue(null, "component")
                    val dr = parser.getAttributeValue(null, "drawable")
                    if (comp != null && dr != null && comp.startsWith("ComponentInfo{")) {
                        val key = comp.removePrefix("ComponentInfo{").removeSuffix("}")
                        val slash = key.indexOf('/')
                        if (slash > 0) {
                            val pk = key.substring(0, slash)
                            var cls = key.substring(slash + 1)
                            if (cls.startsWith(".")) cls = pk + cls
                            map["$pk/$cls"] = dr.removePrefix("@drawable/")
                        }
                    }
                }
                ev = parser.next()
            }
            if (map.isEmpty()) null else LoadedPack(res, pkg, map)
        } catch (e: Exception) {
            null
        }
    }
}
