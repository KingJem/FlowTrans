package com.flowtrans.vpn

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class VpnStatus { STOPPED, STARTING, RUNNING, STOPPING, ERROR }

data class VpnState(
    val status: VpnStatus = VpnStatus.STOPPED,
    val message: String? = null,
    val activeProfileName: String? = null,
    /** Raw packed value from Clash.queryTrafficTotal(); format with core Traffic extensions. */
    val trafficRaw: Long = 0,
)

/** Process-wide VPN state, observed by the UI and updated by [FlowVpnService]. */
object VpnController {
    private val _state = MutableStateFlow(VpnState())
    val state: StateFlow<VpnState> = _state

    internal fun update(block: (VpnState) -> VpnState) {
        _state.value = block(_state.value)
    }

    fun start(context: Context) {
        val intent = Intent(context, FlowVpnService::class.java).setAction(FlowVpnService.ACTION_START)
        context.startForegroundService(intent)
    }

    fun stop(context: Context) {
        val intent = Intent(context, FlowVpnService::class.java).setAction(FlowVpnService.ACTION_STOP)
        context.startService(intent)
    }
}
