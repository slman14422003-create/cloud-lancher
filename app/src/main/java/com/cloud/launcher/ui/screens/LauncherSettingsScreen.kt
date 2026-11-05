package com.cloud.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cloud.launcher.R
import com.cloud.launcher.data.ClockFormat
import com.cloud.launcher.data.ClockStyle
import com.cloud.launcher.data.DrawerBackground
import com.cloud.launcher.data.DrawerLayout
import com.cloud.launcher.data.GlassType
import com.cloud.launcher.data.IconShape
import com.cloud.launcher.ui.LauncherViewModel
import com.cloud.launcher.ui.components.AppIcon
import com.cloud.launcher.ui.components.ClockWidget
import com.cloud.launcher.ui.components.liquidGlass
import com.cloud.launcher.ui.theme.AccentPalette
import com.cloud.launcher.ui.theme.BackgroundPalette
import com.cloud.launcher.ui.theme.CloudAccent
import com.cloud.launcher.ui.theme.CloudBlack
import com.cloud.launcher.ui.theme.CloudDanger
import com.cloud.launcher.ui.theme.CloudGray
import com.cloud.launcher.ui.theme.CloudWhite
import com.cloud.launcher.ui.theme.GlassTintPalette
import com.cloud.launcher.ui.theme.rememberThemeBrush

