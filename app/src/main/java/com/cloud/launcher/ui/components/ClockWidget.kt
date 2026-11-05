package com.cloud.launcher.ui.components

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
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
import androidx.compose.ui.graphics.Color
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ClockWidget(modifier: Modifier = Modifier) {
    val context = LocalContext.current
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

    val time = remember(now) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)) }
    val date = remember(now) { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(now)) }

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = time,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 86.sp,
                brush = Brush.verticalGradient(listOf(Color.White, Color(0xFFB3E5FC)))
            )
        )
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
            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(CloudAccent))
            Spacer(Modifier.width(8.dp))
            Text(text = date, color = CloudWhite, style = MaterialTheme.typography.bodyMedium)
        }
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
                    StatChip(icon = Icons.Filled.Memory, text = "$freeMb MB", tint = CloudAccent)
                }
            }
        }
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
