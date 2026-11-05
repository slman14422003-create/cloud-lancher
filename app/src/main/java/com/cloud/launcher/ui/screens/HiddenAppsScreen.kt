package com.cloud.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cloud.launcher.R
import com.cloud.launcher.ui.LauncherViewModel
import com.cloud.launcher.ui.components.AppIcon
import com.cloud.launcher.ui.components.liquidGlass
import com.cloud.launcher.ui.theme.CloudAccent
import com.cloud.launcher.ui.theme.CloudGray
import com.cloud.launcher.ui.theme.CloudWhite
import com.cloud.launcher.ui.theme.rememberThemeBrush

@Composable
fun HiddenAppsScreen(vm: LauncherViewModel, onBack: () -> Unit) {
    val hidden by vm.hiddenPkgs.collectAsStateWithLifecycle()
    val appMap by vm.appMap.collectAsStateWithLifecycle()
    val apps = remember(hidden, appMap) {
        hidden.mapNotNull { appMap[it] }.sortedBy { it.label.lowercase() }
    }

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
                text = stringResource(R.string.hidden_title) + "  (" + apps.size + ")",
                color = CloudWhite,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
        }

        if (apps.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.hidden_empty),
                    color = CloudGray,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
            ) {
                items(apps, key = { it.packageName }) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .liquidGlass(RoundedCornerShape(20.dp))
                            .clickable { vm.launchApp(app.packageName) }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(packageName = app.packageName, size = 46.dp)
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = app.label,
                            color = CloudWhite,
                            maxLines = 1,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = stringResource(R.string.hidden_unhide),
                            color = CloudAccent,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .clickable { vm.toggleHidden(app.packageName) }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}
