package com.cloud.launcher.service

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.cloud.launcher.utils.RecentsIntent
import com.cloud.launcher.data.RecentAppsRepository
import com.cloud.launcher.utils.PrefsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AppMonitorService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var repository: RecentAppsRepository? = null
    private var lastPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        repository = RecentAppsRepository(PrefsManager(applicationContext))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == lastPackage) return
        if (pkg == applicationContext.packageName || pkg == "com.android.systemui") return
        val launchable = try {
            packageManager.getLaunchIntentForPackage(pkg) != null
        } catch (e: Exception) {
            false
        }
        if (!launchable) return
        lastPackage = pkg
        val repo = repository ?: return
        scope.launch { repo.record(pkg) }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code != KeyEvent.KEYCODE_APP_SWITCH && code != KeyEvent.KEYCODE_MENU) {
            return super.onKeyEvent(event)
        }
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
            RecentsIntent.open(applicationContext)
        }
        return true
    }

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
