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

/** "Artista · Género · 3:34": the parts a song row shows, skipping unknown genre or duration. */
internal fun songMetaLine(artist: String, genre: String?, seconds: Int?): String =
    listOfNotNull(artist.takeIf { it.isNotBlank() }, genre?.trim()?.takeIf { it.isNotEmpty() }, clockLabel(seconds))
        .joinToString(META_SEPARATOR)

internal const val META_SEPARATOR = " · "

internal enum class Greeting { Morning, Afternoon, Night }

/** Buenos días 5:00–11:59, buenas tardes 12:00–18:59, buenas noches otherwise (a bar app lives at night). */
internal fun greeting(hour: Int): Greeting = when (hour) {
    in 5..11 -> Greeting.Morning
    in 12..18 -> Greeting.Afternoon
    else -> Greeting.Night
}

/** Genres people search for in Colombian bars; shown after the ones present in today's trends. */
internal val DefaultGenres = listOf("Reggaetón", "Vallenato", "Salsa", "Cumbia", "Pop", "Rock", "Hip hop",
    "Electrónica", "Baladas", "Merengue", "Champeta", "Música popular")

/** A genre tile in "Explorar": the label it searches for and, when trends have one, a cover for it. */
internal data class GenreTile(val label: String, val cover: String?)

/**
 * Trend genres first (most frequent first, ties in trend order), then the defaults not already present.
 * Labels are compared ignoring case and accents so "Reggaeton" and "Reggaetón" make one tile.
 */
internal fun genreTiles(trends: List<Song>, defaults: List<String> = DefaultGenres, max: Int = 12): List<GenreTile> {
    val seen = LinkedHashMap<String, MutableList<Song>>()
    val labels = HashMap<String, String>()
    trends.forEach { song ->
        val label = song.genre?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
        val key = genreKey(label)
        labels.getOrPut(key) { label }
        seen.getOrPut(key) { mutableListOf() } += song
    }
    val fromTrends = seen.entries.sortedByDescending { it.value.size } // stable: ties keep trend order
        .map { (key, songs) -> GenreTile(labels.getValue(key), songs.firstNotNullOfOrNull { it.thumbnailUrl }) }
    val keys = seen.keys
    val extra = defaults.filter { genreKey(it) !in keys }.distinctBy(::genreKey).map { GenreTile(it, null) }
    return (fromTrends + extra).take(max)
}

internal fun genreKey(label: String): String =
    java.text.Normalizer.normalize(label.trim().lowercase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").replace(Regex("\\s+"), " ")

/** A home carousel's data: loads once, keeps showing old data quietly while it refreshes or fails. */
internal sealed interface Feed<out T> {
    /** Not offered for this session (for example a session saved before the QR slug existed). */
    data object Unavailable : Feed<Nothing>
    data object Loading : Feed<Nothing>
    data class Ready<T>(val value: T, val refreshing: Boolean = false) : Feed<T>
    data class Failed(val message: String) : Feed<Nothing>
}

internal fun <T> Feed<T>.reloading(): Feed<T> = when (this) {
    is Feed.Ready -> copy(refreshing = true)
    Feed.Unavailable -> Feed.Unavailable
    else -> Feed.Loading
}

internal fun <T> Feed<T>.loaded(value: T): Feed<T> = if (this == Feed.Unavailable) this else Feed.Ready(value)

/** A failed refresh keeps the data already on screen; only a first load turns into the retry card. */
internal fun <T> Feed<T>.failed(message: String): Feed<T> = when (this) {
    is Feed.Ready -> copy(refreshing = false)
    Feed.Unavailable -> Feed.Unavailable
    else -> Feed.Failed(message)
}

/** What one carousel draws, derived from its feed. */
internal sealed interface Shelf<out T> {
    data object Hidden : Shelf<Nothing>
    data object Loading : Shelf<Nothing>
    data object Empty : Shelf<Nothing>
    data class Items<T>(val items: List<T>) : Shelf<T>
    data class Failed(val message: String) : Shelf<Nothing>
}

internal fun <T, R> Feed<T>.shelf(select: (T) -> List<R>): Shelf<R> = when (this) {
    Feed.Unavailable -> Shelf.Hidden
    Feed.Loading -> Shelf.Loading
    is Feed.Failed -> Shelf.Failed(message)
    is Feed.Ready -> select(value).let { if (it.isEmpty()) Shelf.Empty else Shelf.Items(it) }
}

/** Fraction of the playing song already heard, from the server's remaining time; null when unknown. */
internal fun playedFraction(durationSeconds: Int?, remainingSeconds: Long): Float? {
    val total = durationSeconds?.takeIf { it > 0 } ?: return null
    if (remainingSeconds < 0) return null
    return (1f - remainingSeconds.toFloat() / total).coerceIn(0f, 1f)
}

/** "22:41" in the viewer's zone for a server instant; null when it does not parse. */
internal fun localTime(iso: String, zone: java.util.TimeZone, locale: java.util.Locale): String? {
    val millis = expiresAtMillis(iso) ?: return null
    return java.text.SimpleDateFormat("HH:mm", locale).apply { timeZone = zone }.format(millis)
}
