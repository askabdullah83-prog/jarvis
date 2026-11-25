package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import java.lang.ref.WeakReference

class JarvisAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instanceRef = WeakReference(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Passive observation; actions are triggered on-demand by user commands
    }

    override fun onInterrupt() {
        // No-op
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instanceRef?.get() === this) {
            instanceRef = null
        }
    }

    companion object {
        @Volatile
        private var instanceRef: WeakReference<JarvisAccessibilityService>? = null

        fun isConnected(): Boolean = instanceRef?.get() != null

        fun isAccessibilitySettingEnabled(context: Context): Boolean {
            if (isConnected()) return true
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val serviceId = "${context.packageName}/${JarvisAccessibilityService::class.java.name}"
            val shortServiceId = "${context.packageName}/.service.JarvisAccessibilityService"
            return enabledServices.contains(serviceId, ignoreCase = true) ||
                enabledServices.contains(shortServiceId, ignoreCase = true)
        }

        fun goHome(): Boolean = instanceRef?.get()?.performGlobalAction(GLOBAL_ACTION_HOME) == true

        fun goBack(): Boolean = instanceRef?.get()?.performGlobalAction(GLOBAL_ACTION_BACK) == true

        fun openRecents(): Boolean = instanceRef?.get()?.performGlobalAction(GLOBAL_ACTION_RECENTS) == true

        fun openNotifications(): Boolean =
            instanceRef?.get()?.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) == true

        fun openQuickSettings(): Boolean =
            instanceRef?.get()?.performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS) == true

        fun lockScreen(): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                instanceRef?.get()?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) == true
            } else {
                false
            }
        }

        fun takeScreenshot(): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                instanceRef?.get()?.performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT) == true
            } else {
                false
            }
        }
    }
}
