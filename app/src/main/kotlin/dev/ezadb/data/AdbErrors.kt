package dev.ezadb.data

import android.content.Context
import com.flyfishxu.kadb.cert.KadbCertException
import com.flyfishxu.kadb.exception.AdbAuthException
import com.flyfishxu.kadb.exception.AdbPairAuthException
import dev.ezadb.R
import java.io.EOFException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Turns low level TLS/socket noise into a message the user can act on.
 * Raw stack-trace text ("javax.net.ssl.SSLHandshakeException: ...") is kept as a suffix
 * only when nothing more specific matched.
 */
object AdbErrors {

    fun message(context: Context, error: Throwable?): String {
        if (error == null) return context.getString(R.string.err_unknown)
        val chain = generateSequence(error) { it.cause }.toList()
        val text = chain.mapNotNull { it.message }.joinToString(" ").lowercase()

        if (chain.any { it is AdbPairAuthException } ||
            text.contains("certificate_required") || text.contains("certificate_unknown") ||
            text.contains("unknown_ca") || text.contains("access_denied")
        ) return context.getString(R.string.err_pairing_required)

        if (chain.any { it is AdbAuthException } || text.contains("auth") && text.contains("signature")) {
            return context.getString(R.string.err_auth_rejected)
        }

        if (chain.any { it is KadbCertException }) return context.getString(R.string.err_keystore)

        if (chain.any { it is ConnectException } || text.contains("connection refused") || text.contains("econnrefused")) {
            return context.getString(R.string.err_port_closed)
        }

        if (chain.any { it is UnknownHostException } || chain.any { it is NoRouteToHostException } ||
            text.contains("unreachable") || text.contains("no route to host")
        ) return context.getString(R.string.err_unreachable)

        if (chain.any { it is SocketTimeoutException } || text.contains("timeout") || text.contains("timed out")) {
            return context.getString(R.string.err_timeout)
        }

        if (chain.any { it is SSLException } || text.contains("tls handshake") || text.contains("ssl")) {
            return context.getString(R.string.err_tls)
        }

        if (chain.any { it is EOFException } || text.contains("socket is closed") ||
            text.contains("connection reset") || text.contains("broken pipe") ||
            text.contains("stream closed") || text.contains("connection closed")
        ) return context.getString(R.string.err_link_lost)

        val raw = chain.firstOrNull { !it.message.isNullOrBlank() }?.message?.trim()
        return raw?.take(300) ?: context.getString(R.string.err_unknown)
    }
}
