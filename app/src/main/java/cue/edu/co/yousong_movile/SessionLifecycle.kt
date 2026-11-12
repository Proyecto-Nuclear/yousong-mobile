package cue.edu.co.yousong_movile

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

interface SessionRecordStore {
    fun read(): Pair<String, ParticipantSession>?
    fun write(origin: String, session: ParticipantSession)
    fun clear()
}

internal fun expiresAtMillis(value: String): Long? {
    val normalized = value.replace(Regex("\\.(\\d{3})\\d+(?=Z|[+-])"), ".$1")
    return listOf("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX").firstNotNullOfOrNull { pattern ->
        val format = SimpleDateFormat(pattern, Locale.ROOT).apply {
            isLenient = false; timeZone = TimeZone.getTimeZone("UTC")
        }
        val position = java.text.ParsePosition(0)
        format.parse(normalized, position)?.time?.takeIf { position.index == normalized.length }
    }
}

class SessionLifecycle(private val store: SessionRecordStore, private val now: () -> Long = System::currentTimeMillis) {
    fun restore(): Pair<String, ParticipantSession>? = try {
        store.read()?.takeIf { (_, session) ->
            session.token.isNotBlank() && (expiresAtMillis(session.expiresAt) ?: 0) > now()
        }.also { if (it == null) store.clear() }
    } catch (_: Exception) { store.clear(); null }

    fun joined(origin: String, session: ParticipantSession) { store.write(origin, session) }
    fun ended() { store.clear() }
    fun failure(status: Int, code: String): Boolean = (status == 401).also { if (it) ended() }
    companion object { const val POLL_INTERVAL_MS = 7_000L }
}
