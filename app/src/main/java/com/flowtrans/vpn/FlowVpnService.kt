package com.flowtrans.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.flowtrans.MainActivity
import com.flowtrans.core.ConfigBuilder
import com.flowtrans.data.AppDatabase
import com.flowtrans.data.SettingsStore
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.bridge.Bridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetSocketAddress

class FlowVpnService : VpnService() {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val connectivity by lazy { getSystemService<ConnectivityManager>()!! }
    private var fd: Int = -1
    private var trafficJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTunnel("Stopped by user")
                return START_NOT_STICKY
            }
            else -> startTunnel()
        }
        return START_STICKY
    }

    private fun startTunnel() {
        if (VpnController.state.value.status == VpnStatus.RUNNING) return
        startForegroundInternal("Starting…")
        VpnController.update { it.copy(status = VpnStatus.STARTING, message = null) }

        scope.launch {
            try {
                val settings = SettingsStore(this@FlowVpnService)
                val dao = AppDatabase.get(this@FlowVpnService).profileDao()
                val profile = dao.getById(settings.activeProfileId)
                    ?: error("No active profile selected")

                // 1) Generate + write config into the core home dir (filesDir/clash).
                val yaml = ConfigBuilder.build(
                    profile = profile,
                    routingMode = settings.routingMode,
                    dnsMode = settings.dnsMode,
                )
                val home = File(filesDir, "clash").apply { mkdirs() }
                File(home, "config.yaml").writeText(yaml)
                android.util.Log.i("FlowTrans", "config written; establishing TUN")

                // 2) Establish the TUN interface and hand its fd to the core FIRST
                // (CMFA establishes the tunnel and loads config concurrently, not
                // sequentially — awaiting load before establish can deadlock).
                establishAndAttach(settings, profile.name)
                android.util.Log.i("FlowTrans", "TUN attached; loading config")

                // 3) Load config into the core. Clash.load takes the HOME DIRECTORY
                // (mihomo reads config.yaml inside it), not the file path.
                Clash.load(home)
                android.util.Log.i("FlowTrans", "config load requested")

                dao.touch(profile.id, System.currentTimeMillis())
                VpnController.update {
                    it.copy(status = VpnStatus.RUNNING, message = null, activeProfileName = profile.name)
                }
                updateNotification("Forwarding via ${profile.name}")
                startTrafficPolling()
            } catch (t: Throwable) {
                android.util.Log.e("FlowTrans", "startTunnel failed", t)
                VpnController.update {
                    it.copy(status = VpnStatus.ERROR, message = t.message ?: t.toString())
                }
                stopTunnel(t.message ?: "error")
            }
        }
    }

    private fun establishAndAttach(settings: SettingsStore, profileName: String) {
        val builder = Builder().apply {
            addAddress(TUN_GATEWAY, TUN_PREFIX)
            addRoute("0.0.0.0", 0)
            addDnsServer(TUN_DNS)
            setMtu(TUN_MTU)
            setBlocking(false)
            setSession("FlowTrans · $profileName")

            when (settings.routingMode) {
                com.flowtrans.data.RoutingMode.GLOBAL -> {
                    // Everything except ourselves.
                    runCatching { addDisallowedApplication(packageName) }
                }
                com.flowtrans.data.RoutingMode.PER_APP -> {
                    val pkgs = settings.selectedPackages - packageName
                    if (pkgs.isEmpty()) error("Per-app mode is on but no apps are selected")
                    pkgs.forEach { runCatching { addAllowedApplication(it) } }
                }
            }
            if (Build.VERSION.SDK_INT >= 29) setMetered(false)
            setConfigureIntent(
                PendingIntent.getActivity(
                    this@FlowVpnService, 0,
                    Intent(this@FlowVpnService, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
        }

        android.util.Log.i("FlowTrans", "prepare()=${VpnService.prepare(this)}, establishing TUN…")
        val pfd = builder.establish()
        android.util.Log.i("FlowTrans", "establish() returned: $pfd")
        fd = pfd?.detachFd()
            ?: error("VPN permission not granted / establish() rejected")
        android.util.Log.i("FlowTrans", "TUN fd=$fd, calling startTun")

        Clash.startTun(
            fd = fd,
            stack = settings.tunStack.value,
            gateway = "$TUN_GATEWAY/$TUN_PREFIX",
            portal = TUN_PORTAL,
            dns = "0.0.0.0", // hijack all DNS into the core resolver
            markSocket = ::protect,
            querySocketUid = ::queryUid,
        )
    }

    private fun queryUid(protocol: Int, source: InetSocketAddress, target: InetSocketAddress): Int {
        if (Build.VERSION.SDK_INT < 29) return -1
        return runCatching { connectivity.getConnectionOwnerUid(protocol, source, target) }
            .getOrElse { -1 }
    }

    private fun startTrafficPolling() {
        trafficJob?.cancel()
        trafficJob = scope.launch {
            while (isActive) {
                val total = runCatching { Clash.queryTrafficTotal() }.getOrDefault(0L)
                VpnController.update { it.copy(trafficRaw = total) }
                delay(1000)
            }
        }
    }

    private fun stopTunnel(reason: String) {
        VpnController.update { it.copy(status = VpnStatus.STOPPING, message = reason) }
        trafficJob?.cancel()
        runCatching { Clash.stopTun() }
        runCatching { Clash.stopHttp() }
        VpnController.update { VpnState(status = VpnStatus.STOPPED, message = reason) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // --- foreground notification ---
    private fun startForegroundInternal(text: String) {
        val nm = getSystemService<NotificationManager>()!!
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "FlowTrans VPN", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = buildNotification(text)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun updateNotification(text: String) {
        getSystemService<NotificationManager>()!!.notify(NOTIF_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("FlowTrans")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .build()

    override fun onDestroy() {
        runCatching { Clash.stopTun() }
        scope.cancel()
        VpnController.update { VpnState(status = VpnStatus.STOPPED) }
        super.onDestroy()
    }

    override fun onRevoke() {
        stopTunnel("VPN revoked by system")
        super.onRevoke()
    }

    companion object {
        const val ACTION_START = "com.flowtrans.action.START"
        const val ACTION_STOP = "com.flowtrans.action.STOP"

        private const val CHANNEL = "flowtrans_vpn"
        private const val NOTIF_ID = 1001

        private const val TUN_MTU = 9000
        private const val TUN_GATEWAY = "172.19.0.1"
        private const val TUN_PREFIX = 30
        private const val TUN_PORTAL = "172.19.0.2"
        private const val TUN_DNS = TUN_PORTAL

        /** Force the vendored bridge to initialize (loadLibrary + nativeInit) early. */
        fun preload() { runCatching { Bridge.nativeCoreVersion() } }
    }
}
