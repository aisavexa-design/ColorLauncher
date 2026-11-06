package com.colorlauncher

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.util.Xml
import androidx.core.content.ContextCompat
import org.xmlpull.v1.XmlPullParser

object IconPack {

    private val actions = listOf(
        "org.adw.launcher.THEMES",
        "com.novalauncher.THEME",
        "com.anddoes.launcher.THEME"
    )

    /** Installed icon packs: packageName to label. */
    fun installed(ctx: Context): List<Pair<String, String>> {
        val pm = ctx.packageManager
        val out = LinkedHashMap<String, String>()
        for (a in actions) {
            try {
                for (ri in pm.queryIntentActivities(Intent(a), 0)) {
                    out[ri.activityInfo.packageName] = ri.loadLabel(pm).toString()
                }
            } catch (e: Exception) {
            }
        }
        return out.toList()
    }

    fun context(ctx: Context, pkg: String): Context? = try {
        ctx.createPackageContext(pkg, Context.CONTEXT_IGNORE_SECURITY)
    } catch (e: Exception) {
        null
    }

    /** appfilter.xml se component (pkg/cls) ko drawable name tak map karta hai. */
    fun map(ctx: Context, pkg: String): Map<String, String>? {
        val pctx = context(ctx, pkg) ?: return null
        return try {
            val m = HashMap<String, String>()
            pctx.assets.open("appfilter.xml").use { ins ->
                val p = Xml.newPullParser()
                p.setInput(ins, "utf-8")
                var ev = p.eventType
                while (ev != XmlPullParser.END_DOCUMENT) {
                    if (ev == XmlPullParser.START_TAG && p.name == "item") {
                        val comp = p.getAttributeValue(null, "component")
                        val draw = p.getAttributeValue(null, "drawable")
                        if (comp != null && draw != null) {
                            val key = comp.removePrefix("ComponentInfo{").removeSuffix("}")
                            m[key] = draw
                        }
                    }
                    ev = p.next()
                }
            }
            m
        } catch (e: Exception) {
            null
        }
    }

    fun drawable(pctx: Context, name: String): Drawable? = try {
        val id = pctx.resources.getIdentifier(name, "drawable", pctx.packageName)
        if (id == 0) null else ContextCompat.getDrawable(pctx, id)
    } catch (e: Exception) {
        null
    }
}
