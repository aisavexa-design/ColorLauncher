package com.colorlauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.LruCache
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val REPO_TAG = "CL"
private const val REPO_CACHE_VER = "v9"

/**
 * App list, icon cache (LRU + disk) aur icon pack lookup.
 * Background thread par kaam karta hai, UI callback runOnUi se milta hai.
 */
class AppRepository(private val ctx: Context, private val iconSizePx: Int) {

    private val bg: ExecutorService = Executors.newSingleThreadExecutor()

    private val lru = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 24).toInt().coerceIn(6 * 1024 * 1024, 20 * 1024 * 1024)
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    val isShutdown: Boolean get() = bg.isShutdown

    fun currentPack(): String =
        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("icon_pack", "") ?: ""

    /** Cache ka tag match kare to purani list turant de do (app khulte hi dikhne ke liye). */
    fun loadCached(): List<AppItem>? {
        return try {
            val pack = currentPack()
            val c = ctx.getSharedPreferences("app_cache", Context.MODE_PRIVATE)
            if (c.getString("tag", "") != pack + "|" + REPO_CACHE_VER) return null
            val lines = (c.getString("list", "") ?: "").split("\n").filter { it.isNotEmpty() }
            val list = lines.mapNotNull { line ->
                try {
                    val parts = line.split("\t")
                    if (parts.size < 2) return@mapNotNull null
                    val cn = ComponentName.unflattenFromString(parts[0]) ?: return@mapNotNull null
                    val bmp = fromDisk(pack, parts[0]) ?: return@mapNotNull null
                    AppItem(parts[1], parts[0], intentFor(cn), bmp)
                } catch (e: Exception) {
                    null
                }
            }
            if (list.isEmpty()) null else list
        } catch (e: Exception) {
            Log.e(REPO_TAG, "loadCached", e)
            null
        }
    }

    /** Background mein full scan. Result callback UI thread par nahi, caller ko runOnUi karna hai. */
    fun scan(onDone: (List<AppItem>) -> Unit) {
        if (bg.isShutdown) return
        val pack = currentPack()
        bg.execute {
            try {
                val pm = ctx.packageManager
                val packMap = if (pack.isNotEmpty()) IconPack.map(ctx, pack) else null
                val packCtx = if (pack.isNotEmpty()) IconPack.context(ctx, pack) else null
                val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val apps = pm.queryIntentActivities(main, 0).mapNotNull { ri ->
                    try {
                        val cn = ComponentName(ri.activityInfo.packageName, ri.activityInfo.name)
                        val key = cn.flattenToString()
                        val icon = fromDisk(pack, key)
                            ?: makeIcon(pack, packMap, packCtx, ri, pm).also { lru.put(pack + "|" + key, it) }
                        AppItem(ri.loadLabel(pm).toString(), key, intentFor(cn), icon)
                    } catch (e: Exception) {
                        Log.e(REPO_TAG, "app item", e)
                        null
                    }
                }.sortedBy { it.label.lowercase() }
                save(apps, pack)
                onDone(apps)
            } catch (e: Exception) {
                Log.e(REPO_TAG, "scan", e)
            }
        }
    }

    /** Ek app ka icon disk se delete (update/uninstall par). */
    fun forget(key: String) {
        try {
            lru.remove(currentPack() + "|" + key)
            cacheFile(currentPack(), key).delete()
        } catch (e: Exception) {
            Log.e(REPO_TAG, "forget", e)
        }
    }

    fun trim() {
        lru.evictAll()
    }

    fun shutdown() {
        bg.shutdownNow()
        lru.evictAll()
    }

    /** Folder icon ya koi aur bitmap cache se nikalne ke liye (MainActivity ke liye). */
    fun intentFor(cn: ComponentName): Intent =
        Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(cn)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    private fun cacheFile(pack: String, key: String): File {
        val dir = File(File(ctx.filesDir, "icons"), if (pack.isEmpty()) "default" else pack).apply { mkdirs() }
        return File(dir, key.hashCode().toString() + ".png")
    }

    private fun fromDisk(pack: String, key: String): Bitmap? {
        val mk = pack + "|" + key
        lru.get(mk)?.let { return it }
        return try {
            val f = cacheFile(pack, key)
            if (!f.exists() || f.length() == 0L) return null
            BitmapFactory.decodeFile(f.path)?.also { lru.put(mk, it) }
        } catch (e: Exception) {
            Log.e(REPO_TAG, "icon decode $key", e)
            null
        }
    }

    private fun makeIcon(
        pack: String,
        packMap: Map<String, String>?,
        packCtx: Context?,
        ri: ResolveInfo,
        pm: PackageManager
    ): Bitmap {
        val key = ComponentName(ri.activityInfo.packageName, ri.activityInfo.name).flattenToString()
        val packDraw = if (pack.isNotEmpty() && packMap != null && packCtx != null) {
            packMap[key]?.let { IconPack.drawable(packCtx, it) }
        } else null
        val src: Drawable = packDraw ?: ri.loadIcon(pm)
        return squareIcon(src)
    }

    private fun squareIcon(dr: Drawable): Bitmap {
        val size = iconSizePx
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        try {
            if (dr is AdaptiveIconDrawable) {
                dr.background?.let { it.setBounds(0, 0, size, size); it.draw(c) }
                dr.foreground?.let { it.setBounds(0, 0, size, size); it.draw(c) }
            } else {
                dr.setBounds(0, 0, size, size)
                dr.draw(c)
            }
        } catch (e: Exception) {
            Log.e(REPO_TAG, "squareIcon", e)
        }
        return bmp
    }

    private fun save(apps: List<AppItem>, pack: String) {
        try {
            for (a in apps) {
                val f = cacheFile(pack, a.key)
                if (f.exists() && f.length() > 0) continue
                FileOutputStream(f).use { out -> a.icon.compress(Bitmap.CompressFormat.PNG, 100, out) }
            }
            ctx.getSharedPreferences("app_cache", Context.MODE_PRIVATE).edit()
                .putString("tag", pack + "|" + REPO_CACHE_VER)
                .putString("list", apps.joinToString("\n") { it.key + "\t" + it.label })
                .apply()
        } catch (e: Exception) {
            Log.e(REPO_TAG, "save", e)
        }
    }
}
