package com.cloud.launcher.root

import android.app.ActivityManager
import android.content.Context
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import com.cloud.launcher.utils.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

object MemoryOptimizer {

    private const val MB = 1024L * 1024L
    private const val MIN_INTERVAL_MS = 20_000L
    private const val PRESSURE_RATIO = 0.42f
    private const val SCREEN_OFF_RATIO = 0.65f
    private const val MIN_ADJ = 500

    private val CORE_PREFIXES = listOf(
        "android",
        "system",
        "com.android.systemui",
        "com.android.phone",
        "com.android.server",
        "com.android.providers.",
        "com.android.bluetooth",
        "com.android.nfc",
        "com.android.settings",
        "com.android.shell",
        "com.android.location",
        "com.android.inputdevices",
        "com.android.keychain",
        "com.android.se",
        "com.android.incallui",
        "com.android.cellbroadcastreceiver",
        "com.android.emergency",
        "com.android.stk",
        "com.android.wallpaper",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.google.process.",
        "com.qualcomm.",
        "com.qti.",
        "com.mediatek.",
        "com.samsung.android.incallui",
        "com.samsung.android.providers.",
        "com.samsung.android.server.",
        "com.sec.phone",
        "com.sec.imsservice",
        "com.sec.epdg",
        "com.sec.android.app.launcher",
        "com.sec.android.inputmethod",
        "com.samsung.android.knox",
        "com.samsung.android.bluetooth",
        "com.samsung.android.network",
        "com.samsung.android.app.telephonyui",
        "com.samsung.android.dialer",
        "com.samsung.android.messaging",
        "com.samsung.accessibility",
        "com.samsung.android.smartcallprovider",
        "com.samsung.android.scs"
    )

    private val mutex = Mutex()

    @Volatile
    private var lastRun = 0L

    private val _lastFreedMb = MutableStateFlow(0L)
    val lastFreedMb: StateFlow<Long> = _lastFreedMb.asStateFlow()

    private val _lastKilled = MutableStateFlow(0)
    val lastKilled: StateFlow<Int> = _lastKilled.asStateFlow()

    private const val SCAN_SCRIPT = """
        PS_LIST=§(ps -A -o PID,NAME 2>/dev/null)
        [ -z "§PS_LIST" ] && PS_LIST=§(ps 2>/dev/null | while read U P PP V R W PC S N; do echo "§P §N"; done)
        echo "§PS_LIST" | while read PID NAME; do
          case "§NAME" in
            *.*) ;;
            *) continue ;;
          esac
          ADJ=§(cat /proc/§PID/oom_score_adj 2>/dev/null)
          [ -z "§ADJ" ] && continue
          [ "§ADJ" -lt $MIN_ADJ ] && continue
          echo "§{NAME%%:*}"
        done
    """

    private fun sh(script: String): String = script.trimIndent().replace('§', '$')

    fun memoryRatio(context: Context): Float {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        if (info.totalMem <= 0L) return 0f
        return 1f - info.availMem.toFloat() / info.totalMem.toFloat()
    }

    fun availMb(context: Context): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.availMem / MB
    }

    fun shouldRun(context: Context, screenOff: Boolean): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastRun < MIN_INTERVAL_MS) return false
        val used = memoryRatio(context)
        return if (screenOff) used > 1f - SCREEN_OFF_RATIO else used > PRESSURE_RATIO
    }

    private fun essentialPackages(context: Context, extra: Collection<String>): Set<String> {
        val keep = HashSet<String>()
        keep.add(context.packageName)
        keep.addAll(extra)
        try {
            val ime = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ime?.substringBefore('/')?.let { if (it.isNotBlank()) keep.add(it) }
        } catch (e: Exception) {
        }
        try {
            Telephony.Sms.getDefaultSmsPackage(context)?.let { keep.add(it) }
        } catch (e: Exception) {
        }
        try {
            val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            tm.defaultDialerPackage?.let { keep.add(it) }
        } catch (e: Exception) {
        }
        try {
            val home = android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_HOME)
            context.packageManager.queryIntentActivities(home, 0).forEach { keep.add(it.activityInfo.packageName) }
        } catch (e: Exception) {
        }
        return keep
    }

    private fun isCore(pkg: String): Boolean {
        for (p in CORE_PREFIXES) {
            if (p.endsWith(".")) {
                if (pkg.startsWith(p)) return true
            } else if (pkg == p || pkg.startsWith("$p.") && p.startsWith("com.")) {
                return true
            }
        }
        return pkg == "android" || pkg.startsWith("android.")
    }

    suspend fun optimize(
        context: Context,
        protectedPkgs: Collection<String>,
        foreground: String?
    ): Boolean = withContext(Dispatchers.IO) {
        if (!RootManager.isGranted) return@withContext false
        if (!mutex.tryLock()) return@withContext false
        try {
            lastRun = System.currentTimeMillis()
            val before = availMb(context)
            val keep = essentialPackages(context, protectedPkgs + listOfNotNull(foreground))
            val scan = ShellUtils.runAsRoot(sh(SCAN_SCRIPT.replace("\$MIN_ADJ", MIN_ADJ.toString())), 15_000L)
            if (!scan.isSuccess && scan.stdout.isBlank()) return@withContext false
            val targets = scan.stdout.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && ShellUtils.isValidPackage(it) }
                .distinct()
                .filter { it !in keep && !isCore(it) }
                .toList()
            if (targets.isEmpty()) {
                _lastKilled.value = 0
                _lastFreedMb.value = 0L
                return@withContext true
            }
            val script = StringBuilder()
            script.append("export PATH=/system/bin:/system/xbin:/sbin:/vendor/bin:\$PATH\n")
            var n = 0
            for (pkg in targets) {
                script.append("(am kill --user 0 $pkg >/dev/null 2>&1 || am kill $pkg >/dev/null 2>&1) &\n")
                n++
                if (n % 4 == 0) script.append("wait\n")
            }
            script.append("wait\n")
            script.append("sync\n")
            script.append("echo 1 > /proc/sys/vm/compact_memory 2>/dev/null\n")
            script.append("echo 0 > /proc/sys/vm/drop_caches 2>/dev/null\n")
            ShellUtils.runAsRoot(script.toString(), 30_000L)
            kotlinx.coroutines.delay(400L)
            val after = availMb(context)
            _lastKilled.value = targets.size
            _lastFreedMb.value = (after - before).coerceAtLeast(0L)
            true
        } finally {
            mutex.unlock()
        }
    }
}
