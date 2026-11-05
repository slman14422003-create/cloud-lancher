package com.cloud.launcher.root

import com.cloud.launcher.utils.ShellUtils
import java.io.BufferedReader
import java.io.InputStreamReader

object DoubleTapWake {

    private const val MIN_GAP_MS = 60L
    private const val MAX_GAP_MS = 420L
    private const val DEBOUNCE_MS = 1200L

    private val NODES = listOf(
        "/sys/android_touch/doubletap2wake",
        "/sys/touchpanel/double_tap",
        "/proc/touchpanel/double_tap_enable",
        "/proc/touchscreen/double_tap",
        "/proc/tp_gesture",
        "/sys/devices/virtual/input/lge_touch/dt_wake_enabled",
        "/sys/class/touch/switch/double_tap",
        "/sys/devices/platform/soc/soc:touch_dev/double_tap_enable",
        "/sys/bus/i2c/devices/i2c-3/3-0038/doubletap2wake",
        "/sys/class/input/input0/wake_gesture",
        "/sys/class/input/input1/wake_gesture",
        "/sys/class/input/input2/wake_gesture",
        "/sys/class/input/input3/wake_gesture"
    )

    private const val ARM_SCRIPT = """
        for N in __NODES__; do
          [ -w "§N" ] && echo 1 > "§N" 2>/dev/null
        done
        [ -w /sys/class/sec/tsp/cmd ] && echo "aot_enable,1" > /sys/class/sec/tsp/cmd 2>/dev/null
        [ -w /sys/class/sec/tsp/dt2w_enable ] && echo 1 > /sys/class/sec/tsp/dt2w_enable 2>/dev/null
        [ -w /sys/class/sec/sec_touchscreen/cmd ] && echo "aot_enable,1" > /sys/class/sec/sec_touchscreen/cmd 2>/dev/null
        [ -w /sys/devices/virtual/sec/tsp/cmd ] && echo "aot_enable,1" > /sys/devices/virtual/sec/tsp/cmd 2>/dev/null
        [ -w /sys/class/input/input1/enabled ] && echo 1 > /sys/class/input/input1/enabled 2>/dev/null
        [ -w /sys/class/input/input2/enabled ] && echo 1 > /sys/class/input/input2/enabled 2>/dev/null
        [ -w /sys/class/input/input3/enabled ] && echo 1 > /sys/class/input/input3/enabled 2>/dev/null
        [ -w /sys/class/input/input4/enabled ] && echo 1 > /sys/class/input/input4/enabled 2>/dev/null
        exit 0
    """

    private const val FIND_TOUCH_SCRIPT = """
        getevent -pl 2>/dev/null | while read A B C D; do
          case "§A" in
            add) DEV="§D" ;;
            ABS_MT_POSITION_X|ABS_MT_POSITION_X:) echo "§DEV" ;;
          esac
        done
        exit 0
    """

    private fun sh(script: String): String = script.trimIndent().replace('§', '$')

    @Volatile
    private var process: Process? = null

    @Volatile
    private var worker: Thread? = null

    @Volatile
    private var generation = 0

    @Volatile
    private var devices: List<String>? = null

    fun arm() {
        val nodes = NODES.joinToString(" ")
        ShellUtils.runAsRoot(sh(ARM_SCRIPT).replace("__NODES__", nodes), 8_000L)
    }

    @Synchronized
    fun start() {
        stop()
        val gen = ++generation
        val t = Thread { listen(gen) }
        t.isDaemon = true
        worker = t
        t.start()
    }

    @Synchronized
    fun stop() {
        generation++
        try {
            process?.destroy()
        } catch (e: Exception) {
        }
        process = null
        worker?.interrupt()
        worker = null
        val killer = Thread {
            ShellUtils.runAsRoot("pkill -f 'getevent -l /dev/input' 2>/dev/null || killall getevent 2>/dev/null; exit 0", 4_000L)
        }
        killer.isDaemon = true
        killer.start()
    }

    private fun touchDevices(): List<String> {
        devices?.let { if (it.isNotEmpty()) return it }
        val out = ShellUtils.runAsRoot(sh(FIND_TOUCH_SCRIPT), 8_000L).stdout
        val list = out.lineSequence()
            .map { it.trim().substringAfterLast(' ') }
            .filter { it.startsWith("/dev/input/event") }
            .distinct()
            .toList()
        devices = list
        return list
    }

    private fun listen(gen: Int) {
        try {
            arm()
            val devs = touchDevices()
            if (devs.isEmpty() || gen != generation) return
            val pb = ProcessBuilder("su")
            pb.redirectErrorStream(true)
            val p = pb.start()
            process = p
            p.outputStream.bufferedWriter().use { w ->
                w.write("exec getevent -l ${devs.joinToString(" ")}\n")
                w.flush()
                val reader = BufferedReader(InputStreamReader(p.inputStream))
                var down = false
                var lastDown = 0L
                var lastWake = 0L
                while (gen == generation) {
                    val line = reader.readLine() ?: break
                    val isTouchDown = (line.contains("BTN_TOUCH") && line.trimEnd().endsWith("DOWN")) ||
                        (line.contains("ABS_MT_TRACKING_ID") && !line.trimEnd().endsWith("ffffffff"))
                    val isTouchUp = (line.contains("BTN_TOUCH") && line.trimEnd().endsWith("UP")) ||
                        (line.contains("ABS_MT_TRACKING_ID") && line.trimEnd().endsWith("ffffffff"))
                    if (isTouchDown && !down) {
                        down = true
                        val now = System.currentTimeMillis()
                        val gap = now - lastDown
                        if (gap in MIN_GAP_MS..MAX_GAP_MS && now - lastWake > DEBOUNCE_MS) {
                            lastWake = now
                            lastDown = 0L
                            wakeNow()
                        } else {
                            lastDown = now
                        }
                    } else if (isTouchUp) {
                        down = false
                    }
                }
            }
        } catch (e: Exception) {
        } finally {
            try {
                process?.destroy()
            } catch (e: Exception) {
            }
        }
    }

    fun wakeNow() {
        ShellUtils.runAsRoot("input keyevent 224", 5_000L)
    }
}
