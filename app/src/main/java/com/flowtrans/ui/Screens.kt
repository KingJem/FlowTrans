package com.flowtrans.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.flowtrans.data.DnsMode
import com.flowtrans.data.Protocol
import com.flowtrans.data.ProxyProfile
import com.flowtrans.data.RoutingMode
import com.flowtrans.data.TunStack
import com.flowtrans.vpn.VpnStatus
import com.github.kr328.clash.core.util.trafficDownload
import com.github.kr328.clash.core.util.trafficUpload

private sealed interface Screen {
    data object Home : Screen
    data object Profiles : Screen
    data class Edit(val profile: ProxyProfile?) : Screen
    data object Apps : Screen
    data object Certificate : Screen
}

@Composable
fun FlowApp(
    vm: MainViewModel,
    onStartVpn: () -> Unit,
    onStopVpn: () -> Unit,
    onImportCa: () -> Unit,
) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    // Intercept the system back gesture (right-edge swipe) on sub-screens so it
    // navigates back within the app instead of finishing the Activity to launcher.
    // Screen.Apps is excluded: it owns its own BackHandler so it can persist the
    // selection before leaving (same as its top-bar back button).
    BackHandler(enabled = screen != Screen.Home && screen != Screen.Apps) {
        screen = when (screen) {
            is Screen.Edit -> Screen.Profiles
            else -> Screen.Home
        }
    }
    when (val s = screen) {
        Screen.Home -> HomeScreen(vm, onStartVpn, onStopVpn,
            onManageProfiles = { screen = Screen.Profiles },
            onPickApps = { screen = Screen.Apps },
            onOpenCert = { screen = Screen.Certificate })
        Screen.Profiles -> ProfilesScreen(vm,
            onBack = { screen = Screen.Home },
            onAdd = { screen = Screen.Edit(null) },
            onEdit = { screen = Screen.Edit(it) })
        is Screen.Edit -> EditScreen(vm, s.profile, onDone = { screen = Screen.Profiles })
        Screen.Apps -> AppPickerScreen(vm, onBack = { screen = Screen.Home })
        Screen.Certificate -> CertificateScreen(vm, onImportCa, onBack = { screen = Screen.Home })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    vm: MainViewModel,
    onStartVpn: () -> Unit,
    onStopVpn: () -> Unit,
    onManageProfiles: () -> Unit,
    onPickApps: () -> Unit,
    onOpenCert: () -> Unit,
) {
    val state by vm.vpnState.collectAsState()
    val settings by vm.uiSettings.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val active = profiles.firstOrNull { it.id == settings.activeProfileId }

    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("FlowTrans") }) }) { inner ->
        Column(
            Modifier.padding(inner).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Status card
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Status: ${state.status}", style = MaterialTheme.typography.titleMedium)
                    state.message?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                    if (state.status == VpnStatus.RUNNING) {
                        Text("↑ ${state.trafficRaw.trafficUpload()}   ↓ ${state.trafficRaw.trafficDownload()}")
                    }
                }
            }

            // Active profile
            Card(Modifier.fillMaxWidth().clickable { onManageProfiles() }) {
                Column(Modifier.padding(16.dp)) {
                    Text("Upstream profile", style = MaterialTheme.typography.labelMedium)
                    if (active != null) {
                        Text(active.name, style = MaterialTheme.typography.titleMedium)
                        Text("${active.protocol.label} · ${active.displayEndpoint}",
                            style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text("No profile — tap to add one",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Routing mode
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Routing", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = settings.routingMode == RoutingMode.GLOBAL,
                            onClick = { vm.setRoutingMode(RoutingMode.GLOBAL) },
                            label = { Text("Global") })
                        FilterChip(
                            selected = settings.routingMode == RoutingMode.PER_APP,
                            onClick = { vm.setRoutingMode(RoutingMode.PER_APP) },
                            label = { Text("Per-app") })
                    }
                    if (settings.routingMode == RoutingMode.PER_APP) {
                        FilledTonalButton(onClick = onPickApps) {
                            Text("Select apps (${settings.selectedPackages.size})")
                        }
                    }
                }
            }

            AdvancedCard(vm)

            Card(Modifier.fillMaxWidth().clickable { onOpenCert() }) {
                Column(Modifier.padding(16.dp)) {
                    Text("HTTPS decryption (CA)", style = MaterialTheme.typography.labelMedium)
                    Text("Trust mitmproxy's CA so HTTPS can be decrypted",
                        style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(Modifier.height(4.dp))

            val running = state.status == VpnStatus.RUNNING || state.status == VpnStatus.STARTING
            Button(
                onClick = { if (running) onStopVpn() else onStartVpn() },
                enabled = active != null || running,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (running) "Stop" else "Start forwarding") }
        }
    }
}

