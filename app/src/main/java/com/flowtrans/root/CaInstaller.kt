package com.flowtrans.root

import android.security.KeyChain
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * Helpers for trusting the mitmproxy CA at the SYSTEM level so mitmproxy can decrypt HTTPS
 * from any app. On Android 14 the system store lives in the immutable Conscrypt APEX
 * (/apex/com.android.conscrypt/cacerts), so system-wide trust needs root.
 *
 * This device (and many rooted setups) already runs a KernelSU/Magisk "MoveCertificate"
 * module that promotes user-installed CAs into the APEX store at boot. So the robust,
 * low-risk flow is:
 *   1) import the CA into the user store via the OS ([KeyChain.createInstallIntent]),
 *   2) let MoveCertificate promote it (reboot, or run its service script),
 * rather than hand-editing APEX mounts.
 */
object CaInstaller {

    data class Status(
        val rootAvailable: Boolean,
        val moveCertModule: Boolean,
        val mitmTrustedSystemWide: Boolean,
    )

    /** openssl `-subject_hash_old`: MD5 of the DER-encoded subject, first 4 bytes little-endian. */
    fun subjectHashOld(cert: X509Certificate): String {
        val der = cert.subjectX500Principal.encoded
        val md5 = MessageDigest.getInstance("MD5").digest(der)
        val v = (md5[0].toLong() and 0xff) or
            ((md5[1].toLong() and 0xff) shl 8) or
            ((md5[2].toLong() and 0xff) shl 16) or
            ((md5[3].toLong() and 0xff) shl 24)
        return String.format("%08x", v)
    }

    fun parseCert(bytes: ByteArray): X509Certificate =
        CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate

    fun status(): Status {
        val root = runSu("id").let { it.code == 0 && it.out.contains("uid=0") }
        val module = root && runSu("test -d /data/adb/modules/MoveCertificate && echo yes").out.contains("yes")
        val trusted = root && runSu(
            "grep -l -i mitmproxy /apex/com.android.conscrypt/cacerts/* 2>/dev/null | head -1"
        ).out.isNotBlank()
        return Status(root, module, trusted)
    }

    /** Ask MoveCertificate to re-scan the user store and re-inject into the APEX (no reboot). */
    fun refreshMoveCertificate(): SuResult {
        val script = "/data/adb/modules/MoveCertificate/service.sh"
        return runSu("[ -f $script ] && sh $script; echo done")
    }

    fun reboot(): SuResult = runSu("svc power reboot || reboot")

    /** Build the OS "install CA certificate" intent (no root needed; user confirms). */
    fun installIntent(cert: X509Certificate, name: String = "mitmproxy") =
        KeyChain.createInstallIntent().apply {
            putExtra(KeyChain.EXTRA_CERTIFICATE, cert.encoded)
            putExtra(KeyChain.EXTRA_NAME, name)
        }

    data class SuResult(val code: Int, val out: String)

    private fun runSu(cmd: String): SuResult = try {
        val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        SuResult(p.exitValue(), out.trim())
    } catch (t: Throwable) {
        SuResult(-1, t.message ?: "su failed")
    }
}
