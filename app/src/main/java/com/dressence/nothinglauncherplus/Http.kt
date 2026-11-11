package com.dressence.nothinglauncherplus

import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloading something over HTTPS, the module's one way to do it (until 0.70.8 SteamGridArt,
 * SteamTrailers (twice) and PlayStore each had their own copy of the same steps).
 *
 * Blocking: call it off the main thread.
 */
object Http {

    /** The name the module gives itself, unless a caller needs another (PlayStore: a browser's). */
    const val AGENT = "Nothing2Something"

    /** An answer other than 2xx, with its code (401 or 403: refused; 404: not there). */
    class Error(val code: Int) : java.io.IOException("HTTP $code")

    /** The largest body read unless a caller asks for less: far above any picture fetched. */
    const val MAX_BYTES = 48 * 1024 * 1024

    /**
     * The body at [address]; throws [Error] for an answer other than 2xx, IOException if offline
     * or if the body is larger than [maxBytes]. Read in steps against that limit: a whole
     * body read first, checked after, could be hundreds of megabytes (a Steam movie offered
     * only as an mp4) and run the app out of memory (0.70.14 audit).
     */
    @JvmStatic
    @JvmOverloads
    fun get(address: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = 8000,
            maxBytes: Int = MAX_BYTES): ByteArray {
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.setRequestProperty("User-Agent", AGENT)
            for ((name, value) in headers) connection.setRequestProperty(name, value)
            if (connection.responseCode !in 200..299) throw Error(connection.responseCode)
            if (connection.contentLengthLong > maxBytes) {
                throw java.io.IOException("too large: ${connection.contentLengthLong} bytes")
            }
            return connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (out.size() + n > maxBytes) throw java.io.IOException("over $maxBytes bytes")
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }
}
