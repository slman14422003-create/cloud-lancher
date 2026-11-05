package com.cloud.launcher.ui.components

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloud.launcher.ui.theme.CloudAccent
import com.cloud.launcher.ui.theme.CloudGray
import com.cloud.launcher.ui.theme.CloudSuccess
import com.cloud.launcher.ui.theme.CloudWhite
import kotlinx.coroutines.delay
import com.cloud.launcher.data.ClockFormat
import com.cloud.launcher.data.ClockStyle
import com.cloud.launcher.ui.theme.LocalLauncherSettings
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ClockWidget(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings = LocalLauncherSettings.current
    val accent = CloudAccent
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var battery by remember { mutableIntStateOf(-1) }
    var freeMb by remember { mutableIntStateOf(-1) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000L - (now % 60_000L) + 50L)
        }
    }

    LaunchedEffect(Unit) {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        while (true) {
            battery = try {
                bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            } catch (e: Exception) {
                -1
            }
            am.getMemoryInfo(info)
            freeMb = (info.availMem / (1024L * 1024L)).toInt()
            delay(15_000L)
        }
    }

    val is12 = settings.clockFormat == ClockFormat.H12
    val hours = remember(now, is12) {
        SimpleDateFormat(if (is12) "h" else "HH", Locale.getDefault()).format(Date(now))
    }
    val minutes = remember(now) { SimpleDateFormat("mm", Locale.getDefault()).format(Date(now)) }
    val marker = remember(now) { SimpleDateFormat("a", Locale.getDefault()).format(Date(now)) }
    val date = remember(now) { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(now)) }
    val showMarker = is12 && settings.showAmPm

    val timeBrush = Brush.verticalGradient(listOf(Color.White, lerp(Color.White, accent, 0.45f)))

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            when (settings.clockStyle) {
                ClockStyle.DIGITAL -> TimeRow(
                    text = "$hours:$minutes",
                    marker = marker,
                    showMarker = showMarker,
                    brush = timeBrush,
                    fontSize = 86.sp,
                    weight = FontWeight.Bold
                )

                ClockStyle.THIN -> TimeRow(
                    text = "$hours:$minutes",
                    marker = marker,
                    showMarker = showMarker,
                    brush = timeBrush,
                    fontSize = 92.sp,
                    weight = FontWeight.ExtraLight
                )

                ClockStyle.STACKED -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = hours,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 76.sp,
                            lineHeight = 70.sp,
                            letterSpacing = 0.sp,
                            brush = timeBrush
                        )
                    )
                    Text(
                        text = minutes,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 76.sp,
                            lineHeight = 70.sp,
                            letterSpacing = 0.sp,
                            fontWeight = FontWeight.Light,
                            color = accent
                        )
                    )
                    if (showMarker) {
                        Text(text = marker, color = CloudGray, style = MaterialTheme.typography.labelSmall)
                    }
                }

                ClockStyle.ANALOG -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnalogClock(now = now, dimension = 176.dp, accent = accent)
                    if (showMarker) {
                        Spacer(Modifier.height(4.dp))
                        Text(text = marker, color = CloudGray, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        if (settings.showDate) {
            Spacer(Modifier.height(6.dp))
            val pill = RoundedCornerShape(50)
            Row(
                modifier = Modifier
                    .clip(pill)
                    .background(CloudWhite.copy(alpha = 0.08f))
                    .border(1.dp, CloudWhite.copy(alpha = 0.12f), pill)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(accent))
                Spacer(Modifier.width(8.dp))
                Text(text = date, color = CloudWhite, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (settings.showStats) {
            Spacer(Modifier.height(12.dp))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (battery >= 0) {
                        StatChip(
                            icon = Icons.Filled.BatteryFull,
                            text = "$battery%",
                            tint = if (battery <= 15) Color(0xFFFF5252) else CloudSuccess
                        )
                    }
                    if (freeMb >= 0) {
                        StatChip(icon = Icons.Filled.Memory, text = "$freeMb MB", tint = accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeRow(
    text: String,
    marker: String,
    showMarker: Boolean,
    brush: Brush,
    fontSize: androidx.compose.ui.unit.TextUnit,
    weight: FontWeight
) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = text,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = fontSize,
                fontWeight = weight,
                brush = brush
            )
        )
        if (showMarker) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = marker,
                color = CloudGray,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }
}

private fun polar(center: Offset, radius: Float, degrees: Float): Offset {
    val rad = (degrees - 90f) * PI / 180.0
    return Offset(center.x + (cos(rad) * radius).toFloat(), center.y + (sin(rad) * radius).toFloat())
}

@Composable
private fun AnalogClock(now: Long, dimension: Dp, accent: Color) {
    val calendar = remember(now) { Calendar.getInstance().apply { timeInMillis = now } }
    val minute = calendar.get(Calendar.MINUTE)
    val hour = calendar.get(Calendar.HOUR) + minute / 60f
    Canvas(modifier = Modifier.size(dimension)) {
        val c = center
        val r = this.size.minDimension / 2f
        drawCircle(color = Color.White.copy(alpha = 0.06f), radius = r, center = c)
        drawCircle(
            color = Color.White.copy(alpha = 0.22f),
            radius = r - 1.dp.toPx(),
            center = c,
            style = Stroke(width = 1.5.dp.toPx())
        )
        for (i in 0 until 60) {
            val major = i % 5 == 0
            val outer = r * 0.9f
            val inner = outer - if (major) r * 0.11f else r * 0.05f
            drawLine(
                color = if (i % 15 == 0) accent else Color.White.copy(alpha = if (major) 0.8f else 0.3f),
                start = polar(c, inner, i * 6f),
                end = polar(c, outer, i * 6f),
                strokeWidth = if (major) 2.dp.toPx() else 1.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        drawLine(
            color = Color.White,
            start = c,
            end = polar(c, r * 0.5f, hour * 30f),
            strokeWidth = 5.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White,
            start = c,
            end = polar(c, r * 0.75f, minute * 6f),
            strokeWidth = 3.5.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawCircle(color = accent, radius = 5.dp.toPx(), center = c)
    }
}

@Composable
private fun StatChip(icon: ImageVector, text: String, tint: Color) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(text = text, color = CloudGray, style = MaterialTheme.typography.labelSmall)
    }
}
