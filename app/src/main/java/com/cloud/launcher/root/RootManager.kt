package com.cloud.launcher.root

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.cloud.launcher.service.AppMonitorService
import com.cloud.launcher.utils.PermissionUtils
import com.cloud.launcher.utils.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RootManager {

    @Volatile
    private var granted: Boolean = false

    val isGranted: Boolean get() = granted

    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        val result = ShellUtils.runAsRoot("id", 30_000L)
        val ok = result.stdout.contains("uid=0")
        granted = ok
        ok
    }

    suspend fun requestRoot(): Boolean = withContext(Dispatchers.IO) {
        val result = ShellUtils.runAsRoot("id", 60_000L)
        val ok = result.stdout.contains("uid=0")
        granted = ok
        ok
    }

    suspend fun executeCommand(cmd: String): String = withContext(Dispatchers.IO) {
        val result = ShellUtils.runAsRoot(cmd)
        if (result.isSuccess || result.stdout.isNotEmpty()) {
            result.stdout
        } else {
            val reason = if (result.stderr.isNotEmpty()) result.stderr else "exit ${result.exitCode}"
            "ERROR: $reason"
        }
    }

    suspend fun executeCommands(cmds: List<String>): String =
        executeCommand(cmds.joinToString("\n"))

    suspend fun enableAccessibility(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (PermissionUtils.isAccessibilityEnabled(context)) return@withContext true
        val component = ComponentName(context, AppMonitorService::class.java).flattenToString()
        val current = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        val value = if (current.isNullOrBlank() || current == "null") component else "$current:$component"
        ShellUtils.runAsRoot(
            "settings put secure enabled_accessibility_services $value\n" +
                "settings put secure accessibility_enabled 1"
        )
        PermissionUtils.isAccessibilityEnabled(context)
    }
}
