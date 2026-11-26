package cue.edu.co.yousong_movile

import java.net.URI
import java.net.URLEncoder

/** Pure contract validation: pasted QR hosts never select the API or receive credentials. */
object ClientContract {
    private val slugPattern = Regex("[a-z0-9-]{3,80}")

    fun slug(input: String): String? {
        val text = input.trim()
        if (slugPattern.matches(text)) return text
        val uri = runCatching { URI(text) }.getOrNull() ?: return null
        if (uri.scheme !in listOf("https", "http") || uri.host == null || uri.userInfo != null ||
            uri.rawQuery != null || uri.rawFragment != null) return null
        val path = uri.rawPath ?: return null
        return path.removePrefix("/s/").takeIf { path.startsWith("/s/") && slugPattern.matches(it) }
    }

    /** Emulator host and paired-phone loopback (adb reverse): the only HTTP origins every debug build accepts. */
    private val debugHttpOrigins = setOf("http://10.0.2.2:8080", "http://127.0.0.1:8080")

    /**
     * The API origin, or null when it is not allowed. HTTPS always; plain HTTP only in debug builds, only on port
     * 8080 and only for the fixed debug hosts or [lanHost], the LAN address chosen at build time
     * (`-PyousongLanHost`), which must be a private IPv4 address ([privateLanHost]).
     */
    fun apiBase(input: String, debug: Boolean = false, lanHost: String = ""): String? {
        val text = input.trim().trimEnd('/')
        val uri = runCatching { URI(text) }.getOrNull() ?: return null
        val http = debug && (text in debugHttpOrigins || (privateLanHost(lanHost) && text == "http://$lanHost:8080"))
        return text.takeIf {
            (uri.scheme == "https" || http) && uri.host != null && uri.userInfo == null &&
                uri.rawQuery == null && uri.rawFragment == null && uri.rawPath.isNullOrEmpty()
        }
    }

    private val ipv4 = Regex("(0|[1-9][0-9]{0,2})\\.(0|[1-9][0-9]{0,2})\\.(0|[1-9][0-9]{0,2})\\.(0|[1-9][0-9]{0,2})")

    /** True only for a dotted IPv4 address in 10.0.0.0/8, 172.16.0.0/12 or 192.168.0.0/16 (RFC 1918). */
    fun privateLanHost(host: String): Boolean {
        val octets = ipv4.matchEntire(host)?.groupValues?.drop(1)?.map { it.toInt() } ?: return false
        if (octets.any { it > 255 }) return false
        val (a, b) = octets
        return a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168)
    }

    fun query(input: String): String? = input.trim().replace(Regex("\\s+"), " ").takeIf { it.length in 2..100 }
    fun searchPath(query: String, page: String? = null): String =
        "/api/participante/catalogo/busqueda?q=${encode(query)}" +
            (page?.let { "&pagina=${encode(it)}" } ?: "")
    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
}

data class Establishment(val name: String, val slug: String, val service: String, val canJoin: Boolean,
    val nextOpening: String? = null)
data class Purpose(val code: String, val description: String)
data class PolicySection(val title: String, val text: String)
data class PrivacyPolicy(val version: String, val purposes: List<Purpose>,
    val sections: List<PolicySection> = emptyList())
// Deliberately not a data class: generated toString must never expose credentials.
class ParticipantSession(val token: String, val expiresAt: String, val pseudonym: String, val name: String,
    val service: String, val qrSlug: String? = null)
data class SessionStatus(val name: String, val pseudonym: String, val service: String)
/** A catalog song. [genre] is an optional Spanish label from the backend; null when it has none. */
data class Song(
    val videoId: String, val title: String, val artist: String, val verified: Boolean,
    val channel: String, val durationSeconds: Int?, val thumbnailUrl: String?, val genre: String? = null
)
data class SearchPage(val songs: List<Song>, val nextPage: String?)
fun durationLabel(seconds: Int?): String = seconds?.takeIf { it >= 0 }?.let {
    "${it / 60}:${(it % 60).toString().padStart(2, '0')}"
} ?: "Duración no disponible"
