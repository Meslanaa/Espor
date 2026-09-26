package org.mesos.scanner

import java.net.URLDecoder

/**
 * What a scanned code contains. MesOS never acts on a code by itself: the user
 * sees the content first and chooses an action.
 */
sealed interface ScanContent {
    val raw: String

    /** A web link. [secure] is false for plain http. */
    data class Url(override val raw: String, val url: String, val secure: Boolean) : ScanContent

    data class Wifi(
        override val raw: String,
        val ssid: String,
        val password: String?,
        val security: WifiSecurity,
        val hidden: Boolean,
    ) : ScanContent

    data class Phone(override val raw: String, val number: String) : ScanContent

    data class Email(override val raw: String, val address: String, val subject: String?, val body: String?) : ScanContent

    data class Sms(override val raw: String, val number: String, val body: String?) : ScanContent

    data class Geo(override val raw: String, val latitude: Double, val longitude: Double, val label: String?) : ScanContent

    data class Contact(
        override val raw: String,
        val name: String?,
        val phones: List<String>,
        val emails: List<String>,
        val organization: String?,
    ) : ScanContent

    data class Text(override val raw: String) : ScanContent
}

enum class WifiSecurity { OPEN, WEP, WPA, WPA3 }

/** Turns the text of a QR code or barcode into [ScanContent]. Pure Kotlin, unit tested. */
object ScanParser {

    fun parse(input: String): ScanContent {
        val raw = input.trim()
        val lower = raw.lowercase()
        return when {
            lower.startsWith("http://") || lower.startsWith("https://") -> url(raw)
            lower.startsWith("wifi:") -> wifi(raw) ?: ScanContent.Text(raw)
            lower.startsWith("tel:") -> phone(raw, raw.substring(4))
            lower.startsWith("mailto:") -> mailto(raw)
            lower.startsWith("matmsg:") -> matmsg(raw)
            lower.startsWith("smsto:") || lower.startsWith("sms:") -> sms(raw)
            lower.startsWith("geo:") -> geo(raw) ?: ScanContent.Text(raw)
            lower.startsWith("mecard:") -> mecard(raw)
            lower.startsWith("begin:vcard") -> vcard(raw)
            else -> ScanContent.Text(raw)
        }
    }

    private fun url(raw: String): ScanContent {
        if (raw.any { it.isWhitespace() }) return ScanContent.Text(raw)
        return ScanContent.Url(raw, raw, secure = raw.lowercase().startsWith("https://"))
    }

    private fun phone(raw: String, number: String): ScanContent {
        val cleaned = number.trim()
        return if (cleaned.isEmpty()) ScanContent.Text(raw) else ScanContent.Phone(raw, cleaned)
    }

    /** WIFI:T:WPA;S:name;P:secret;H:true;; with \ escaping ; , : " and \. */
    private fun wifi(raw: String): ScanContent.Wifi? {
        val fields = fields(raw.substring(5))
        val ssid = fields["S"]?.takeIf { it.isNotEmpty() } ?: return null
        val type = fields["T"]?.uppercase().orEmpty()
        val password = fields["P"]?.takeIf { it.isNotEmpty() }
        val security = when {
            type == "WEP" -> WifiSecurity.WEP
            type == "SAE" || type == "WPA3" -> WifiSecurity.WPA3
            type.startsWith("WPA") -> WifiSecurity.WPA
            type == "NOPASS" || password == null -> WifiSecurity.OPEN
            else -> WifiSecurity.WPA
        }
        return ScanContent.Wifi(
            raw = raw,
            ssid = ssid,
            password = if (security == WifiSecurity.OPEN) null else password,
            security = security,
            hidden = fields["H"].equals("true", ignoreCase = true),
        )
    }

    private fun mailto(raw: String): ScanContent {
        val rest = raw.substring(7)
        val address = decode(rest.substringBefore('?')).trim()
        val query = if ('?' in rest) query(rest.substringAfter('?')) else emptyMap()
        if (address.isEmpty() && query["to"].isNullOrBlank()) return ScanContent.Text(raw)
        return ScanContent.Email(raw, address.ifEmpty { query["to"].orEmpty() }, query["subject"], query["body"])
    }

    /** MATMSG:TO:a@b.c;SUB:subject;BODY:text;; */
    private fun matmsg(raw: String): ScanContent {
        val fields = fields(raw.substring(7))
        val to = fields["TO"]?.trim().orEmpty()
        if (to.isEmpty()) return ScanContent.Text(raw)
        return ScanContent.Email(raw, to, fields["SUB"]?.takeIf { it.isNotEmpty() }, fields["BODY"]?.takeIf { it.isNotEmpty() })
    }

