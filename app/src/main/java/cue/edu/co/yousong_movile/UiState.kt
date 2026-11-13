package cue.edu.co.yousong_movile

/** Pure presentation rules, kept free of Compose so they run as JVM unit tests. */

/** `m:ss` clock for a known, non-negative duration; null when the catalog has none. */
internal fun clockLabel(seconds: Long?): String? = seconds?.takeIf { it >= 0 }?.let {
    "${it / 60}:${(it % 60).toString().padStart(2, '0')}"
}

internal fun clockLabel(seconds: Int?): String? = clockLabel(seconds?.toLong())

/** Whole minutes for "Espera ~ N min": rounded up, never below one for a positive wait. */
internal fun waitMinutes(seconds: Long): Int? = when {
    seconds < 0 -> null
    seconds == 0L -> 0
    else -> ((seconds + 59) / 60).toInt()
}

internal enum class Tab { Inicio, Buscar, Cola }

/** Back from a secondary tab returns home; null means back leaves the app. */
internal fun backTarget(tab: Tab): Tab? = if (tab == Tab.Inicio) null else Tab.Inicio

internal sealed interface VoteState {
    data object Votable : VoteState
    data object Voted : VoteState
    data object Pending : VoteState
    /** The local cannot take votes right now, or the server did not say whether you voted. */
    data class Unavailable(val reason: UnavailableReason) : VoteState
}

internal enum class UnavailableReason { Paused, Unknown, NotReady }

/**
 * Maps server facts to the vote button. Polling briefly flips readiness every few seconds,
 * so [interactive] must be a settled signal, not the raw `ready` flag, to avoid flicker.
 * Only the row whose mutation is in flight shows progress; the rest keep their look.
 */
internal fun voteState(
    voted: Boolean?,
    state: ServiceState?,
    interactive: Boolean,
    pendingThis: Boolean,
): VoteState = when {
    voted == true -> VoteState.Voted
    pendingThis -> VoteState.Pending
    voted == null -> VoteState.Unavailable(UnavailableReason.Unknown)
    state != ServiceState.ABIERTO -> VoteState.Unavailable(UnavailableReason.Paused)
    !interactive -> VoteState.Unavailable(UnavailableReason.NotReady)
    else -> VoteState.Votable
}

/** What the song detail's primary button offers for a catalog song. */
internal enum class SongCta { Request, Vote, Voted, Unavailable }

internal fun songCta(song: Song, snapshot: QueueSnapshot?, interactive: Boolean): SongCta {
    val queued = snapshot?.items?.firstOrNull { it.song.videoId == song.videoId }
    val playing = snapshot?.playing?.takeIf { it.song.videoId == song.videoId }
    val voted = queued?.voted ?: playing?.voted
    val exists = queued != null || playing != null
    return when {
        exists && voted == true -> SongCta.Voted
        snapshot?.state != ServiceState.ABIERTO || !interactive -> SongCta.Unavailable
        exists && voted == null -> SongCta.Unavailable
        exists -> SongCta.Vote
        else -> SongCta.Request
    }
}

/** The crowd favourite on Inicio: the most voted queued song, ties keep server order. */
internal fun favourite(snapshot: QueueSnapshot?): QueueItem? =
    snapshot?.items?.filter { it.votes > 0 }?.maxByOrNull { it.votes }

internal enum class ServiceChip { Live, Paused, Closed, Maintenance }

internal fun serviceChip(state: ServiceState?): ServiceChip? = when (state) {
    ServiceState.ABIERTO -> ServiceChip.Live
    ServiceState.PAUSADO -> ServiceChip.Paused
    ServiceState.CERRADO -> ServiceChip.Closed
    ServiceState.MANTENIMIENTO -> ServiceChip.Maintenance
    else -> null
}

/** Friendly service state for the establishment card (the API sends raw enum names). */
internal fun serviceChip(service: String): ServiceChip? =
    serviceChip(ServiceState.entries.firstOrNull { it.name == service })

/** A server-supplied opening instant, described relative to the viewer's local day. */
internal data class Opening(val dayOffset: Int, val time: String, val date: String)

internal fun opening(iso: String?, now: Long, zone: java.util.TimeZone, locale: java.util.Locale): Opening? {
    val millis = iso?.let(::expiresAtMillis) ?: return null
    fun day(value: Long) = java.util.Calendar.getInstance(zone, locale).apply { timeInMillis = value }.let {
        it.get(java.util.Calendar.YEAR) * 400 + it.get(java.util.Calendar.DAY_OF_YEAR)
    }
    val time = java.text.SimpleDateFormat("HH:mm", locale).apply { timeZone = zone }.format(millis)
    val date = java.text.SimpleDateFormat("EEEE d", locale).apply { timeZone = zone }.format(millis)
    val offset = (day(millis) - day(now)).let { if (it in 0..1) it else 2 }
    return Opening(offset, time, date)
}
