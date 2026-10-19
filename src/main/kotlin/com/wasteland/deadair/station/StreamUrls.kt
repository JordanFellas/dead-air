package com.wasteland.deadair.station

import com.wasteland.deadair.DeadAirConfig
import java.net.HttpURLConnection
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.URL

/**
 * Checks and opens station stream URLs. Players type these in, so by default the server refuses
 * anything that resolves to itself or the local network, and re-checks every redirect.
 */
object StreamUrls {
    const val MAX_LENGTH = 256
    private const val MAX_REDIRECTS = 5
    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 15000

    class StreamUrlException(message: String) : Exception(message)

    /** Null when the URL looks usable, otherwise why not (shown to the player). */
    fun problemWith(raw: String): String? {
        if (raw.isBlank()) return null
        if (raw.length > MAX_LENGTH) return "URL is too long"
        val uri = try {
            URI(raw.trim())
        } catch (e: Exception) {
            return "That isn't a valid URL"
        }
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return "Only http:// and https:// links work"
        if (uri.host.isNullOrBlank()) return "That isn't a valid URL"
        return null
    }

    /** Connects (following redirects safely) and returns the open connection. Blocking: call off-thread. */
    fun open(raw: String): HttpURLConnection {
        problemWith(raw)?.let { throw StreamUrlException(it) }
        var url = URI(raw.trim()).toURL()
        repeat(MAX_REDIRECTS + 1) {
            checkHost(url)
            val connection = url.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "DeadAir-Minecraft-Radio/1.0")
            connection.setRequestProperty("Icy-MetaData", "1")
            connection.setRequestProperty("Accept", "audio/mpeg, audio/*;q=0.9, */*;q=0.5")
            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location") ?: throw StreamUrlException("Redirect without a target")
                connection.disconnect()
                url = url.toURI().resolve(location.trim()).toURL()
                if (url.protocol.lowercase() !in setOf("http", "https")) throw StreamUrlException("Redirected to a non-web link")
                return@repeat
            }
            if (code !in 200..299) {
                connection.disconnect()
                throw StreamUrlException("Server answered $code")
            }
            return connection
        }
        throw StreamUrlException("Too many redirects")
    }

    private fun checkHost(url: URL) {
        if (DeadAirConfig.STREAM_ALLOW_LOCAL.get()) return
        val addresses = try {
            InetAddress.getAllByName(url.host)
        } catch (e: Exception) {
            throw StreamUrlException("Can't find ${url.host}")
        }
        if (addresses.any { isPrivate(it) }) throw StreamUrlException("Local network addresses aren't allowed")
    }

    private fun isPrivate(address: InetAddress): Boolean {
        if (address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress ||
            address.isAnyLocalAddress || address.isMulticastAddress
        ) return true
        val bytes = address.address
        if (address is Inet6Address) return (bytes[0].toInt() and 0xFE) == 0xFC // fc00::/7 unique local
        val first = bytes[0].toInt() and 0xFF
        val second = bytes[1].toInt() and 0xFF
        return first == 0 || (first == 100 && second in 64..127) // "this network" and carrier-grade NAT
    }
}