@Composable
private fun AdvancedCard(vm: MainViewModel) {
    val settings by vm.uiSettings.collectAsState()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Advanced", style = MaterialTheme.typography.labelMedium)
            Text("DNS mode", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DnsMode.entries.forEach { m ->
                    FilterChip(selected = settings.dnsMode == m,
                        onClick = { vm.setDnsMode(m) }, label = { Text(m.yaml) })
                }
            }
            Text("TUN stack", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TunStack.entries.forEach { st ->
                    FilterChip(selected = settings.tunStack == st,
                        onClick = { vm.setTunStack(st) }, label = { Text(st.value) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfilesScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (ProxyProfile) -> Unit,
) {
    val profiles by vm.profiles.collectAsState()
    val settings by vm.uiSettings.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Profiles & history") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } })
        },
        floatingActionButton = { FloatingActionButton(onClick = onAdd) { Icon(Icons.Default.Add, null) } },
    ) { inner ->
        LazyColumn(Modifier.padding(inner).fillMaxSize()) {
            items(profiles, key = { it.id }) { p ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.setActiveProfile(p.id) }.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = p.id == settings.activeProfileId,
                        onClick = { vm.setActiveProfile(p.id) })
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium)
                        Text("${p.protocol.label} · ${p.displayEndpoint}",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { onEdit(p) }) { Icon(Icons.Default.Edit, null) }
                    IconButton(onClick = { vm.deleteProfile(p) }) { Icon(Icons.Default.Delete, null) }
                }
                Divider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditScreen(vm: MainViewModel, existing: ProxyProfile?, onDone: () -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var protocol by remember { mutableStateOf(existing?.protocol ?: Protocol.HTTP) }
    var host by remember { mutableStateOf(existing?.host ?: "") }
    var port by remember { mutableStateOf((existing?.port ?: 8080).toString()) }
    var username by remember { mutableStateOf(existing?.username ?: "") }
    var password by remember { mutableStateOf(existing?.password ?: "") }
    var tls by remember { mutableStateOf(existing?.tls ?: false) }
    var sni by remember { mutableStateOf(existing?.sni ?: "") }
    var skipVerify by remember { mutableStateOf(existing?.skipCertVerify ?: true) }

    Scaffold(topBar = {
        TopAppBar(title = { Text(if (existing == null) "New profile" else "Edit profile") },
            navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.Default.ArrowBack, null) } })
    }) { inner ->
        Column(
            Modifier.padding(inner).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") },
                modifier = Modifier.fillMaxWidth())
            Text("Protocol", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Protocol.entries.forEach { pr ->
                    FilterChip(selected = protocol == pr, onClick = { protocol = pr },
                        label = { Text(pr.name) })
                }
            }
            OutlinedTextField(host, { host = it }, label = { Text("Host (mitmproxy PC IP)") },
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(port, { port = it.filter(Char::isDigit) }, label = { Text("Port") },
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(username, { username = it }, label = { Text("Username (optional)") },
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(password, { password = it }, label = { Text("Password (optional)") },
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())

            if (protocol != Protocol.HTTPS) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = tls, onCheckedChange = { tls = it }); Spacer(Modifier.width(8.dp))
                    Text("TLS to upstream")
                }
            }
            if (protocol == Protocol.HTTPS || tls) {
                OutlinedTextField(sni, { sni = it }, label = { Text("SNI (optional)") },
                    modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = skipVerify, onCheckedChange = { skipVerify = it })
                    Spacer(Modifier.width(8.dp)); Text("Skip cert verify")
                }
            }

            Button(
                onClick = {
                    vm.saveProfile(
                        (existing ?: MainViewModel.blankProfile()).copy(
                            name = name.ifBlank { host },
                            protocol = protocol,
                            host = host.trim(),
                            port = port.toIntOrNull() ?: 8080,
                            username = username.ifBlank { null },
                            password = password.ifBlank { null },
                            tls = protocol == Protocol.HTTPS || tls,
                            sni = sni.ifBlank { null },
                            skipCertVerify = skipVerify,
                        )
                    )
                    onDone()
                },
                enabled = host.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CertificateScreen(vm: MainViewModel, onImportCa: () -> Unit, onBack: () -> Unit) {
    val status by vm.caStatus.collectAsState()
    var note by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.refreshCaStatus() }

    Scaffold(topBar = {
        TopAppBar(title = { Text("HTTPS decryption") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } })
    }) { inner ->
        Column(
            Modifier.padding(inner).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val s = status
                    if (s == null) {
                        Text("Checking…")
                    } else {
                        StatusLine("Root (su) available", s.rootAvailable)
                        StatusLine("MoveCertificate module", s.moveCertModule)
                        StatusLine("mitmproxy CA trusted system-wide", s.mitmTrustedSystemWide)
                    }
                }
            }

            Text(
                "To decrypt HTTPS, mitmproxy's CA must be trusted by the system store. " +
                    "Import the CA (from ~/.mitmproxy/mitmproxy-ca-cert.cer on your PC), then — on a " +
                    "rooted device with a MoveCertificate-style module — it is promoted to the system " +
                    "store automatically (reboot to apply). Without root, only this app could trust it.",
                style = MaterialTheme.typography.bodySmall,
            )

            Button(onClick = onImportCa, modifier = Modifier.fillMaxWidth()) {
                Text("Import CA certificate…")
            }
            FilledTonalButton(
                onClick = { vm.refreshMoveCertificate { note = "MoveCertificate: ${it.ifBlank { "done" }}" } },
                enabled = status?.rootAvailable == true,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Re-run MoveCertificate (no reboot)") }
            FilledTonalButton(
                onClick = { vm.reboot(); note = "Rebooting…" },
                enabled = status?.rootAvailable == true,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Reboot to apply") }

            note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun StatusLine(label: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (ok) "✓ " else "✗ ",
            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPickerScreen(vm: MainViewModel, onBack: () -> Unit) {
    val apps by vm.installedApps.collectAsState()
    val settings by vm.uiSettings.collectAsState()
    var selected by remember(settings.selectedPackages) { mutableStateOf(settings.selectedPackages) }
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableIntStateOf(0) } // 0 = third-party, 1 = system
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.loadInstalledApps() }
    // Persist selection then leave — shared by the top-bar button and the back gesture.
    val commitAndBack = { vm.setSelectedPackages(selected); onBack() }
    BackHandler { commitAndBack() }

    val q = query.trim().lowercase()
    fun matches(a: InstalledApp) =
        q.isEmpty() || a.label.lowercase().contains(q) || a.packageName.lowercase().contains(q)
    val thirdParty = apps.filter { !it.isSystem }
    val system = apps.filter { it.isSystem }
    // Selected apps float to the top; ordering is anchored to the persisted set so
    // rows don't jump around while you tap. It re-sorts next time the screen opens.
    val shown = (if (tab == 0) thirdParty else system)
        .filter(::matches)
        .sortedWith(
            compareBy({ it.packageName !in settings.selectedPackages }, { it.label.lowercase() })
        )

    Scaffold(topBar = {
        TopAppBar(title = { Text("Select apps (${selected.size})") },
            navigationIcon = {
                IconButton(onClick = { commitAndBack() }) {
                    Icon(Icons.Default.ArrowBack, null)
                }
            })
    }) { inner ->
        Column(Modifier.padding(inner).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search apps") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, null) }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            )
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 },
                    text = { Text("Third-party (${thirdParty.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 },
                    text = { Text("System (${system.size})") })
            }
            if (apps.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading apps…") }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(shown, key = { it.packageName }) { app ->
                        val checked = app.packageName in selected
                        Row(
                            Modifier.fillMaxWidth()
                                .background(
                                    if (checked) MaterialTheme.colorScheme.primaryContainer
                                    else Color.Transparent
                                )
                                .clickable {
                                    selected = if (checked) selected - app.packageName else selected + app.packageName
                                }.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            AppIcon(vm, app.packageName)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(
                                    app.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal,
                                    color = if (checked) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                )
                                Text(app.packageName, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            }
                            if (checked) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Divider()
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIcon(vm: MainViewModel, pkg: String) {
    var icon by remember(pkg) { mutableStateOf<ImageBitmap?>(null) }
    androidx.compose.runtime.LaunchedEffect(pkg) { icon = vm.loadIcon(pkg) }
    val ic = icon
    if (ic != null) {
        Image(bitmap = ic, contentDescription = null,
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)))
    } else {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant))
    }
}
