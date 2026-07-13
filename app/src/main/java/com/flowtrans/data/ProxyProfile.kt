package com.flowtrans.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A saved upstream forwarding target (Postern-style profile). The full list doubles
 * as history; [lastUsedAt] drives "recently used" ordering.
 */
@Entity(tableName = "profiles")
data class ProxyProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val protocol: Protocol,
    val host: String,
    val port: Int,
    val username: String? = null,
    val password: String? = null,
    /** HTTPS/SOCKS5 over TLS. For [Protocol.HTTPS] this is implied true. */
    val tls: Boolean = false,
    val sni: String? = null,
    val skipCertVerify: Boolean = true,
    /** Only meaningful for SOCKS5. */
    val udp: Boolean = true,
    val createdAt: Long = 0L,
    val lastUsedAt: Long = 0L,
) {
    val displayEndpoint: String get() = "$host:$port"
}
