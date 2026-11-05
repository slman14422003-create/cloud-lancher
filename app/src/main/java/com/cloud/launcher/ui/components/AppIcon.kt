package com.cloud.launcher.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.cloud.launcher.data.AppInfo
import com.cloud.launcher.data.IconShape
import com.cloud.launcher.ui.theme.CloudAccent
import com.cloud.launcher.ui.theme.CloudBlack
import com.cloud.launcher.ui.theme.CloudSurfaceHigh
import com.cloud.launcher.ui.theme.CloudWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object IconCache {
    private val cache = LruCache<String, ImageBitmap>(250)

    var shape: IconShape = IconShape.ORIGINAL
        private set

    var version by mutableIntStateOf(0)
        private set

    fun get(packageName: String): ImageBitmap? = cache.get(packageName)

    fun clear() = cache.evictAll()

    fun setShape(value: IconShape): Boolean {
        if (value == shape) return false
        shape = value
        cache.evictAll()
        version += 1
        return true
    }

    suspend fun load(context: Context, packageName: String): ImageBitmap? = withContext(Dispatchers.IO) {
        val cached = cache.get(packageName)
        if (cached != null) return@withContext cached
        val requested = shape
        try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val bitmap = render(drawable, 112, requested).asImageBitmap()
            if (requested == shape) cache.put(packageName, bitmap)
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    private fun render(drawable: Drawable, px: Int, shape: IconShape): Bitmap {
        if (shape == IconShape.ORIGINAL) return drawable.toBitmap(px, px, Bitmap.Config.ARGB_8888)
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (Build.VERSION.SDK_INT >= 26 && drawable is AdaptiveIconDrawable) {
            val e = (px * 0.25f).toInt()
            drawable.background?.let {
                it.setBounds(-e, -e, px + e, px + e)
                it.draw(canvas)
            }
            drawable.foreground?.let {
                it.setBounds(-e, -e, px + e, px + e)
                it.draw(canvas)
            }
        } else {
            drawable.setBounds(0, 0, px, px)
            drawable.draw(canvas)
        }
        val size = px.toFloat()
        val rect = RectF(0f, 0f, size, size)
        val path = Path()
        when (shape) {
            IconShape.CIRCLE -> path.addOval(rect, Path.Direction.CW)
            IconShape.SQUIRCLE -> path.addRoundRect(rect, size * 0.34f, size * 0.34f, Path.Direction.CW)
            IconShape.ROUNDED -> path.addRoundRect(rect, size * 0.2f, size * 0.2f, Path.Direction.CW)
            IconShape.ORIGINAL -> path.addRect(rect, Path.Direction.CW)
        }
        val mask = Paint(Paint.ANTI_ALIAS_FLAG)
        mask.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        canvas.drawPath(path, mask)
        return bitmap
    }
}

@Composable
fun AppIcon(packageName: String, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version = IconCache.version
    val bitmap by produceState<ImageBitmap?>(initialValue = IconCache.get(packageName), packageName, version) {
        value = IconCache.get(packageName) ?: IconCache.load(context, packageName)
    }
    val current = bitmap
    if (current != null) {
        Image(bitmap = current, contentDescription = null, modifier = modifier.size(size))
    } else {
        Box(modifier = modifier.size(size).clip(CircleShape).background(CloudSurfaceHigh))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTile(
    app: AppInfo,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    showLockBadge: Boolean = false,
    iconSize: Dp = 54.dp,
    showLabel: Boolean = true
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Box {
            AppIcon(packageName = app.packageName, size = iconSize)
            if (showLockBadge) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(CloudAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = CloudBlack, modifier = Modifier.size(11.dp))
                }
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = app.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = CloudWhite,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
