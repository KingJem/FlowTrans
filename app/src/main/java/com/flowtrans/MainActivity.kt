package com.flowtrans

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import android.widget.Toast
import com.flowtrans.root.CaInstaller
import com.flowtrans.ui.FlowApp
import com.flowtrans.ui.FlowTransTheme
import com.flowtrans.ui.MainViewModel
import com.flowtrans.vpn.FlowVpnService
import com.flowtrans.vpn.VpnController

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val vpnConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) VpnController.start(this)
    }

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* best-effort */ }

    private val pickCaFile =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@registerForActivityResult
            runCatching {
                val bytes = contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                val cert = CaInstaller.parseCert(bytes)
                startActivity(CaInstaller.installIntent(cert))
            }.onFailure {
                Toast.makeText(this, "Import failed: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Warm up the vendored mihomo core off the UI thread guarantee (it's fast).
        FlowVpnService.preload()

        setContent {
            FlowTransTheme {
                FlowApp(
                    vm = vm,
                    onStartVpn = ::requestStart,
                    onStopVpn = { VpnController.stop(this) },
                    onImportCa = { pickCaFile.launch("*/*") },
                )
            }
        }
    }

    private fun requestStart() {
        ensureNotificationPermission()
        val prepare = VpnService.prepare(this)
        if (prepare != null) vpnConsent.launch(prepare) else VpnController.start(this)
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
