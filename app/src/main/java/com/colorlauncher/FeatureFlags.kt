package com.colorlauncher

import android.content.Context

/** Naye features ki settings. Sab "settings" SharedPreferences mein save hoti hain. */
object FeatureFlags {
    private const val PREFS = "settings"

    const val KEY_DRAWER_CATEGORY = "drawer_category"   // 0 = Simple (default), 1 = Category
    const val KEY_GLASS_MASTER = "glass_master"         // liquid glass master switch (default ON)
    const val KEY_GLASS_DOCK = "glass_dock"             // default ON
    const val KEY_GLASS_FOLDER = "glass_folder"         // default OFF
    const val KEY_GLASS_DRAWER = "glass_drawer"         // default OFF
    const val KEY_GLASS_CATEGORY = "glass_category"     // default OFF

    /** Drawer style: int value (0 = Simple, 1 = Category). Default Simple. */
    fun isCategoryDrawer(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_DRAWER_CATEGORY, 0) == 1

    /**
     * Glass on hai ya nahi. Master ON ho aur us surface ka switch ON ho.
     * Defaults: master ON, dock ON, folder/drawer/category OFF.
     */
    fun glassOn(ctx: Context, surfaceKey: String): Boolean {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val surfaceDefault = surfaceKey == KEY_GLASS_DOCK
        return p.getBoolean(KEY_GLASS_MASTER, true) && p.getBoolean(surfaceKey, surfaceDefault)
    }
}