    /** SMSTO:number:body or sms:number?body=text */
    private fun sms(raw: String): ScanContent {
        val rest = raw.substringAfter(':')
        val (number, body) = if (raw.lowercase().startsWith("smsto:")) {
            rest.substringBefore(':') to rest.substringAfter(':', "").takeIf { it.isNotEmpty() }
        } else {
            rest.substringBefore('?') to (if ('?' in rest) query(rest.substringAfter('?'))["body"] else null)
        }
        if (number.isBlank()) return ScanContent.Text(raw)
        return ScanContent.Sms(raw, number.trim(), body)
    }

    /** geo:41.0082,28.9784 or geo:41.0,28.9?q=41.0,28.9(Label) */
    private fun geo(raw: String): ScanContent.Geo? {
        val rest = raw.substring(4)
        val coordinates = rest.substringBefore('?').substringBefore(';').split(',')
        if (coordinates.size < 2) return null
        val latitude = coordinates[0].trim().toDoubleOrNull() ?: return null
        val longitude = coordinates[1].trim().toDoubleOrNull() ?: return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        val q = if ('?' in rest) query(rest.substringAfter('?'))["q"] else null
        val label = q?.substringAfter('(', "")?.substringBefore(')')?.takeIf { it.isNotBlank() }
            ?: q?.takeIf { it.isNotBlank() && it.none { c -> c.isDigit() || c == ',' } }
        return ScanContent.Geo(raw, latitude, longitude, label)
    }

    /** MECARD:N:Doe,John;TEL:+90 555;EMAIL:j@d.com;ORG:MesOS;; */
    private fun mecard(raw: String): ScanContent {
        val entries = entries(raw.substring(7))
        val name = entries.firstOrNull { it.first == "N" }?.second?.let { n ->
            val parts = n.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size >= 2) "${parts[1]} ${parts[0]}" else parts.firstOrNull()
        }
        return ScanContent.Contact(
            raw = raw,
            name = name,
            phones = entries.filter { it.first == "TEL" }.map { it.second.trim() }.filter { it.isNotEmpty() },
            emails = entries.filter { it.first == "EMAIL" }.map { it.second.trim() }.filter { it.isNotEmpty() },
            organization = entries.firstOrNull { it.first == "ORG" }?.second?.takeIf { it.isNotBlank() },
        )
    }

    private fun vcard(raw: String): ScanContent {
        // Unfold continuation lines (RFC 6350: a line starting with a space continues the previous one).
        val lines = raw.replace("\r\n", "\n").replace("\n ", "").replace("\n\t", "").split('\n')
        var formatted: String? = null
        var structured: String? = null
        var organization: String? = null
        val phones = mutableListOf<String>()
        val emails = mutableListOf<String>()
        for (line in lines) {
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val key = line.substring(0, colon).substringBefore(';').uppercase()
            val value = line.substring(colon + 1).trim()
            if (value.isEmpty()) continue
            when (key) {
                "FN" -> formatted = value
                "N" -> structured = value.split(';').let { parts ->
                    listOfNotNull(parts.getOrNull(1), parts.getOrNull(0)).filter { it.isNotBlank() }.joinToString(" ")
                }
                "TEL" -> phones += value.removePrefix("tel:")
                "EMAIL" -> emails += value
                "ORG" -> organization = value.replace(';', ' ').trim()
            }
        }
        return ScanContent.Contact(raw, formatted ?: structured?.takeIf { it.isNotBlank() }, phones, emails, organization)
    }

    /** KEY:value;KEY:value;; with backslash escapes, keys upper-cased; the last value wins. */
    private fun fields(body: String): Map<String, String> = entries(body).toMap()

    private fun entries(body: String): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        val current = StringBuilder()
        var escaped = false
        fun flush() {
            val text = current.toString()
            current.setLength(0)
            val colon = text.indexOf(':')
            if (colon > 0) result += text.substring(0, colon).trim().uppercase() to text.substring(colon + 1)
        }
        for (c in body) {
            when {
                escaped -> {
                    current.append(c)
                    escaped = false
                }
                c == '\\' -> escaped = true
                c == ';' -> flush()
                else -> current.append(c)
            }
        }
        flush()
        return result
    }

    private fun query(text: String): Map<String, String> =
        text.split('&').mapNotNull { pair ->
            val key = pair.substringBefore('=').lowercase()
            if (key.isEmpty()) null else key to decode(pair.substringAfter('=', ""))
        }.toMap()

    private fun decode(text: String): String =
        try {
            URLDecoder.decode(text.replace("+", "%2B"), "UTF-8")
        } catch (e: IllegalArgumentException) {
            text
        }
}
