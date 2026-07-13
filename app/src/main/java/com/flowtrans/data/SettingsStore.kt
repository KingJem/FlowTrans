package com.flowtrans.data

import android.content.Context
import android.content.SharedPreferences

/** Non-profile settings: which profile is active, routing mode, per-app selection, DNS/stack. */
class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("flowtrans_settings", Context.MODE_PRIVATE)

    var activeProfileId: Long
        get() = prefs.getLong(KEY_ACTIVE, -1L)
        set(v) = prefs.edit().putLong(KEY_ACTIVE, v).apply()

    var routingMode: RoutingMode
        get() = RoutingMode.valueOf(prefs.getString(KEY_MODE, RoutingMode.GLOBAL.name)!!)
        set(v) = prefs.edit().putString(KEY_MODE, v.name).apply()

    /** Packages selected for PER_APP mode (allow-list). */
    var selectedPackages: Set<String>
        get() = prefs.getStringSet(KEY_APPS, emptySet())!!.toSet()
        set(v) = prefs.edit().putStringSet(KEY_APPS, v).apply()

    var dnsMode: DnsMode
        get() = DnsMode.valueOf(prefs.getString(KEY_DNS, DnsMode.REDIR_HOST.name)!!)
        set(v) = prefs.edit().putString(KEY_DNS, v.name).apply()

    var tunStack: TunStack
        get() = TunStack.valueOf(prefs.getString(KEY_STACK, TunStack.GVISOR.name)!!)
        set(v) = prefs.edit().putString(KEY_STACK, v.name).apply()

    private companion object {
        const val KEY_ACTIVE = "active_profile_id"
        const val KEY_MODE = "routing_mode"
        const val KEY_APPS = "selected_packages"
        const val KEY_DNS = "dns_mode"
        const val KEY_STACK = "tun_stack"
    }
}
