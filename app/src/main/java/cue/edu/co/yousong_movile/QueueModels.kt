package cue.edu.co.yousong_movile

import java.net.URLEncoder
import org.json.JSONObject

enum class ServiceState { ABIERTO, PAUSADO, CERRADO }
data class QueueSong(val videoId: String, val title: String, val artist: String, val verified: Boolean,
    val durationSeconds: Int?, val thumbnailUrl: String?)
data class QueueItem(val requestId: String, val position: Int, val votes: Long, val song: QueueSong,
    val waitSeconds: Long, val voted: Boolean?)
data class PlayingSong(val requestId: String?, val song: QueueSong, val startedAt: String,
    val remainingSeconds: Long, val voted: Boolean?)
data class QueueSnapshot(val version: Long, val state: ServiceState, val playing: PlayingSong?, val items: List<QueueItem>)
data class SupportResult(val requestId: String, val votes: Long, val voted: Boolean, val position: Int?, val created: Boolean)
data class Suggestion(val text: String, val song: Song?)

object QueueContract {
    fun requireVideoId(value: String) { require(Regex("[A-Za-z0-9_-]{11}").matches(value)) }
    fun votePath(id: String): String {
        require(Regex("[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}").matches(id))
        return "/api/participante/solicitudes/$id/votos"
    }
    fun suggestionsPath(query: String): String {
        val normalized = requireNotNull(ClientContract.query(query))
        return "/api/participante/catalogo/sugerencias?q=${URLEncoder.encode(normalized, "UTF-8")}"
    }
}

internal fun parseQueue(json: JSONObject): QueueSnapshot {
    val items = json.getJSONArray("cola")
    val playing = if (json.isNull("enReproduccion")) null else json.getJSONObject("enReproduccion").let {
        PlayingSong(it.nullableString("solicitudId"), parseQueueSong(it.getJSONObject("cancion")),
            it.getString("iniciadaAt"), it.getLong("restanteSegundos"), it.optionalVote())
    }
    return QueueSnapshot(json.getLong("colaVersion"), ServiceState.valueOf(json.getString("estado")), playing,
        List(items.length()) { index -> items.getJSONObject(index).let {
            QueueItem(it.getString("solicitudId"), it.getInt("posicion"), it.getLong("votos"),
                parseQueueSong(it.getJSONObject("cancion")), it.getLong("esperaEstimadaSegundos"), it.optionalVote())
        } })
}

private fun parseQueueSong(json: JSONObject) = QueueSong(json.getString("videoId"), json.getString("titulo"),
    json.getString("artista"), json.getBoolean("artistaVerificado"),
    if (json.isNull("duracionSegundos")) null else json.getInt("duracionSegundos"), json.nullableString("miniaturaUrl"))

internal fun parseSupport(json: JSONObject) = SupportResult(json.getString("solicitudId"), json.getLong("votos"),
    json.getBoolean("votado"), if (json.isNull("posicion")) null else json.getInt("posicion"), json.getBoolean("creada"))

internal fun parseSuggestions(json: JSONObject): List<Suggestion> {
    val items = json.getJSONArray("sugerencias")
    require(items.length() <= 8)
    return List(items.length()) { index -> items.getJSONObject(index).let {
        val song = when (it.getString("tipo")) {
            "CANCION" -> parseCatalogSong(it.getJSONObject("cancion"))
            "BUSQUEDA" -> null
            else -> error("Unsupported suggestion type")
        }
        Suggestion(it.getString("texto"), song)
    } }
}

private fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else getString(key)
// Absent participant context must never become permission to vote again.
private fun JSONObject.optionalVote(): Boolean? = if (!has("votado") || isNull("votado")) null else getBoolean("votado")
