package com.cloud.launcher.root

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.cloud.launcher.MainActivity
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
        if (PermissionUtils.hasWriteSecureSettings(context)) {
            try {
                Settings.Secure.putString(
                    context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, value
                )
                Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            } catch (e: Exception) {
                // fall through to root
            }
            if (PermissionUtils.isAccessibilityEnabled(context)) return@withContext true
        }
        ShellUtils.runAsRoot(
            "settings put secure enabled_accessibility_services $value\n" +
                "settings put secure accessibility_enabled 1"
        )
        PermissionUtils.isAccessibilityEnabled(context)
    }

    suspend fun grantAllPermissions(context: Context): Boolean = withContext(Dispatchers.IO) {
        val pkg = context.packageName
        if (!ShellUtils.isValidPackage(pkg)) return@withContext false
        ShellUtils.runAsRoot(
            listOf(
                "pm grant $pkg android.permission.POST_NOTIFICATIONS",
                "pm grant $pkg android.permission.WRITE_SECURE_SETTINGS",
                "pm grant $pkg android.permission.GET_TASKS",
                "pm grant $pkg android.permission.REAL_GET_TASKS",
                "pm grant $pkg android.permission.FORCE_STOP_PACKAGES",
                "appops set $pkg GET_USAGE_STATS allow",
                "appops set $pkg android:get_usage_stats allow",
                "appops set $pkg SYSTEM_ALERT_WINDOW allow",
                "appops set $pkg android:system_alert_window allow",
                "dumpsys deviceidle whitelist +$pkg"
            ).joinToString("\n")
        )
        enableAccessibility(context)
        PermissionUtils.hasUsageAccess(context)
    }

    suspend fun compileForSpeed(context: Context): Boolean = withContext(Dispatchers.IO) {
        val pkg = context.packageName
        if (!ShellUtils.isValidPackage(pkg)) return@withContext false
        val result = ShellUtils.runAsRoot(
            "pm compile -m speed -f $pkg 2>&1 || cmd package compile -m speed -f $pkg 2>&1",
            240_000L
        )
        result.stdout.contains("Success", ignoreCase = true) || result.isSuccess
    }

    suspend fun setDefaultLauncher(context: Context): Boolean = withContext(Dispatchers.IO) {
        val pkg = context.packageName
        if (!ShellUtils.isValidPackage(pkg)) return@withContext false
        val component = ComponentName(context, MainActivity::class.java).flattenToShortString()
        ShellUtils.runAsRoot(
            listOf(
                "cmd role add-role-holder --user 0 android.app.role.HOME $pkg 0",
                "cmd package set-home-activity --user 0 $component",
                "pm set-home-activity --user 0 $component",
                "pm set-home-activity $component"
            ).joinToString("\n")
        )
        PermissionUtils.isDefaultLauncher(context)
    }
}
