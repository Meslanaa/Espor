package org.mesos.messages

/**
 * The headers of an MMS "notification indication" (M-Notification.ind, OMA MMS
 * encapsulation), which says an MMS is waiting on the carrier's server.
 */
data class MmsNotification(
    val transactionId: String,
    val contentLocation: String,
    val from: String?,
    val subject: String?,
    val messageSize: Long?,
    /** Seconds since the epoch, or seconds from now when [expiryRelative]. */
    val expiry: Long?,
    val expiryRelative: Boolean,
    val messageClass: Int?,
    val version: Int?,
)

/**
 * Minimal WSP/MMS header parser for [MmsNotification]. Pure Kotlin, unit tested.
 * Unknown headers are skipped using the generic WSP value encodings.
 */
object MmsNotificationParser {

    private const val MESSAGE_TYPE = 0x8C
    private const val NOTIFICATION_IND = 0x82
    private const val TRANSACTION_ID = 0x98
    private const val VERSION = 0x8D
    private const val FROM = 0x89
    private const val SUBJECT = 0x96
    private const val MESSAGE_CLASS = 0x8A
    private const val MESSAGE_SIZE = 0x8E
    private const val EXPIRY = 0x88
    private const val CONTENT_LOCATION = 0x83

    fun parse(pdu: ByteArray): MmsNotification? {
        val reader = Reader(pdu)
        var type: Int? = null
        var transactionId: String? = null
        var contentLocation: String? = null
        var from: String? = null
        var subject: String? = null
        var size: Long? = null
        var expiry: Long? = null
        var relative = false
        var messageClass: Int? = null
        var version: Int? = null
        try {
            while (reader.hasMore()) {
                val header = reader.byte()
                if (header < 0x80) return null // Not a well-known MMS header: not a notification.
                when (header) {
                    MESSAGE_TYPE -> type = reader.byte()
                    TRANSACTION_ID -> transactionId = reader.textString()
                    VERSION -> version = reader.shortInteger()
                    CONTENT_LOCATION -> contentLocation = reader.textString()
                    SUBJECT -> subject = reader.encodedString()
                    MESSAGE_SIZE -> size = reader.longInteger()
                    MESSAGE_CLASS -> messageClass = reader.byte().takeIf { it >= 0x80 }?.let { it and 0x7F }
                        ?: reader.skipTextAfterFirst()
                    FROM -> from = reader.fromAddress()
                    EXPIRY -> {
                        val length = reader.valueLength()
                        val end = reader.position + length
                        val token = reader.byte()
                        relative = token == 0x81
                        expiry = reader.longInteger()
                        reader.position = end
                    }
                    else -> reader.skipValue()
                }
            }
        } catch (e: IndexOutOfBoundsException) {
            return null
        }
        if (type != NOTIFICATION_IND) return null
        return MmsNotification(
            transactionId = transactionId ?: return null,
            contentLocation = contentLocation ?: return null,
            from = from,
            subject = subject,
            messageSize = size,
            expiry = expiry,
            expiryRelative = relative,
            messageClass = messageClass,
            version = version,
        )
    }

    private class Reader(private val data: ByteArray) {
        var position = 0

        fun hasMore() = position < data.size

        fun byte(): Int = data[position++].toInt() and 0xFF

        private fun peek(): Int = data[position].toInt() and 0xFF

        /** Text until NUL; a leading 0x7F quote is dropped. */
        fun textString(): String {
            if (peek() == 0x7F) position++
            val start = position
            while (data[position] != 0.toByte()) position++
            val text = String(data, start, position - start, Charsets.UTF_8)
            position++ // NUL
            return text
        }

        fun shortInteger(): Int = byte() and 0x7F

        fun uintvar(): Long {
            var value = 0L
            var count = 0
            while (true) {
                val b = byte()
                value = (value shl 7) or (b and 0x7F).toLong()
                if (b and 0x80 == 0) return value
                if (++count > 5) throw IndexOutOfBoundsException("uintvar too long")
            }
        }

        fun valueLength(): Int {
            val first = byte()
            return when {
                first < 0x1F -> first
                first == 0x1F -> uintvar().toInt()
                else -> throw IndexOutOfBoundsException("not a value length")
            }
        }

        fun longInteger(): Long {
            val length = byte()
            if (length > 8) throw IndexOutOfBoundsException("long integer too long")
            var value = 0L
            repeat(length) { value = (value shl 8) or byte().toLong() }
            return value
        }

        /** Encoded-string-value: plain text, or length + charset + text. */
        fun encodedString(): String {
            val first = peek()
            if (first >= 0x20) return textString()
            val length = valueLength()
            val end = position + length
            // Charset (short integer or long integer); MesOS reads the text as UTF-8/ASCII.
            if (peek() >= 0x80) byte() else longInteger()
            val text = textString()
            position = end
            return text
        }

        /** From: value-length, then address-present (0x80) + address, or insert-address (0x81). */
        fun fromAddress(): String? {
            val length = valueLength()
            val end = position + length
            val token = byte()
            val address = if (token == 0x80 && position < end) encodedString() else null
            position = end
            return address?.substringBefore("/TYPE=")
        }

        /** Message class given as a token-text instead of a short integer. */
        fun skipTextAfterFirst(): Int? {
            position--
            textString()
            return null
        }

        /** Skips any header value: short integer, length-prefixed data or text. */
        fun skipValue() {
            val first = peek()
            when {
                first >= 0x80 -> position++
                first <= 0x1F -> {
                    val length = valueLength()
                    position += length
                }
                else -> textString()
            }
        }
    }
}
