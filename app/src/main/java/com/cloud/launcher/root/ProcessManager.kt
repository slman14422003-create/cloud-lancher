package com.cloud.launcher.root

import android.app.ActivityManager
import android.content.Context
import com.cloud.launcher.utils.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class RunningApp(val packageName: String, val pid: Int, val rssKb: Long) {
    val rssMb: Long get() = rssKb / 1024L
}

data class CloseResult(val ok: Boolean, val freedKb: Long)

data class KillAllResult(val closed: List<String>, val failed: List<String>, val freedKb: Long)

object ProcessManager {

    private const val PS_COMMAND = "ps -A -o PID,RSS,NAME 2>/dev/null || ps"
    private const val PATH_FIX = "export PATH=/system/bin:/system/xbin:/sbin:/vendor/bin:§PATH"
    private val WHITESPACE = Regex("\\s+")

    private fun sh(script: String): String = script.replace('§', '$')

    suspend fun getRunningApps(installed: Set<String>): List<RunningApp> = withContext(Dispatchers.IO) {
        val result = ShellUtils.runAsRoot(PS_COMMAND)
        parsePs(result.stdout, installed)
    }

    fun parsePs(output: String, installed: Set<String>): List<RunningApp> {
        val pids = HashMap<String, Int>()
        val rss = HashMap<String, Long>()
        for (line in output.lineSequence()) {
            val t = line.trim().split(WHITESPACE)
            val pid: Int?
            val mem: Long?
            val name: String
            if (t.size == 3) {
                pid = t[0].toIntOrNull()
                mem = t[1].toLongOrNull()
                name = t[2]
            } else if (t.size >= 8) {
                pid = t[1].toIntOrNull()
                mem = t[4].toLongOrNull()
                name = t[t.size - 1]
            } else {
                continue
            }
            if (pid == null || mem == null) continue
            val pkg = name.substringBefore(':')
            if (pkg !in installed) continue
            rss[pkg] = (rss[pkg] ?: 0L) + mem
            if (name == pkg || !pids.containsKey(pkg)) pids[pkg] = pid
        }
        return pids.map { RunningApp(it.key, it.value, rss[it.key] ?: 0L) }
    }

    suspend fun getPid(packageName: String): Int? = withContext(Dispatchers.IO) {
        if (!ShellUtils.isValidPackage(packageName)) return@withContext null
        val direct = ShellUtils.runAsRoot("pidof $packageName").stdout
            .trim().split(WHITESPACE).firstOrNull()?.toIntOrNull()
        if (direct != null) {
            direct
        } else {
            val ps = ShellUtils.runAsRoot(PS_COMMAND).stdout
            parsePs(ps, setOf(packageName)).firstOrNull()?.pid
        }
    }

    fun forceStopScript(packageName: String): String = sh(
        PATH_FIX + "\n" +
            "am force-stop --user 0 $packageName >/dev/null 2>&1 || am force-stop $packageName >/dev/null 2>&1\n" +
            "am kill $packageName >/dev/null 2>&1\n" +
            "exit 0\n"
    )

    fun killAllScript(packages: List<String>): String {
        val valid = packages.filter { ShellUtils.isValidPackage(it) }
        return sh(
            PATH_FIX + "\n" +
                "N=0\n" +
                "for PKG in ${valid.joinToString(" ")}; do\n" +
                "  (am force-stop --user 0 §PKG >/dev/null 2>&1 || am force-stop §PKG >/dev/null 2>&1) &\n" +
                "  N=§((N+1))\n" +
                "  if [ §N -ge 4 ]; then wait; N=0; fi\n" +
                "done\n" +
                "wait\n" +
                "exit 0\n"
        )
    }

    fun hardKillScript(packages: List<String>): String {
        val valid = packages.filter { ShellUtils.isValidPackage(it) }
        if (valid.isEmpty()) return "exit 0"
        val pattern = valid.joinToString("|") { "\"$it\"|\"$it\":*" }
        return sh(
            PATH_FIX + "\n" +
                "LIST=§(ps -A -o PID,NAME 2>/dev/null)\n" +
                "[ -z \"§LIST\" ] && LIST=§(ps 2>/dev/null | while read U P PP V R W PC S N; do echo \"§P §N\"; done)\n" +
                "echo \"§LIST\" | while read PID NAME; do\n" +
                "  case \"§NAME\" in\n" +
                "    $pattern) kill -9 §PID >/dev/null 2>&1 ;;\n" +
                "  esac\n" +
                "done\n" +
                "exit 0\n"
        )
    }

    private fun snapshot(packages: Set<String>): Map<String, RunningApp> =
        parsePs(ShellUtils.runAsRoot(PS_COMMAND).stdout, packages).associateBy { it.packageName }

    suspend fun closePackage(packageName: String): CloseResult = withContext(Dispatchers.IO) {
        if (!ShellUtils.isValidPackage(packageName)) return@withContext CloseResult(false, 0L)
        val single = setOf(packageName)
        val before = snapshot(single)[packageName]
        ShellUtils.runAsRoot(forceStopScript(packageName))
        delay(250L)
        var after = snapshot(single)[packageName]
        if (after != null) {
            ShellUtils.runAsRoot(hardKillScript(listOf(packageName)))
            delay(350L)
            after = snapshot(single)[packageName]
        }
        CloseResult(after == null, if (after == null) before?.rssKb ?: 0L else 0L)
    }

    suspend fun killAll(packages: List<String>): KillAllResult = withContext(Dispatchers.IO) {
        val valid = packages.filter { ShellUtils.isValidPackage(it) }
        if (valid.isEmpty()) return@withContext KillAllResult(emptyList(), emptyList(), 0L)
        val all = valid.toSet()
        val before = snapshot(all)
        ShellUtils.runAsRoot(killAllScript(valid), 60_000L)
        delay(300L)
        var after = snapshot(all)
        if (after.isNotEmpty()) {
            ShellUtils.runAsRoot(hardKillScript(after.keys.toList()), 30_000L)
            delay(400L)
            after = snapshot(all)
        }
        val closed = valid.filter { it !in after }
        val freed = closed.sumOf { before[it]?.rssKb ?: 0L }
        KillAllResult(closed, after.keys.toList(), freed)
    }

    fun killBackground(context: Context, packageName: String) {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.killBackgroundProcesses(packageName)
        } catch (e: Exception) {
        }
    }
}
