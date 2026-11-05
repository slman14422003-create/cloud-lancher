package com.cloud.launcher.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.PowerManager
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.cloud.launcher.data.LauncherSettings
import com.cloud.launcher.data.RecentAppsRepository
import com.cloud.launcher.root.DoubleTapWake
import com.cloud.launcher.root.MemoryOptimizer
import com.cloud.launcher.root.RootManager
import com.cloud.launcher.utils.PrefsManager
import com.cloud.launcher.utils.RecentsIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class AppMonitorService : AccessibilityService() {

    companion object {
        @Volatile
        private var ref: WeakReference<AppMonitorService>? = null

        val instance: AppMonitorService? get() = ref?.get()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var repository: RecentAppsRepository? = null
    private var lastPackage: String? = null

    @Volatile
    private var settings = LauncherSettings()

    @Volatile
    private var protectedPkgs: Set<String> = emptySet()

    @Volatile
    private var foreground: String? = null

    @Volatile
    private var foregroundIsRoot = false

    private val activityCache = HashMap<String, Boolean>()
    private val launcherClassCache = HashMap<String, String?>()

    private var optimizeJob: Job? = null
    private var receiverRegistered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> onScreenOff()
                Intent.ACTION_SCREEN_ON -> onScreenOn()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        ref = WeakReference(this)
        val prefs = PrefsManager(applicationContext)
        repository = RecentAppsRepository(prefs)
        scope.launch { prefs.settings.collect { settings = it } }
        scope.launch { prefs.protectedApps.collect { protectedPkgs = it.toSet() } }
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            registerReceiver(screenReceiver, filter)
            receiverRegistered = true
        }
        scope.launch {
            if (RootManager.isRootAvailable()) {
                scheduleOptimize(2_000L, false)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == "com.android.systemui") return
        val cls = event.className?.toString()
        trackForeground(pkg, cls)
        if (pkg == lastPackage) return
        if (pkg == applicationContext.packageName) return
        val launchable = try {
            packageManager.getLaunchIntentForPackage(pkg) != null
        } catch (e: Exception) {
            false
        }
        if (!launchable) return
        lastPackage = pkg
        val repo = repository ?: return
        scope.launch { repo.record(pkg) }
        if (settings.autoMemory) scheduleOptimize(3_500L, false)
    }

    private fun trackForeground(pkg: String, cls: String?) {
        if (cls == null) return
        val isActivity = isActivityClass(pkg, cls)
        if (!isActivity) return
        foreground = pkg
        val launcherClass = launcherClassFor(pkg)
        foregroundIsRoot = launcherClass != null && launcherClass == cls
    }

    private fun isActivityClass(pkg: String, cls: String): Boolean {
        val key = "$pkg/$cls"
        activityCache[key]?.let { return it }
        val result = try {
            packageManager.getActivityInfo(ComponentName(pkg, cls), 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (e: Exception) {
            false
        }
        if (activityCache.size > 400) activityCache.clear()
        activityCache[key] = result
        return result
    }

    private fun launcherClassFor(pkg: String): String? {
        if (launcherClassCache.containsKey(pkg)) return launcherClassCache[pkg]
        val cls = try {
            packageManager.getLaunchIntentForPackage(pkg)?.component?.className
        } catch (e: Exception) {
            null
        }
        launcherClassCache[pkg] = cls
        return cls
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code == KeyEvent.KEYCODE_BACK) {
            val pkg = foreground
            if (settings.backKeepProtected && pkg != null && foregroundIsRoot && pkg in protectedPkgs &&
                pkg != applicationContext.packageName
            ) {
                if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
                return true
            }
            return super.onKeyEvent(event)
        }
        if (code != KeyEvent.KEYCODE_APP_SWITCH && code != KeyEvent.KEYCODE_MENU) {
            return super.onKeyEvent(event)
        }
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
            RecentsIntent.open(applicationContext)
        }
        return true
    }

    private fun onScreenOff() {
        val s = settings
        if (s.doubleTapWake) {
            scope.launch {
                if (RootManager.isGranted || RootManager.isRootAvailable()) DoubleTapWake.start()
            }
        }
        if (s.autoMemory) scheduleOptimize(1_500L, true)
    }

    private fun onScreenOn() {
        DoubleTapWake.stop()
    }

    private fun scheduleOptimize(delayMs: Long, screenOff: Boolean) {
        if (!settings.autoMemory) return
        optimizeJob?.cancel()
        optimizeJob = scope.launch {
            delay(delayMs)
            if (!settings.autoMemory) return@launch
            if (!RootManager.isGranted) return@launch
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!screenOff && !pm.isInteractive) return@launch
            if (!MemoryOptimizer.shouldRun(applicationContext, screenOff)) return@launch
            MemoryOptimizer.optimize(applicationContext, protectedPkgs, foreground)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            scheduleOptimize(800L, false)
        }
    }

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        DoubleTapWake.stop()
        if (receiverRegistered) {
            try {
                unregisterReceiver(screenReceiver)
            } catch (e: Exception) {
            }
            receiverRegistered = false
        }
        if (ref?.get() === this) ref = null
        scope.cancel()
        super.onDestroy()
    }
}
