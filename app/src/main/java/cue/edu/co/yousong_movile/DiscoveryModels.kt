package cue.edu.co.yousong_movile

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** YouTube's most popular music in Colombia, as cached by the backend (anonymous endpoint). */
data class Trends(val region: String, val updatedAt: String, val songs: List<Song>)

/** A ranked song from the local's history: votes, requests or plays, depending on the list. */
data class RankedSong(val song: Song, val count: Long)

data class RecentPlay(val song: Song, val playedAt: String, val playing: Boolean)

/** The local's own rankings over the last [periodDays] days (anonymous endpoint, by QR slug). */
data class Highlights(
    val periodDays: Int,
    val mostVoted: List<RankedSong>,
    val mostRequested: List<RankedSong>,
    val mostPlayed: List<RankedSong>,
    val recent: List<RecentPlay>,
)

object DiscoveryContract {
    const val TRENDS_PATH = "/api/public/tendencias"
    const val TRENDS_MAX = 25
    const val HIGHLIGHTS_MAX = 10
    const val DEFAULT_DAYS = 30

    fun highlightsPath(slug: String, days: Int = DEFAULT_DAYS): String {
        require(validatedQrSlug(slug) != null)
        require(days in 1..365)
        return "/api/public/establecimientos/$slug/destacadas?dias=$days"
    }
}

internal fun parseTrends(json: JSONObject): Trends {
    val region = json.getString("region")
    if (region.isBlank()) throw JSONException("region is blank")
    val updated = json.getString("actualizadoAt")
    if (expiresAtMillis(updated) == null) throw JSONException("actualizadoAt is not an instant")
    val songs = json.getJSONArray("canciones").bounded(DiscoveryContract.TRENDS_MAX)
    return Trends(region, updated, List(songs.length()) { parseListedSong(songs.getJSONObject(it)) })
}

internal fun parseHighlights(json: JSONObject): Highlights {
    val days = json.getInt("periodoDias")
    if (days !in 1..365) throw JSONException("periodoDias out of range")
    fun ranked(key: String, countKey: String): List<RankedSong> {
        val items = json.getJSONArray(key).bounded(DiscoveryContract.HIGHLIGHTS_MAX)
        return List(items.length()) { index ->
            val item = items.getJSONObject(index)
            val count = item.getLong(countKey)
            if (count < 0) throw JSONException("$countKey is negative")
            RankedSong(parseListedSong(item.getJSONObject("cancion")), count)
        }
    }
    val recent = json.getJSONArray("ultimasSonadas").bounded(DiscoveryContract.HIGHLIGHTS_MAX)
    return Highlights(days, ranked("masVotadas", "votos"), ranked("masPedidas", "veces"),
        ranked("masSonadas", "reproducciones"),
        List(recent.length()) { index ->
            val item = recent.getJSONObject(index)
            val at = item.getString("sonoAt")
            if (expiresAtMillis(at) == null) throw JSONException("sonoAt is not an instant")
            RecentPlay(parseListedSong(item.getJSONObject("cancion")), at, item.getBoolean("enCurso"))
        })
}

/**
 * A song in a public list. It may come in the catalog shape (with `canal`) or the queue shape
 * (without it), so only `canal` is optional here; every other field keeps the strict rules.
 */
internal fun parseListedSong(json: JSONObject): Song {
    val id = json.getString("videoId")
    QueueContract.requireVideoId(id)
    return Song(id, json.getString("titulo"), json.getString("artista"), json.getBoolean("artistaVerificado"),
        if (json.isNull("canal")) "" else json.getString("canal"),
        if (json.isNull("duracionSegundos")) null else json.getInt("duracionSegundos"),
        if (json.isNull("miniaturaUrl")) null else json.getString("miniaturaUrl"),
        json.optionalGenre())
}

private fun JSONArray.bounded(max: Int): JSONArray {
    if (length() > max) throw JSONException("list longer than $max")
    return this
}
