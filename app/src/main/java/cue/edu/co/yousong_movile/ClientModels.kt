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

    fun apiBase(input: String, debug: Boolean = false): String? {
        val text = input.trim().trimEnd('/')
        val uri = runCatching { URI(text) }.getOrNull() ?: return null
        return text.takeIf {
            (uri.scheme == "https" || (debug && text in setOf("http://10.0.2.2:8080", "http://192.168.1.21:8080"))) && uri.host != null && uri.userInfo == null &&
                uri.rawQuery == null && uri.rawFragment == null && uri.rawPath.isNullOrEmpty()
        }
    }

    fun query(input: String): String? = input.trim().replace(Regex("\\s+"), " ").takeIf { it.length in 2..100 }
    fun searchPath(query: String, page: String? = null): String =
        "/api/participante/catalogo/busqueda?q=${encode(query)}" +
            (page?.let { "&pagina=${encode(it)}" } ?: "")
    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
}

data class Establishment(val name: String, val slug: String, val service: String, val canJoin: Boolean)
data class Purpose(val code: String, val description: String)
data class PrivacyPolicy(val version: String, val purposes: List<Purpose>)
// Deliberately not a data class: generated toString must never expose credentials.
class ParticipantSession(val token: String, val expiresAt: String, val pseudonym: String, val name: String, val service: String)
data class SessionStatus(val name: String, val pseudonym: String, val service: String)
data class Song(
    val videoId: String, val title: String, val artist: String, val verified: Boolean,
    val channel: String, val durationSeconds: Int?, val thumbnailUrl: String?
)
data class SearchPage(val songs: List<Song>, val nextPage: String?)
fun durationLabel(seconds: Int?): String = seconds?.takeIf { it >= 0 }?.let {
    "${it / 60}:${(it % 60).toString().padStart(2, '0')}"
} ?: "Duración no disponible"
