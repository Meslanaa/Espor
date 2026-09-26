package org.mesos.browser

import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder

/** Search engines MesOS Browser offers. */
enum class SearchEngine(val id: String, val label: String, private val template: String) {
    GOOGLE("google", "Google", "https://www.google.com/search?q=%s"),
    DUCKDUCKGO("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    BING("bing", "Bing", "https://www.bing.com/search?q=%s"),
    STARTPAGE("startpage", "Startpage", "https://www.startpage.com/do/search?q=%s");

    fun searchUrl(query: String): String = template.replace("%s", URLEncoder.encode(query.trim(), "UTF-8"))

    companion object {
        fun fromId(id: String?): SearchEngine = entries.firstOrNull { it.id == id } ?: GOOGLE
    }
}

/** What the browser does with a link. */
enum class LinkAction {
    /** Load in the tab (http and https). */
    LOAD,

    /** Hand to another app (tel:, mailto:, intent:, market:…), always with the user's tap behind it. */
    EXTERNAL,

    /** Never (javascript:, file:, content:, data: navigations, unknown junk). */
    BLOCK,
}

/**
 * Rules for what users type and what pages link to. Pure Kotlin, unit tested,
 * so the browser's security decisions are easy to review.
 */
object UrlPolicy {

    private val externalSchemes = setOf("tel", "mailto", "sms", "smsto", "mms", "mmsto", "geo", "market", "intent", "whatsapp", "tg", "spotify")
    private val blockedSchemes = setOf("javascript", "file", "content", "data", "blob", "about", "chrome", "vbscript")
    private val hostLike = Regex("^[\\p{L}\\p{N}-]+(\\.[\\p{L}\\p{N}-]+)+(:\\d{1,5})?(/.*)?$")
    private val ipLike = Regex("^\\d{1,3}(\\.\\d{1,3}){3}(:\\d{1,5})?(/.*)?$")

    /**
     * Turns address bar input into a URL: links stay links (https:// is added when
     * no scheme is given), everything else becomes a search.
     */
    fun resolve(input: String, engine: SearchEngine): String {
        val text = input.trim()
        if (text.isEmpty()) return ""
        val scheme = schemeOf(text)
        if (scheme == "http" || scheme == "https") return text
        if (scheme != null && scheme in externalSchemes) return text
        if (' ' !in text && (hostLike.matches(text) || ipLike.matches(text) || text.startsWith("localhost"))) {
            return "https://$text"
        }
        return engine.searchUrl(text)
    }

    fun classify(url: String): LinkAction {
        val scheme = schemeOf(url.trim()) ?: return LinkAction.BLOCK
        return when (scheme) {
            "http", "https" -> LinkAction.LOAD
            in externalSchemes -> LinkAction.EXTERNAL
            in blockedSchemes -> LinkAction.BLOCK
            else -> LinkAction.BLOCK
        }
    }

    /** Host for the address bar ("www." dropped), or the input when it has none. */
    fun displayHost(url: String): String {
        val host = try {
            URI(url).host
        } catch (e: URISyntaxException) {
            null
        } ?: return url
        return host.removePrefix("www.")
    }

    fun isSecure(url: String): Boolean = url.trim().lowercase().startsWith("https://")

    /** Scheme in lower case, or null when the text has none. */
    fun schemeOf(text: String): String? {
        val colon = text.indexOf(':')
        if (colon <= 0) return null
        val candidate = text.substring(0, colon)
        if (!candidate.first().isLetter() || candidate.any { !(it.isLetterOrDigit() || it == '+' || it == '-' || it == '.') }) return null
        // "example.com:8080" is a host with a port, not a scheme.
        if ('.' in candidate && text.substring(colon + 1).takeWhile { it != '/' }.all { it.isDigit() }) return null
        return candidate.lowercase()
    }

    /** Only http(s) fallbacks from intent: links are followed. */
    fun safeFallback(url: String?): String? = url?.takeIf { classify(it) == LinkAction.LOAD }
}
