package com.cloud.launcher.root

import android.content.Context
import com.cloud.launcher.R
import com.cloud.launcher.utils.PermissionUtils
import com.cloud.launcher.utils.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object SystemInstaller {

    private const val DIR = "/system/priv-app/CloudLauncher"
    private const val APK_TARGET = "$DIR/CloudLauncher.apk"
    private const val XML_TARGET = "/system/etc/permissions/privapp-permissions-com.cloud.launcher.xml"

    data class Result(val ok: Boolean, val log: String)

    suspend fun install(context: Context): Result = withContext(Dispatchers.IO) {
        val xml = File(context.cacheDir, "privapp.xml")
        try {
            context.resources.openRawResource(R.raw.privapp_permissions).use { input ->
                xml.outputStream().use { input.copyTo(it) }
            }
            xml.setReadable(true, false)
        } catch (e: Exception) {
            return@withContext Result(false, e.message ?: "xml")
        }
        val apk = context.applicationInfo.sourceDir
        val script = listOf(
            "mount -o rw,remount /system || mount -o remount,rw /system || mount -o rw,remount /",
            "mkdir -p $DIR",
            "cp '$apk' $APK_TARGET",
            "chown 0:0 $DIR $APK_TARGET || chown 0.0 $DIR $APK_TARGET",
            "chmod 755 $DIR",
            "chmod 644 $APK_TARGET",
            "chcon u:object_r:system_file:s0 $DIR $APK_TARGET 2>/dev/null",
            "mkdir -p /system/etc/permissions",
            "cp '${xml.absolutePath}' $XML_TARGET",
            "chmod 644 $XML_TARGET",
            "chcon u:object_r:system_file:s0 $XML_TARGET 2>/dev/null",
            "sync",
            "mount -o ro,remount /system 2>/dev/null",
            "[ -s $APK_TARGET ] && [ -s $XML_TARGET ] && echo INSTALL_OK"
        ).joinToString("\n")
        val result = ShellUtils.runAsRoot(script, 90_000L)
        Result(result.stdout.contains("INSTALL_OK"), (result.stdout + "\n" + result.stderr).trim())
    }

    suspend fun removeUserCopyAndReboot(context: Context) = withContext(Dispatchers.IO) {
        val apk = File(context.applicationInfo.sourceDir)
        val lines = mutableListOf<String>()
        if (!PermissionUtils.isSystemApp(context)) {
            val parent = apk.parentFile
            if (parent != null && parent.name == "app") {
                lines += "rm -f '${apk.path}'"
            } else if (parent != null) {
                lines += "rm -rf '${parent.path}'"
            }
        }
        lines += "sync"
        lines += "reboot"
        ShellUtils.runAsRoot(lines.joinToString("\n"), 30_000L)
    }
}
