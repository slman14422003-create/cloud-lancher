package com.cloud.launcher.root

import com.cloud.launcher.utils.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object OomController {

    private fun sh(script: String): String = script.trimIndent().replace('§', '$')

    private const val LIST_PROCS = """
        PS_LIST=§(ps -A -o PID,NAME 2>/dev/null)
        [ -z "§PS_LIST" ] && PS_LIST=§(ps 2>/dev/null | while read U P PP V R W PC S N; do echo "§P §N"; done)
    """

    fun protectScript(pkg: String): String = sh(
        LIST_PROCS + """
        echo "§PS_LIST" | while read PID NAME; do
          case "§NAME" in
            "$pkg"|"$pkg":*)
              echo -17 > /proc/§PID/oom_adj 2>/dev/null
              echo -1000 > /proc/§PID/oom_score_adj 2>/dev/null
              renice -n -20 -p §PID >/dev/null 2>&1 || renice -20 -p §PID >/dev/null 2>&1
              ;;
          esac
        done
        cmd deviceidle whitelist +$pkg >/dev/null 2>&1
        dumpsys deviceidle whitelist +$pkg >/dev/null 2>&1
        cmd appops set $pkg RUN_IN_BACKGROUND allow >/dev/null 2>&1
        cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow >/dev/null 2>&1
        appops set $pkg RUN_IN_BACKGROUND allow >/dev/null 2>&1
        am set-inactive $pkg false >/dev/null 2>&1
        am set-standby-bucket $pkg active >/dev/null 2>&1
        """
    )

    fun unprotectScript(pkg: String): String = sh(
        LIST_PROCS + """
        echo "§PS_LIST" | while read PID NAME; do
          case "§NAME" in
            "$pkg"|"$pkg":*)
              echo 0 > /proc/§PID/oom_adj 2>/dev/null
              echo 0 > /proc/§PID/oom_score_adj 2>/dev/null
              renice -n 0 -p §PID >/dev/null 2>&1 || renice 0 -p §PID >/dev/null 2>&1
              ;;
          esac
        done
        cmd deviceidle whitelist -$pkg >/dev/null 2>&1
        dumpsys deviceidle whitelist -$pkg >/dev/null 2>&1
        """
    )

    fun monitorScript(packages: List<String>): String = sh(
        LIST_PROCS + """
        for PKG in ${packages.joinToString(" ")}; do
          echo "§PS_LIST" | while read PID NAME; do
            case "§NAME" in
              "§PKG"|"§PKG":*)
                echo -17 > /proc/§PID/oom_adj 2>/dev/null
                echo -1000 > /proc/§PID/oom_score_adj 2>/dev/null
                ;;
            esac
          done
          PIDS=§(pidof §PKG)
          if [ -n "§PIDS" ]; then
            echo "§PKG §{PIDS%% *}"
          else
            echo "§PKG 0"
          fi
        done
        """
    )

    suspend fun protectApp(pkg: String): Boolean = withContext(Dispatchers.IO) {
        if (!ShellUtils.isValidPackage(pkg)) return@withContext false
        ShellUtils.runAsRoot(protectScript(pkg)).exitCode == 0
    }

    suspend fun unprotectApp(pkg: String): Boolean = withContext(Dispatchers.IO) {
        if (!ShellUtils.isValidPackage(pkg)) return@withContext false
        ShellUtils.runAsRoot(unprotectScript(pkg)).exitCode == 0
    }

    suspend fun monitorAndReprotect(
        packages: List<String>,
        lastPids: MutableMap<String, Int>
    ): Map<String, Int> = withContext(Dispatchers.IO) {
        val valid = packages.filter { ShellUtils.isValidPackage(it) }
        if (valid.isEmpty()) return@withContext emptyMap<String, Int>()
        val out = ShellUtils.runAsRoot(monitorScript(valid)).stdout
        val current = LinkedHashMap<String, Int>()
        for (line in out.lines()) {
            val parts = line.trim().split(" ")
            if (parts.size == 2) {
                val pid = parts[1].toIntOrNull()
                if (pid != null && parts[0] in valid) current[parts[0]] = pid
            }
        }
        for ((pkg, pid) in current) {
            if (pid > 0 && lastPids[pkg] != pid) {
                ShellUtils.runAsRoot(protectScript(pkg))
            }
            lastPids[pkg] = pid
        }
        lastPids.keys.retainAll(valid.toSet())
        current
    }

    suspend fun relaunchApp(pkg: String): Boolean = withContext(Dispatchers.IO) {
        if (!ShellUtils.isValidPackage(pkg)) return@withContext false
        ShellUtils.runAsRoot(
            "monkey -p $pkg -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1"
        ).exitCode == 0
    }
}
