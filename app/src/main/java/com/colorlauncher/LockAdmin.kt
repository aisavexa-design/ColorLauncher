package com.colorlauncher

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.app.admin.DevicePolicyManager
import android.content.ComponentName

class LockAdmin : DeviceAdminReceiver() {
    companion object {
        fun component(ctx: Context) = ComponentName(ctx, LockAdmin::class.java)

        fun isActive(ctx: Context): Boolean {
            val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            return dpm.isAdminActive(component(ctx))
        }

        fun lockNow(ctx: Context): Boolean {
            val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            return if (dpm.isAdminActive(component(ctx))) {
                dpm.lockNow()
                true
            } else false
        }
    }
}
