package com.cloud.launcher.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cloud.launcher.R
import com.cloud.launcher.ui.LauncherViewModel
import com.cloud.launcher.ui.theme.CloudAccent
import com.cloud.launcher.ui.theme.CloudBlack
import com.cloud.launcher.ui.theme.CloudDanger
import com.cloud.launcher.ui.theme.CloudGray
import com.cloud.launcher.ui.theme.CloudSuccess
import com.cloud.launcher.ui.theme.CloudWhite
import com.cloud.launcher.utils.PermissionUtils

private const val STEP_COUNT = 6

@Composable
fun SetupWizard(vm: LauncherViewModel, onRequestNotifications: () -> Unit, onFinish: () -> Unit) {
    val context = LocalContext.current
    val root by vm.rootAvailable.collectAsStateWithLifecycle()
    val setup by vm.setup.collectAsStateWithLifecycle()
    val install by vm.systemInstall.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableIntStateOf(0) }
    val last = STEP_COUNT - 1

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler { if (step > 0) step -= 1 else onFinish() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CloudBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                for (i in 0 until STEP_COUNT) {
                    Box(
                        modifier = Modifier
                            .size(if (i == step) 22.dp else 8.dp, 8.dp)
                            .background(if (i <= step) CloudAccent else CloudGray.copy(alpha = 0.3f), CircleShape)
                    )
                }
            }
            if (step < last) {
                TextButton(onClick = onFinish) {
                    Text(stringResource(R.string.setup_skip), color = CloudGray)
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp)
        ) {
            when (step) {
                0 -> StepFrame(Icons.Filled.RocketLaunch, R.string.setup_welcome_title, R.string.setup_welcome_body) {}
                1 -> StepFrame(Icons.Filled.Security, R.string.setup_step_root_title, R.string.setup_root_note) {
                    StatusRow(stringResource(R.string.setup_root), root == true, stringResource(R.string.setup_grant)) {
                        vm.requestRoot()
                    }
                }
                2 -> StepFrame(Icons.Filled.VerifiedUser, R.string.setup_step_perm_title, R.string.setup_step_perm_body) {
                    StatusRow(stringResource(R.string.setup_usage), setup.usageAccess, stringResource(R.string.setup_grant)) {
                        PermissionUtils.openUsageAccessSettings(context)
                    }
                    StatusRow(stringResource(R.string.setup_accessibility), setup.accessibility, stringResource(R.string.setup_grant)) {
                        PermissionUtils.openAccessibilitySettings(context)
                    }
                    StatusRow(stringResource(R.string.setup_notifications), setup.notifications, stringResource(R.string.setup_grant)) {
                        onRequestNotifications()
                    }
                    StatusRow(stringResource(R.string.setup_overlay), setup.overlay, stringResource(R.string.setup_grant)) {
                        PermissionUtils.openOverlaySettings(context)
                    }
                    StatusRow(stringResource(R.string.setup_battery), setup.battery, stringResource(R.string.setup_grant)) {
                        PermissionUtils.requestIgnoreBatteryOptimizations(context)
                    }
                    StatusRow(stringResource(R.string.setup_secure), setup.secureSettings, null) {}
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(stringResource(R.string.setup_grant_all_root), enabled = root == true) {
                        vm.grantAllViaRoot()
                    }
                }
                3 -> StepFrame(Icons.Filled.Home, R.string.setup_step_default_title, R.string.setup_step_default_body) {
                    StatusRow(stringResource(R.string.setup_default_launcher), setup.defaultLauncher, null) {}
                    if (!setup.defaultLauncher) {
                        Spacer(Modifier.height(12.dp))
                        PrimaryButton(stringResource(R.string.setup_set_settings), enabled = true) {
                            PermissionUtils.openDefaultLauncherSettings(context)
                        }
                        Spacer(Modifier.height(8.dp))
                        PrimaryButton(stringResource(R.string.setup_set_root), enabled = root == true) {
                            vm.setDefaultViaRoot()
                        }
                    }
                }
                4 -> StepFrame(Icons.Filled.Memory, R.string.setup_step_system_title, R.string.setup_step_system_body) {
                    StatusRow(stringResource(R.string.setup_system_app), setup.systemApp, null) {}
                    if (!setup.systemApp) {
                        Spacer(Modifier.height(12.dp))
                        when (install.phase) {
                            LauncherViewModel.InstallPhase.COPIED -> {
                                Text(stringResource(R.string.setup_system_copied), color = CloudSuccess, style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(12.dp))
                                PrimaryButton(stringResource(R.string.setup_reboot), enabled = true) {
                                    vm.rebootToApplySystem()
                                }
                            }
                            LauncherViewModel.InstallPhase.RUNNING -> {
                                Text(stringResource(R.string.setup_installing), color = CloudGray, style = MaterialTheme.typography.bodyMedium)
                            }
                            else -> {
                                if (install.phase == LauncherViewModel.InstallPhase.FAILED) {
                                    Text(stringResource(R.string.setup_system_failed), color = CloudDanger, style = MaterialTheme.typography.bodyMedium)
                                    Spacer(Modifier.height(12.dp))
                                }
                                PrimaryButton(stringResource(R.string.setup_install_system), enabled = root == true) {
                                    vm.installAsSystem()
                                }
                            }
                        }
                    }
                }
                else -> StepFrame(Icons.Filled.CheckCircle, R.string.setup_done_title, R.string.setup_done_body) {}
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (step > 0) {
                TextButton(onClick = { step -= 1 }) {
                    Text(stringResource(R.string.setup_back), color = CloudGray)
                }
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { if (step < last) step += 1 else onFinish() },
                colors = ButtonDefaults.buttonColors(containerColor = CloudAccent, contentColor = CloudBlack)
            ) {
                Text(stringResource(if (step < last) R.string.setup_next else R.string.setup_finish))
            }
        }
    }
}

@Composable
private fun StepFrame(icon: ImageVector, title: Int, body: Int, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .liquidGlass(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = CloudAccent, modifier = Modifier.size(42.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(title),
            color = CloudWhite,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(body),
            color = CloudGray,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(20.dp))
        Column(modifier = Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun StatusRow(title: String, granted: Boolean, actionLabel: String?, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (granted) CloudSuccess else CloudDanger
        )
        Spacer(Modifier.width(12.dp))
        Text(title, color = CloudWhite, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (granted) {
            Text(stringResource(R.string.setup_granted), color = CloudSuccess, style = MaterialTheme.typography.labelSmall)
        } else if (actionLabel != null) {
            TextButton(onClick = onAction) { Text(actionLabel, color = CloudAccent) }
        }
    }
}

@Composable
private fun PrimaryButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = CloudAccent.copy(alpha = 0.18f),
            contentColor = CloudAccent,
            disabledContainerColor = CloudGray.copy(alpha = 0.12f),
            disabledContentColor = CloudGray.copy(alpha = 0.5f)
        )
    ) {
        Text(text)
    }
}
