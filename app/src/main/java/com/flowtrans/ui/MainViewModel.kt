package com.flowtrans.ui

import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flowtrans.data.AppDatabase
import com.flowtrans.data.DnsMode
import com.flowtrans.data.Protocol
import com.flowtrans.data.ProxyProfile
import com.flowtrans.data.RoutingMode
import com.flowtrans.data.SettingsStore
import com.flowtrans.data.TunStack
import com.flowtrans.root.CaInstaller
import com.flowtrans.vpn.VpnController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiSettings(
    val activeProfileId: Long = -1,
    val routingMode: RoutingMode = RoutingMode.GLOBAL,
    val selectedPackages: Set<String> = emptySet(),
    val dnsMode: DnsMode = DnsMode.REDIR_HOST,
    val tunStack: TunStack = TunStack.GVISOR,
)

data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val dao = db.profileDao()
    private val settings = SettingsStore(app)

    val profiles: StateFlow<List<ProxyProfile>> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vpnState = VpnController.state

    private val _uiSettings = MutableStateFlow(readSettings())
    val uiSettings: StateFlow<UiSettings> = _uiSettings

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps

    private val _caStatus = MutableStateFlow<CaInstaller.Status?>(null)
    val caStatus: StateFlow<CaInstaller.Status?> = _caStatus

    fun refreshCaStatus() {
        viewModelScope.launch { _caStatus.value = withContext(Dispatchers.IO) { CaInstaller.status() } }
    }

    fun refreshMoveCertificate(onDone: (String) -> Unit) {
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { CaInstaller.refreshMoveCertificate() }
            refreshCaStatus()
            onDone(r.out)
        }
    }

    fun reboot() {
        viewModelScope.launch { withContext(Dispatchers.IO) { CaInstaller.reboot() } }
    }

    private fun readSettings() = UiSettings(
        activeProfileId = settings.activeProfileId,
        routingMode = settings.routingMode,
        selectedPackages = settings.selectedPackages,
        dnsMode = settings.dnsMode,
        tunStack = settings.tunStack,
    )

    fun saveProfile(profile: ProxyProfile) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val id = dao.insert(
                profile.copy(createdAt = if (profile.createdAt == 0L) now else profile.createdAt)
            )
            // Auto-select the first profile created.
            if (settings.activeProfileId <= 0) setActiveProfile(id)
        }
    }

    fun deleteProfile(profile: ProxyProfile) {
        viewModelScope.launch { dao.delete(profile) }
    }

    fun setActiveProfile(id: Long) {
        settings.activeProfileId = id
        _uiSettings.value = _uiSettings.value.copy(activeProfileId = id)
    }

    fun setRoutingMode(mode: RoutingMode) {
        settings.routingMode = mode
        _uiSettings.value = _uiSettings.value.copy(routingMode = mode)
    }

    fun setSelectedPackages(pkgs: Set<String>) {
        settings.selectedPackages = pkgs
        _uiSettings.value = _uiSettings.value.copy(selectedPackages = pkgs)
    }

    fun setDnsMode(mode: DnsMode) {
        settings.dnsMode = mode
        _uiSettings.value = _uiSettings.value.copy(dnsMode = mode)
    }

    fun setTunStack(stack: TunStack) {
        settings.tunStack = stack
        _uiSettings.value = _uiSettings.value.copy(tunStack = stack)
    }

    fun loadInstalledApps() {
        if (_installedApps.value.isNotEmpty()) return
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) {
                val pm = getApplication<Application>().packageManager
                pm.getInstalledApplications(0)
                    .map {
                        InstalledApp(
                            packageName = it.packageName,
                            label = pm.getApplicationLabel(it).toString(),
                            isSystem = (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                        )
                    }
                    .sortedBy { it.label.lowercase() }
            }
            _installedApps.value = apps
        }
    }

    private val iconCache = HashMap<String, ImageBitmap?>()

    /** Lazily load + cache an app launcher icon (called per-row from the UI). */
    suspend fun loadIcon(pkg: String): ImageBitmap? {
        iconCache[pkg]?.let { return it }
        if (iconCache.containsKey(pkg)) return null
        val bmp = withContext(Dispatchers.IO) {
            runCatching {
                getApplication<Application>().packageManager
                    .getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap()
            }.getOrNull()
        }
        iconCache[pkg] = bmp
        return bmp
    }

    fun activeProfile(): ProxyProfile? =
        profiles.value.firstOrNull { it.id == _uiSettings.value.activeProfileId }

    companion object {
        fun blankProfile() = ProxyProfile(
            name = "", protocol = Protocol.HTTP, host = "", port = 8080,
        )
    }
}
