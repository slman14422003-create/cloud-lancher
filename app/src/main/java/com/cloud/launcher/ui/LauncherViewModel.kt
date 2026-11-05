package com.cloud.launcher.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cloud.launcher.R
import com.cloud.launcher.data.AppInfo
import com.cloud.launcher.data.AppRepository
import com.cloud.launcher.data.LauncherSettings
import com.cloud.launcher.ui.theme.ThemeState
import com.cloud.launcher.data.ProtectedAppsRepository
import com.cloud.launcher.data.RecentAppsRepository
import com.cloud.launcher.root.OomController
import com.cloud.launcher.root.ProcessManager
import com.cloud.launcher.root.RootManager
import com.cloud.launcher.root.RunningApp
import com.cloud.launcher.root.SystemInstaller
import com.cloud.launcher.service.ProtectionService
import com.cloud.launcher.ui.components.IconCache
import com.cloud.launcher.utils.PermissionUtils
import com.cloud.launcher.utils.PrefsManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val MAX_FAVORITES = 5
        const val MIN_FAVORITES = 3
        const val MAX_HOME = 16
    }

    data class SetupState(
        val usageAccess: Boolean = false,
        val accessibility: Boolean = false,
        val notifications: Boolean = false,
        val overlay: Boolean = false,
        val battery: Boolean = false,
        val secureSettings: Boolean = false,
        val defaultLauncher: Boolean = false,
        val systemApp: Boolean = false,
        val systemUpdated: Boolean = false
    )

    data class UiMessage(
        val text: String,
        val actionLabel: String? = null,
        val action: (() -> Unit)? = null
    )

    enum class InstallPhase { IDLE, RUNNING, COPIED, FAILED }

    data class SystemInstallState(val phase: InstallPhase = InstallPhase.IDLE, val log: String = "")

    private val ctx: Context = application.applicationContext
    private val prefs = PrefsManager(ctx)
    private val appRepo = AppRepository(ctx)
    private val protectedRepo = ProtectedAppsRepository(prefs)
    private val recentRepo = RecentAppsRepository(prefs)

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    val appMap: StateFlow<Map<String, AppInfo>> = _apps
        .map { list -> list.associateBy { it.packageName } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val favorites: StateFlow<List<String>> =
        prefs.favoriteApps.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val homeApps: StateFlow<List<String>> =
        prefs.homeApps.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val protectedPkgs: StateFlow<List<String>> =
        protectedRepo.protectedApps.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val hiddenPkgs: StateFlow<List<String>> =
        prefs.hiddenApps.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val rawRecents: StateFlow<List<String>> =
        recentRepo.recents.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val recentPkgs: StateFlow<List<String>> =
        combine(rawRecents, hiddenPkgs) { list, hidden -> list.filter { it !in hidden } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val settings: StateFlow<LauncherSettings> =
        prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, LauncherSettings())

    fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) {
        viewModelScope.launch { prefs.updateSettings(transform) }
    }

    fun resetSettings() {
        viewModelScope.launch { prefs.updateSettings { LauncherSettings() } }
    }

    val sortByUsage: StateFlow<Boolean> =
        prefs.sortByUsage.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val launchCounts: StateFlow<Map<String, Long>> =
        prefs.launchCounts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val homeHintShown: StateFlow<Boolean?> =
        prefs.homeHintShown.map<Boolean, Boolean?> { it }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun markHomeHintShown() {
        viewModelScope.launch { prefs.setHomeHintShown(true) }
    }

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val drawerApps: StateFlow<List<AppInfo>> = combine(_apps, _query, sortByUsage, launchCounts, hiddenPkgs) { all, q, byUsage, counts, hidden ->
        val list = all.filter { it.packageName !in hidden }
        val text = q.trim()
        val filtered = if (text.isEmpty()) list else list.filter { it.label.contains(text, ignoreCase = true) }
        if (byUsage) {
            filtered.sortedWith(
                compareByDescending<AppInfo> { counts[it.packageName] ?: 0L }.thenBy { it.label.lowercase() }
            )
        } else {
            filtered
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _rootAvailable = MutableStateFlow<Boolean?>(null)
    val rootAvailable: StateFlow<Boolean?> = _rootAvailable.asStateFlow()

    private val _running = MutableStateFlow<Map<String, RunningApp>>(emptyMap())
    val running: StateFlow<Map<String, RunningApp>> = _running.asStateFlow()

    private val _setup = MutableStateFlow(SetupState())
    val setup: StateFlow<SetupState> = _setup.asStateFlow()

    private val _systemInstall = MutableStateFlow(SystemInstallState())
    val systemInstall: StateFlow<SystemInstallState> = _systemInstall.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    private val _closing = MutableStateFlow<Set<String>>(emptySet())
    val closing: StateFlow<Set<String>> = _closing.asStateFlow()

    private val _killingAll = MutableStateFlow(false)
    val killingAll: StateFlow<Boolean> = _killingAll.asStateFlow()

    private val _showSetup = MutableStateFlow(false)
    val showSetup: StateFlow<Boolean> = _showSetup.asStateFlow()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshApps()
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(ctx, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        viewModelScope.launch {
            settings.collect { s ->
                ThemeState.apply(s)
                if (IconCache.setShape(s.iconShape)) refreshApps()
            }
        }

        refreshApps()
        viewModelScope.launch {
            val shown = prefs.setupShown.first()
            if (!shown) {
                refreshPermissions()
                _showSetup.value = true
            } else {
                _rootAvailable.value = RootManager.isRootAvailable()
                if (_rootAvailable.value == true) {
                    RootManager.enableAccessibility(ctx)
                    refreshPermissions()
                    if (protectedRepo.current().isNotEmpty()) ProtectionService.start(ctx)
                    optimizeIfNeeded()
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun optimizeIfNeeded() {
        val current = try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionCode
        } catch (e: Exception) {
            return
        }
        if (prefs.compiledVersion.first() == current) return
        kotlinx.coroutines.delay(20_000L)
        if (RootManager.compileForSpeed(ctx)) prefs.setCompiledVersion(current)
    }

    override fun onCleared() {
        try {
            ctx.unregisterReceiver(packageReceiver)
        } catch (e: Exception) {
        }
        super.onCleared()
    }

    private fun toast(message: String) {
        Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
    }

    private fun str(id: Int): String = ctx.getString(id)


    fun refreshApps() {
        viewModelScope.launch {
            IconCache.clear()
            val list = appRepo.loadApps()
            _apps.value = list
            val first = (favorites.value + homeApps.value).toSet()
            val ordered = list.sortedByDescending { it.packageName in first }
            withContext(Dispatchers.Default) {
                for (app in ordered) {
                    IconCache.load(ctx, app.packageName)
                    yield()
                }
            }
        }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setSortByUsage(value: Boolean) {
        viewModelScope.launch { prefs.setSortByUsage(value) }
    }

    fun launchApp(packageName: String) {
        if (!appRepo.launch(packageName)) {
            toast(str(R.string.toast_launch_failed))
            return
        }
        viewModelScope.launch {
            prefs.incrementLaunch(packageName)
            recentRepo.record(packageName)
        }
    }

    fun openAppInfo(packageName: String) = appRepo.openAppInfo(packageName)

    fun uninstall(packageName: String) = appRepo.uninstall(packageName)


    private fun favoritesLimit(): Int = settings.value.favoritesCount.coerceIn(MIN_FAVORITES, MAX_FAVORITES)

    fun toggleFavorite(packageName: String) {
        viewModelScope.launch {
            val current = favorites.value
            when {
                packageName in current -> prefs.setFavorites(current - packageName)
                current.size >= favoritesLimit() -> toast(ctx.getString(R.string.toast_home_limit, favoritesLimit()))
                else -> prefs.setFavorites(current + packageName)
            }
        }
    }

    fun toggleHome(packageName: String) {
        viewModelScope.launch {
            val current = homeApps.value
            when {
                packageName in current -> prefs.setHomeApps(current - packageName)
                current.size >= MAX_HOME -> toast(str(R.string.toast_grid_limit))
                else -> prefs.setHomeApps(current + packageName)
            }
        }
    }

    fun toggleHidden(packageName: String) {
        viewModelScope.launch {
            val current = hiddenPkgs.value
            if (packageName in current) {
                prefs.setHidden(current - packageName)
            } else {
                prefs.setHidden(current + packageName)
                if (packageName in homeApps.value) prefs.setHomeApps(homeApps.value - packageName)
                if (packageName in favorites.value) prefs.setFavorites(favorites.value - packageName)
            }
        }
    }

    fun setHomeOrder(order: List<String>) {
        viewModelScope.launch { prefs.setHomeApps(order) }
    }

    fun setFavoritesOrder(order: List<String>) {
        viewModelScope.launch { prefs.setFavorites(order.take(favoritesLimit()) + favorites.value.drop(favoritesLimit()).filter { it !in order }) }
    }


    fun addToRecents(packageName: String) {
        viewModelScope.launch { recentRepo.record(packageName) }
    }

    fun removeFromRecents(packageName: String) {
        viewModelScope.launch { recentRepo.remove(packageName) }
    }

    fun syncRecents() {
        viewModelScope.launch { recentRepo.syncFromUsageStats(ctx) }
    }


    fun requestRoot() {
        viewModelScope.launch {
            val ok = RootManager.requestRoot()
            _rootAvailable.value = ok
            if (ok) {
                RootManager.enableAccessibility(ctx)
                refreshPermissions()
            }
            if (ok && protectedRepo.current().isNotEmpty()) ProtectionService.start(ctx)
        }
    }

    suspend fun refreshRunning() {
        if (!RootManager.isGranted) return
        val installed = _apps.value.map { it.packageName }.toSet()
        _running.value = ProcessManager.getRunningApps(installed)
            .map { it.copy(rssKb = it.rssKb / 4096L * 4096L) }
            .associateBy { it.packageName }
    }

    private fun postMessage(message: UiMessage) {
        if (_messages.subscriptionCount.value > 0) _messages.tryEmit(message) else toast(message.text)
    }

    private fun labelOf(packageName: String): String = appMap.value[packageName]?.label ?: packageName

    fun forceStop(packageName: String) {
        viewModelScope.launch { closeApp(packageName) }
    }

    suspend fun closeApp(packageName: String): Boolean {
        if (packageName == ctx.packageName || packageName in _closing.value) return false
        val label = labelOf(packageName)
        if (packageName in protectedPkgs.value) {
            postMessage(
                UiMessage(
                    str(R.string.toast_cannot_close_protected),
                    str(R.string.action_unprotect_close)
                ) { unprotectAndClose(packageName) }
            )
            return false
        }
        _closing.value = _closing.value + packageName
        var ok: Boolean
        var freedKb = 0L
        if (RootManager.isGranted) {
            val result = ProcessManager.closePackage(packageName)
            ok = result.ok
            freedKb = result.freedKb
        } else {
            ProcessManager.killBackground(ctx, packageName)
            ok = true
        }
        _closing.value = _closing.value - packageName
        val freedMb = freedKb / 1024L
        when {
            !RootManager.isGranted ->
                postMessage(UiMessage(ctx.getString(R.string.msg_closed_noroot, label)))
            !ok ->
                postMessage(
                    UiMessage(
                        ctx.getString(R.string.msg_close_failed, label),
                        str(R.string.action_retry)
                    ) { forceStop(packageName) }
                )
            freedMb > 0L ->
                postMessage(UiMessage(ctx.getString(R.string.msg_closed_freed, label, freedMb)))
            else ->
                postMessage(UiMessage(ctx.getString(R.string.msg_closed, label)))
        }
        refreshRunning()
        return ok
    }

    private fun unprotectAndClose(packageName: String) {
        viewModelScope.launch {
            protectedRepo.remove(packageName)
            if (RootManager.isGranted) OomController.unprotectApp(packageName)
            if (closeApp(packageName)) recentRepo.remove(packageName)
        }
    }

    fun killAll() {
        viewModelScope.launch {
            if (_killingAll.value) return@launch
            if (!RootManager.isGranted) {
                postMessage(UiMessage(str(R.string.root_required)))
                return@launch
            }
            val protectedSet = protectedPkgs.value.toSet()
            val targets = rawRecents.value.filter { it !in protectedSet && it != ctx.packageName }
            if (targets.isEmpty()) {
                postMessage(UiMessage(str(R.string.recents_nothing_to_close)))
                return@launch
            }
            _killingAll.value = true
            val result = ProcessManager.killAll(targets)
            recentRepo.removeAll(result.closed)
            _killingAll.value = false
            postMessage(UiMessage(ctx.getString(R.string.recents_killed_freed, result.closed.size, result.freedKb / 1024L)))
            refreshRunning()
        }
    }

    fun removeFromRecentsNow(packageName: String) {
        viewModelScope.launch { recentRepo.remove(packageName) }
    }

    fun openSystemSettings() {
        PermissionUtils.openSystemSettings(ctx)
    }

    fun toggleProtect(packageName: String) {
        viewModelScope.launch {
            if (packageName in protectedPkgs.value) {
                protectedRepo.remove(packageName)
                if (RootManager.isGranted) OomController.unprotectApp(packageName)
            } else {
                when (protectedRepo.add(packageName)) {
                    ProtectedAppsRepository.AddResult.LIMIT_REACHED ->
                        toast(str(R.string.toast_protected_limit))
                    ProtectedAppsRepository.AddResult.ADDED -> {
                        if (RootManager.isGranted) {
                            OomController.protectApp(packageName)
                        } else {
                            toast(str(R.string.toast_no_root_protect))
                        }
                        ProtectionService.start(ctx)
                    }
                    ProtectedAppsRepository.AddResult.ALREADY -> Unit
                }
            }
        }
    }

    fun reapplyProtection() {
        viewModelScope.launch {
            if (!RootManager.isGranted) {
                toast(str(R.string.root_required))
                return@launch
            }
            for (pkg in protectedRepo.current()) OomController.protectApp(pkg)
            ProtectionService.start(ctx)
            toast(str(R.string.toast_protection_applied))
            refreshRunning()
        }
    }


    fun refreshPermissions() {
        _setup.value = SetupState(
            usageAccess = PermissionUtils.hasUsageAccess(ctx),
            accessibility = PermissionUtils.isAccessibilityEnabled(ctx),
            notifications = NotificationManagerCompat.from(ctx).areNotificationsEnabled(),
            overlay = PermissionUtils.canDrawOverlays(ctx),
            battery = PermissionUtils.isIgnoringBatteryOptimizations(ctx),
            secureSettings = PermissionUtils.hasWriteSecureSettings(ctx),
            defaultLauncher = PermissionUtils.isDefaultLauncher(ctx),
            systemApp = PermissionUtils.isSystemApp(ctx),
            systemUpdated = PermissionUtils.isUpdatedSystemApp(ctx)
        )
    }

    fun grantAllViaRoot() {
        viewModelScope.launch {
            if (!RootManager.isGranted) {
                toast(str(R.string.root_required))
                return@launch
            }
            RootManager.grantAllPermissions(ctx)
            refreshPermissions()
            toast(str(R.string.toast_granted_all))
        }
    }

    fun setDefaultViaRoot() {
        viewModelScope.launch {
            if (!RootManager.isGranted) {
                toast(str(R.string.root_required))
                return@launch
            }
            val ok = RootManager.setDefaultLauncher(ctx)
            refreshPermissions()
            toast(str(if (ok) R.string.toast_default_set else R.string.toast_default_failed))
        }
    }

    fun installAsSystem() {
        viewModelScope.launch {
            if (!RootManager.isGranted) {
                toast(str(R.string.root_required))
                return@launch
            }
            _systemInstall.value = SystemInstallState(InstallPhase.RUNNING)
            val result = SystemInstaller.install(ctx)
            _systemInstall.value = SystemInstallState(
                if (result.ok) InstallPhase.COPIED else InstallPhase.FAILED,
                result.log
            )
        }
    }

    fun rebootToApplySystem() {
        viewModelScope.launch { SystemInstaller.removeUserCopyAndReboot(ctx) }
    }

    fun finishSetup() {
        _showSetup.value = false
        viewModelScope.launch {
            prefs.setSetupShown(true)
            if (_rootAvailable.value == null) _rootAvailable.value = RootManager.isRootAvailable()
        }
    }
}
