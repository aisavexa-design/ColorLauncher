package com.colorlauncher

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.util.Log
import android.view.ViewGroup
import android.widget.LinearLayout
import kotlin.math.max

private const val WM_TAG = "CL"

/**
 * Home screen ke widgets ka manager.
 * Bind, configure (Activity result ke through), restore aur remove.
 * Widget ids prefs ("home" -> widget_ids) mein save hoti hain.
 */
class WidgetManager(
    private val activity: Activity,
    private val container: LinearLayout,
    private val prefs: android.content.SharedPreferences,
    private val hostId: Int,
    private val reqBind: Int,
    private val reqConfig: Int,
    private val dp: (Int) -> Int,
    private val onLongRemove: (remove: () -> Unit) -> Unit
) {
    val host: AppWidgetHost by lazy { AppWidgetHost(activity, hostId) }
    private val mgr: AppWidgetManager by lazy { AppWidgetManager.getInstance(activity) }

    /** Pending widget id (bind ya configure ke beech). */
    var pendingId: Int = -1
        private set

    fun startListening() {
        try { host.startListening() } catch (e: Exception) { Log.e(WM_TAG, "widget start", e) }
    }

    fun stopListening() {
        try { host.stopListening() } catch (e: Exception) { Log.e(WM_TAG, "widget stop", e) }
    }

    fun installedProviders(): List<AppWidgetProviderInfo> =
        mgr.installedProviders.sortedBy { it.loadLabel(activity.packageManager).toString().lowercase() }

    /** Provider select hone par: pehle direct bind try, na ho to permission screen. */
    fun bind(info: AppWidgetProviderInfo) {
        try {
            val id = host.allocateAppWidgetId()
            if (mgr.bindAppWidgetIdIfAllowed(id, info.provider)) {
                afterBind(id)
            } else {
                pendingId = id
                @Suppress("DEPRECATION")
                activity.startActivityForResult(
                    Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                    },
                    reqBind
                )
            }
        } catch (e: Exception) {
            Log.e(WM_TAG, "bind", e)
        }
    }

    /** MainActivity.onActivityResult se call karo. Return true agar request handle hui. */
    fun onActivityResult(requestCode: Int, resultCode: Int): Boolean {
        return try {
            when (requestCode) {
                reqBind -> {
                    if (resultCode == Activity.RESULT_OK) afterBind(pendingId)
                    else host.deleteAppWidgetId(pendingId)
                    true
                }
                reqConfig -> {
                    if (resultCode == Activity.RESULT_OK) {
                        val info = mgr.getAppWidgetInfo(pendingId)
                        if (info != null) finishAdd(pendingId, info) else host.deleteAppWidgetId(pendingId)
                    } else {
                        host.deleteAppWidgetId(pendingId)
                    }
                    true
                }
                else -> false
            }
        } catch (e: Exception) {
            Log.e(WM_TAG, "activityResult", e)
            false
        }
    }

    /** Saved widget ids se sab widgets wapas. Invalid ids saaf. */
    fun restore() {
        try {
            val ids = (prefs.getString("widget_ids", "") ?: "").split(",").mapNotNull { it.toIntOrNull() }
            for (id in ids) {
                try {
                    val info = mgr.getAppWidgetInfo(id)
                    if (info != null) addView(id, info) else host.deleteAppWidgetId(id)
                } catch (e: Exception) {
                    Log.e(WM_TAG, "restore $id", e)
                    try { host.deleteAppWidgetId(id) } catch (_: Exception) {}
                }
            }
            saveIds()
        } catch (e: Exception) {
            Log.e(WM_TAG, "restore", e)
        }
    }

    private fun afterBind(id: Int) {
        try {
            val info = mgr.getAppWidgetInfo(id)
            if (info == null) {
                host.deleteAppWidgetId(id)
                return
            }
            if (info.configure != null) {
                pendingId = id
                @Suppress("DEPRECATION")
                activity.startActivityForResult(
                    Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                        component = info.configure
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    },
                    reqConfig
                )
            } else {
                finishAdd(id, info)
            }
        } catch (e: Exception) {
            Log.e(WM_TAG, "afterBind", e)
            try { host.deleteAppWidgetId(id) } catch (_: Exception) {}
        }
    }

    private fun finishAdd(id: Int, info: AppWidgetProviderInfo) {
        addView(id, info)
        saveIds()
    }

    private fun addView(id: Int, info: AppWidgetProviderInfo) {
        try {
            val hv: AppWidgetHostView = host.createView(activity, id, info)
            hv.tag = id
            hv.setOnLongClickListener {
                onLongRemove {
                    try { host.deleteAppWidgetId(id) } catch (_: Exception) {}
                    container.removeView(hv)
                    saveIds()
                }
                true
            }
            container.addView(hv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(max(info.minHeight, 60))).apply {
                setMargins(dp(12), dp(8), dp(12), 0)
            })
        } catch (e: Exception) {
            Log.e(WM_TAG, "addView", e)
            try { host.deleteAppWidgetId(id) } catch (_: Exception) {}
        }
    }

    private fun saveIds() {
        try {
            val ids = (0 until container.childCount).mapNotNull { container.getChildAt(it).tag as? Int }
            prefs.edit().putString("widget_ids", ids.joinToString(",")).apply()
        } catch (e: Exception) {
            Log.e(WM_TAG, "saveIds", e)
        }
    }
}
