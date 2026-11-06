package com.cloud.launcher

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cloud.launcher.ui.LauncherViewModel
import com.cloud.launcher.ui.components.SetupWizard
import com.cloud.launcher.ui.screens.AppDrawerScreen
import com.cloud.launcher.ui.screens.CustomRecentsScreen
import com.cloud.launcher.ui.screens.HiddenAppsScreen
import com.cloud.launcher.ui.screens.HomeScreen
import com.cloud.launcher.ui.screens.LauncherSettingsScreen
import com.cloud.launcher.ui.screens.ProtectedAppsScreen
import com.cloud.launcher.ui.theme.CloudBlack
import com.cloud.launcher.ui.theme.CloudLauncherTheme
import com.cloud.launcher.utils.PermissionUtils
import com.cloud.launcher.utils.RecentsIntent

object Routes {
    const val HOME = "home"
    const val DRAWER = "drawer"
    const val RECENTS = "recents"
    const val PROTECTED = "protected"
    const val SETTINGS = "settings"
    const val HIDDEN = "hidden"
}

class MainActivity : ComponentActivity() {

    private val vm: LauncherViewModel by viewModels()
    private val homeSignal = mutableIntStateOf(0)
    private val recentsSignal = mutableIntStateOf(0)
    private val recentsFromApp = mutableStateOf(false)
    private var stopped = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (intent?.getBooleanExtra(RecentsIntent.EXTRA_OPEN_RECENTS, false) == true) {
            recentsFromApp.value = true
            recentsSignal.intValue = 1
        }
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            CloudLauncherTheme(settings = settings) {
                LauncherRoot(
                    vm = vm,
                    homeSignal = homeSignal.intValue,
                    recentsSignal = recentsSignal.intValue,
                    recentsFromApp = recentsFromApp.value
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        stopped = false
    }

    override fun onStop() {
        stopped = true
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(RecentsIntent.EXTRA_OPEN_RECENTS, false)) {
            recentsFromApp.value = stopped
            recentsSignal.intValue = recentsSignal.intValue + 1
        } else if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            homeSignal.intValue = homeSignal.intValue + 1
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshPermissions()
        vm.syncRecents()
    }
}

@Composable
fun LauncherRoot(vm: LauncherViewModel, homeSignal: Int, recentsSignal: Int, recentsFromApp: Boolean) {
    val context = LocalContext.current
    val nav = rememberNavController()
    var recentsExternal by remember { mutableStateOf(false) }

    fun closeRecents() {
        if (recentsExternal) {
            recentsExternal = false
            nav.popBackStack(Routes.HOME, false)
            (context as? ComponentActivity)?.moveTaskToBack(true)
        } else {
            nav.popBackStack()
        }
    }
    val showSetup by vm.showSetup.collectAsStateWithLifecycle()

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { vm.refreshPermissions() }

    LaunchedEffect(homeSignal) {
        if (homeSignal > 0) nav.popBackStack(Routes.HOME, false)
    }

    LaunchedEffect(recentsSignal) {
        if (recentsSignal > 0) {
            val route = nav.currentBackStackEntry?.destination?.route
            if (!recentsFromApp && route == Routes.RECENTS) {
                closeRecents()
            } else {
                recentsExternal = recentsFromApp
                nav.popBackStack(Routes.HOME, false)
                nav.navigate(Routes.RECENTS) { launchSingleTop = true }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(CloudBlack)) {
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            enterTransition = { fadeIn(tween(90)) },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { fadeOut(tween(70)) }
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    vm = vm,
                    onOpenDrawer = { nav.navigate(Routes.DRAWER) { launchSingleTop = true } },
                    onOpenRecents = { nav.navigate(Routes.RECENTS) { launchSingleTop = true } },
                    onOpenProtected = { nav.navigate(Routes.PROTECTED) { launchSingleTop = true } },
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                    onOpenHidden = { nav.navigate(Routes.HIDDEN) { launchSingleTop = true } }
                )
            }
            composable(Routes.DRAWER) {
                AppDrawerScreen(vm = vm, onBack = { nav.popBackStack() })
            }
            composable(Routes.RECENTS) {
                BackHandler { closeRecents() }
                DisposableEffect(Unit) { onDispose { recentsExternal = false } }
                CustomRecentsScreen(vm = vm, onBack = { closeRecents() })
            }
            composable(Routes.PROTECTED) {
                ProtectedAppsScreen(vm = vm, onBack = { nav.popBackStack() })
            }
            composable(Routes.HIDDEN) {
                HiddenAppsScreen(vm = vm, onBack = { nav.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                LauncherSettingsScreen(vm = vm, onBack = { nav.popBackStack() })
            }
        }
    }

    if (showSetup) {
        SetupWizard(
            vm = vm,
            onRequestNotifications = {
                if (Build.VERSION.SDK_INT >= 33) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    PermissionUtils.openNotificationSettings(context)
                }
            },
            onFinish = { vm.finishSetup() }
        )
    }
}
