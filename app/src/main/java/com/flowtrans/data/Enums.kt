package com.flowtrans.data

/** Upstream proxy protocol for the forwarding target (e.g. a mitmproxy instance). */
enum class Protocol(val label: String) {
    HTTP("HTTP"),
    HTTPS("HTTPS (HTTP over TLS)"),
    SOCKS5("SOCKS5");
}

/** How device traffic is routed into the tunnel. */
enum class RoutingMode {
    /** Everything goes through the upstream (MATCH,PROXY + all apps tunneled). */
    GLOBAL,

    /** Only the selected apps are tunneled (VpnService allow-list). */
    PER_APP,
}

/** mihomo DNS enhanced-mode. redir-host keeps real hostnames (best for mitmproxy). */
enum class DnsMode(val yaml: String) {
    REDIR_HOST("redir-host"),
    FAKE_IP("fake-ip"),
}

/** mihomo TUN network stack. */
enum class TunStack(val value: String) {
    GVISOR("gvisor"),
    SYSTEM("system"),
    MIXED("mixed"),
}
