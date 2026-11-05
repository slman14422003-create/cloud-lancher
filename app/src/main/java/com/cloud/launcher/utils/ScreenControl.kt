package com.cloud.launcher.utils

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Build
import com.cloud.launcher.root.RootManager
import com.cloud.launcher.service.AppMonitorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object ScreenControl {

    fun sleep(context: Context) {
        val service = AppMonitorService.instance
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && service != null) {
            if (service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)) return
        }
        CoroutineScope(Dispatchers.IO).launch {
            val root = RootManager.isGranted || RootManager.isRootAvailable()
            if (root) {
                ShellUtils.runAsRoot("input keyevent 223 || input keyevent 26", 5_000L)
            }
        }
    }
}
