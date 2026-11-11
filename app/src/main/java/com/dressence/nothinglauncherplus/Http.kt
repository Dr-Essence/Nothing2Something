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

    /** The body at [address]; throws [Error] for an answer other than 2xx, IOException if offline. */
    @JvmStatic
    @JvmOverloads
    fun get(address: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = 8000): ByteArray {
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.setRequestProperty("User-Agent", AGENT)
            for ((name, value) in headers) connection.setRequestProperty(name, value)
            if (connection.responseCode !in 200..299) throw Error(connection.responseCode)
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }
}
