package org.mesos.updater

import org.mesos.core.MesOSRelease
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

/** HTTPS-only network access for the updater. Must be called off the main thread. */
internal class UpdateClient {

    fun fetchText(url: String, maxBytes: Int): String {
        val connection = open(url)
        try {
            val buffer = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val chunk = ByteArray(8 * 1024)
                while (true) {
                    val read = readOrThrow(input, chunk)
                    if (read < 0) break
                    if (buffer.size() + read > maxBytes) throw UpdateException(FailureReason.INVALID_MANIFEST, "manifest too large")
                    buffer.write(chunk, 0, read)
                }
            }
            return buffer.toString(Charsets.UTF_8.name())
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Streams [url] into [destination], refusing to read more than [expectedSize] bytes.
     * Returns the lowercase hex SHA-256 of what was written.
     */
    fun download(url: String, destination: File, expectedSize: Long, onProgress: (Float) -> Unit): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val connection = open(url)
        var total = 0L
        try {
            val stream = try {
                destination.outputStream()
            } catch (e: IOException) {
                throw UpdateException(FailureReason.STORAGE, e.message)
            }
            connection.inputStream.use { input ->
                stream.use { output ->
                    val chunk = ByteArray(64 * 1024)
                    while (true) {
                        val read = readOrThrow(input, chunk)
                        if (read < 0) break
                        total += read
                        if (total > expectedSize) throw UpdateException(FailureReason.TOO_LARGE)
                        digest.update(chunk, 0, read)
                        try {
                            output.write(chunk, 0, read)
                        } catch (e: IOException) {
                            throw UpdateException(FailureReason.STORAGE, e.message)
                        }
                        onProgress(total.toFloat() / expectedSize)
                    }
                    try {
                        output.fd.sync()
                    } catch (e: IOException) {
                        throw UpdateException(FailureReason.STORAGE, e.message)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        if (total != expectedSize) {
            throw UpdateException(FailureReason.CHECKSUM_MISMATCH, "size $total != $expectedSize")
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun readOrThrow(input: java.io.InputStream, buffer: ByteArray): Int =
        try {
            input.read(buffer)
        } catch (e: IOException) {
            throw UpdateException(FailureReason.NETWORK, e.message)
        }

    private fun open(url: String): HttpsURLConnection {
        if (!url.startsWith("https://")) throw UpdateException(FailureReason.INSECURE_URL, url)
        val connection = URL(url).openConnection() as? HttpsURLConnection
            ?: throw UpdateException(FailureReason.INSECURE_URL, url)
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.useCaches = false
        connection.setRequestProperty("User-Agent", "MesOS-Updater/${MesOSRelease.current.versionName}")
        try {
            val code = connection.responseCode
            // Redirects are followed only within HTTPS; re-check where we ended up.
            if (connection.url.protocol != "https") throw UpdateException(FailureReason.INSECURE_URL, connection.url.toString())
            when (code) {
                HttpsURLConnection.HTTP_OK -> return connection
                HttpsURLConnection.HTTP_NOT_FOUND -> throw UpdateException(FailureReason.NO_RELEASE, "HTTP 404")
                else -> throw UpdateException(FailureReason.NETWORK, "HTTP $code")
            }
        } catch (e: IOException) {
            connection.disconnect()
            throw UpdateException(FailureReason.NETWORK, e.message)
        } catch (e: UpdateException) {
            connection.disconnect()
            throw e
        }
    }
}