@Composable
fun LauncherSettingsScreen(vm: LauncherViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val accent = CloudAccent

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(rememberThemeBrush())
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = CloudWhite
                )
            }
            Text(
                text = stringResource(R.string.settings_title),
                color = CloudWhite,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SettingsCard(stringResource(R.string.settings_clock)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0x22000000))
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ClockWidget()
                    }
                    Label(stringResource(R.string.settings_time_format))
                    Chips(
                        options = listOf(
                            ClockFormat.H24 to stringResource(R.string.settings_h24),
                            ClockFormat.H12 to stringResource(R.string.settings_h12)
                        ),
                        selected = s.clockFormat,
                        onSelect = { v -> vm.updateSettings { it.copy(clockFormat = v) } }
                    )
                    if (s.clockFormat == ClockFormat.H12) {
                        SwitchRow(stringResource(R.string.settings_show_ampm), s.showAmPm) { v ->
                            vm.updateSettings { it.copy(showAmPm = v) }
                        }
                    }
                    Label(stringResource(R.string.settings_clock_style))
                    Chips(
                        options = listOf(
                            ClockStyle.DIGITAL to stringResource(R.string.settings_style_digital),
                            ClockStyle.THIN to stringResource(R.string.settings_style_thin),
                            ClockStyle.STACKED to stringResource(R.string.settings_style_stacked),
                            ClockStyle.ANALOG to stringResource(R.string.settings_style_analog)
                        ),
                        selected = s.clockStyle,
                        onSelect = { v -> vm.updateSettings { it.copy(clockStyle = v) } }
                    )
                    SwitchRow(stringResource(R.string.settings_show_date), s.showDate) { v ->
                        vm.updateSettings { it.copy(showDate = v) }
                    }
                    SwitchRow(stringResource(R.string.settings_show_stats), s.showStats) { v ->
                        vm.updateSettings { it.copy(showStats = v) }
                    }
                }
            }

            item {
                SettingsCard(stringResource(R.string.settings_theme)) {
                    Label(stringResource(R.string.settings_background))
                    Swatches(
                        brushes = BackgroundPalette.map { Brush.verticalGradient(listOf(it.top, it.bottom)) },
                        selected = s.backgroundIndex,
                        onSelect = { i -> vm.updateSettings { it.copy(backgroundIndex = i) } }
                    )
                    Label(stringResource(R.string.settings_accent))
                    Swatches(
                        brushes = AccentPalette.map { SolidColor(it) },
                        selected = s.accentIndex,
                        onSelect = { i -> vm.updateSettings { it.copy(accentIndex = i) } }
                    )
                }
            }

            item {
                SettingsCard(stringResource(R.string.settings_glass)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(accent, Color(0xFFB388FF), Color(0xFFFF80AB))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.72f)
                                .height(52.dp)
                                .liquidGlass(RoundedCornerShape(26.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.settings_glass_preview),
                                color = CloudWhite,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    Label(stringResource(R.string.settings_glass_type))
                    Chips(
                        options = listOf(
                            GlassType.LIQUID to stringResource(R.string.settings_glass_liquid),
                            GlassType.FROST to stringResource(R.string.settings_glass_frost),
                            GlassType.CLEAR to stringResource(R.string.settings_glass_clear),
                            GlassType.SOLID to stringResource(R.string.settings_glass_solid)
                        ),
                        selected = s.glassType,
                        onSelect = { v -> vm.updateSettings { it.copy(glassType = v) } }
                    )
                    Label(stringResource(R.string.settings_glass_tint))
                    Swatches(
                        brushes = GlassTintPalette.map { SolidColor(it ?: accent) },
                        selected = s.glassTintIndex,
                        onSelect = { i -> vm.updateSettings { it.copy(glassTintIndex = i) } }
                    )
                    SliderRow(
                        label = stringResource(R.string.settings_glass_intensity),
                        valueText = (s.glassIntensity * 100).toInt().toString() + "%",
                        value = s.glassIntensity,
                        range = 0f..1f,
                        steps = 0,
                        onChange = { v -> vm.updateSettings { it.copy(glassIntensity = v) } }
                    )
                }
            }

            item {
                SettingsCard(stringResource(R.string.settings_library)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0x22000000))
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (app in apps.take(4)) {
                            AppIcon(packageName = app.packageName, size = s.drawerIconSize.dp)
                        }
                    }
                    Label(stringResource(R.string.settings_favorites_count))
                    Chips(
                        options = listOf(3, 4, 5).map { it to it.toString() },
                        selected = s.favoritesCount,
                        onSelect = { v -> vm.updateSettings { it.copy(favoritesCount = v) } }
                    )
                    Label(stringResource(R.string.settings_layout))
                    Chips(
                        options = listOf(
                            DrawerLayout.GRID to stringResource(R.string.settings_layout_grid),
                            DrawerLayout.LIST to stringResource(R.string.settings_layout_list)
                        ),
                        selected = s.drawerLayout,
                        onSelect = { v -> vm.updateSettings { it.copy(drawerLayout = v) } }
                    )
                    if (s.drawerLayout == DrawerLayout.GRID) {
                        Label(stringResource(R.string.settings_columns))
                        Chips(
                            options = listOf(3, 4, 5, 6).map { it to it.toString() },
                            selected = s.drawerColumns,
                            onSelect = { v -> vm.updateSettings { it.copy(drawerColumns = v) } }
                        )
                    }
                    SliderRow(
                        label = stringResource(R.string.settings_icon_size),
                        valueText = s.drawerIconSize.toString(),
                        value = s.drawerIconSize.toFloat(),
                        range = 40f..72f,
                        steps = 7,
                        onChange = { v -> vm.updateSettings { it.copy(drawerIconSize = v.toInt()) } }
                    )
                    Label(stringResource(R.string.settings_icon_shape))
                    Chips(
                        options = listOf(
                            IconShape.ORIGINAL to stringResource(R.string.settings_shape_original),
                            IconShape.CIRCLE to stringResource(R.string.settings_shape_circle),
                            IconShape.SQUIRCLE to stringResource(R.string.settings_shape_squircle),
                            IconShape.ROUNDED to stringResource(R.string.settings_shape_rounded)
                        ),
                        selected = s.iconShape,
                        onSelect = { v -> vm.updateSettings { it.copy(iconShape = v) } }
                    )
                    SwitchRow(stringResource(R.string.settings_show_labels), s.drawerShowLabels) { v ->
                        vm.updateSettings { it.copy(drawerShowLabels = v) }
                    }
                    Label(stringResource(R.string.settings_library_bg))
                    Chips(
                        options = listOf(
                            DrawerBackground.THEME to stringResource(R.string.settings_bg_theme),
                            DrawerBackground.BLACK to stringResource(R.string.settings_bg_black),
                            DrawerBackground.ACCENT to stringResource(R.string.settings_bg_accent)
                        ),
                        selected = s.drawerBackground,
                        onSelect = { v -> vm.updateSettings { it.copy(drawerBackground = v) } }
                    )
                }
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(RoundedCornerShape(50))
                        .clickable { vm.resetSettings() }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.settings_reset),
                        color = CloudDanger,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(RoundedCornerShape(26.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = title, color = CloudAccent, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun Label(text: String) {
    Text(text = text, color = CloudGray, style = MaterialTheme.typography.labelSmall)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Chips(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for ((value, label) in options) {
            val active = value == selected
            val shape = RoundedCornerShape(50)
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(if (active) CloudAccent.copy(alpha = 0.25f) else Color(0x14FFFFFF))
                    .border(1.dp, if (active) CloudAccent else Color(0x1FFFFFFF), shape)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (active) CloudWhite else CloudGray,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Swatches(brushes: List<Brush>, selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        brushes.forEachIndexed { index, brush ->
            val active = index == selected
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(brush)
                    .border(if (active) 2.5.dp else 1.dp, if (active) CloudWhite else Color(0x40FFFFFF), CircleShape)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center
            ) {
                if (active) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = CloudWhite, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = CloudWhite,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = CloudWhite,
                checkedTrackColor = CloudAccent,
                uncheckedThumbColor = CloudGray,
                uncheckedTrackColor = Color(0x26FFFFFF),
                uncheckedBorderColor = Color(0x33FFFFFF)
            )
        )
    }
}

@Composable
private fun SliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                color = CloudWhite,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(text = valueText, color = CloudGray, style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = CloudAccent,
                activeTrackColor = CloudAccent,
                inactiveTrackColor = Color(0x33FFFFFF)
            )
        )
    }
}
