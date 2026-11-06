package com.cloud.launcher

import android.app.Application
import android.content.ComponentCallbacks2
import android.os.Process
import com.cloud.launcher.data.AppRepository
import com.cloud.launcher.ui.components.IconCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CloudApp : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                val apps = AppRepository(applicationContext).loadApps()
                for (app in apps.take(48)) {
                    IconCache.load(applicationContext, app.packageName)
                }
            } catch (e: Exception) {
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) IconCache.clear()
    }
}
