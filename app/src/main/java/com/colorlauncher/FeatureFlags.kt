package com.colorlauncher

import android.content.Context

/** Naye features ki settings. Sab "settings" SharedPreferences mein save hoti hain. */
object FeatureFlags {
    private const val PREFS = "settings"

    const val KEY_DRAWER_CATEGORY = "drawer_category"   // true = category drawer, false = simple drawer
    const val KEY_GLASS_MASTER = "glass_master"         // liquid glass master switch
    const val KEY_GLASS_DOCK = "glass_dock"
    const val KEY_GLASS_FOLDER = "glass_folder"
    const val KEY_GLASS_DRAWER = "glass_drawer"
    const val KEY_GLASS_CATEGORY = "glass_category"

    fun isCategoryDrawer(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DRAWER_CATEGORY, false)

    fun glassOn(ctx: Context, surfaceKey: String): Boolean {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return p.getBoolean(KEY_GLASS_MASTER, false) && p.getBoolean(surfaceKey, true)
    }
}
