package cue.edu.co.yousong_movile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ApiFailure(val status: Int, val code: String) : Exception("API request failed")

/** All I/O stays off main; redirects are forbidden so bearer headers cannot cross origins. */
class ParticipantApi(private val base: String) {
    init { require(ClientContract.apiBase(base, BuildConfig.DEBUG) != null) }

    suspend fun establishment(slug: String): Establishment {
        require(validatedQrSlug(slug) != null)
        return parseEstablishment(request("/api/public/establecimientos/$slug"))
    }

    suspend fun policy(): PrivacyPolicy = request("/api/public/politica").let { json ->
        val purposes = json.getJSONArray("finalidades")
        PrivacyPolicy(json.getString("version"), List(purposes.length()) {
            purposes.getJSONObject(it).let { item -> Purpose(item.getString("codigo"), item.getString("descripcion")) }
        })
    }

    suspend fun join(slug: String, policy: PrivacyPolicy, f1: Boolean, f2: Boolean): ParticipantSession {
        require(validatedQrSlug(slug) != null)
        require(f1 && policy.version.isNotBlank() && policy.version.length <= 20)
        val body = JSONObject().put("canal", "ANDROID").put("politicaVersion", policy.version)
            .put("autorizaF1", f1).put("autorizaF2", f2)
        return request("/api/public/establecimientos/$slug/participaciones", "POST", body = body).let {
            ParticipantSession(it.getString("token"), it.getString("expiraAt"), it.getString("seudonimo"),
                it.getJSONObject("establecimiento").getString("nombre"), it.getString("servicio"), slug)
        }
    }

    suspend fun status(token: String): SessionStatus = request("/api/participante/sesion", token = token).let {
        SessionStatus(it.getJSONObject("establecimiento").getString("nombre"), it.getString("seudonimo"), it.getString("servicio"))
    }
    suspend fun leave(token: String) { request("/api/participante/sesion", "DELETE", token) }
    suspend fun search(token: String, query: String, page: String? = null): SearchPage {
        require(ClientContract.query(query) != null)
        return parseSearch(request(ClientContract.searchPath(query, page), token = token))
    }

    suspend fun queue(token: String): QueueSnapshot = parseQueue(request("/api/participante/cola", token = token))

    suspend fun requestSong(token: String, videoId: String): SupportResult {
        QueueContract.requireVideoId(videoId)
        return parseSupport(request("/api/participante/solicitudes", "POST", token,
            JSONObject().put("videoId", videoId)))
    }

    suspend fun vote(token: String, requestId: String): SupportResult =
        parseSupport(request(QueueContract.votePath(requestId), "POST", token))

    suspend fun suggestions(token: String, query: String): List<Suggestion> =
        parseSuggestions(request(QueueContract.suggestionsPath(query), token = token))

    private suspend fun request(path: String, method: String = "GET", token: String? = null, body: JSONObject? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val connection = URL(base + path).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.useCaches = false
                connection.setRequestProperty("Accept", "application/json")
                token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
                body?.let {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.outputStream.use { stream -> stream.write(it.toString().toByteArray(Charsets.UTF_8)) }
                }
                val status = connection.responseCode
                if (status !in 200..299) {
                    // Never surface raw server payloads, URLs or headers to UI/logs.
                    val code = runCatching {
                        connection.errorStream?.use { stream ->
                            JSONObject(String(stream.readBytesBounded(2 * 1024 * 1024), Charsets.UTF_8)).optString("code")
                        }
                    }.getOrNull().orEmpty()
                    throw ApiFailure(status, code)
                }
                if (status == 204) JSONObject() else connection.inputStream.use {
                    JSONObject(String(it.readBytesBounded(2 * 1024 * 1024), Charsets.UTF_8))
                }
            } finally { connection.disconnect() }
        }
}

internal fun parseEstablishment(json: JSONObject): Establishment = Establishment(
    json.getString("nombre"), requireNotNull(validatedQrSlug(json.get("qrSlug"))),
    json.getString("servicio"), json.getBoolean("puedeParticipar"),
    (json.opt("proximaApertura") as? String)?.takeIf { expiresAtMillis(it) != null })

internal fun parseSearch(json: JSONObject): SearchPage {
    val results = json.getJSONArray("resultados")
    return SearchPage(List(results.length()) { index ->
        parseCatalogSong(results.getJSONObject(index))
    }, if (json.isNull("siguientePagina")) null else json.getString("siguientePagina"))
}

internal fun parseCatalogSong(json: JSONObject): Song = Song(json.getString("videoId"), json.getString("titulo"),
    json.getString("artista"), json.getBoolean("artistaVerificado"), json.getString("canal"),
    if (json.isNull("duracionSegundos")) null else json.getInt("duracionSegundos"),
    if (json.isNull("miniaturaUrl")) null else json.getString("miniaturaUrl"))

fun errorLabel(error: Exception): String = when {
    error is ApiFailure && error.status == 401 -> "La sesión terminó o la credencial caducó. Vuelve a unirte."
    error is ApiFailure && error.code == "SEARCH_BUDGET_EXHAUSTED" -> "El local agotó sus búsquedas de hoy. Consulta al personal."
    error is ApiFailure && error.code == "CATALOG_NOT_CONFIGURED" -> "El catálogo de música no está configurado. Consulta al personal."
    error is ApiFailure && error.code == "CATALOG_QUOTA_EXCEEDED" -> "La cuota del catálogo se agotó. Inténtalo más tarde."
    error is ApiFailure && error.status == 429 -> "Demasiadas solicitudes. Espera un momento antes de reintentar."
    error is ApiFailure && error.code == "POLICY_VERSION_MISMATCH" -> "La política cambió. Consulta de nuevo el local para renovar tu consentimiento."
    error is ApiFailure && error.status == 403 -> "No tienes permiso para esta acción."
    error is ApiFailure && error.code == "ALREADY_VOTED" -> "Ya apoyaste esta canción en esta sesión."
    error is ApiFailure && error.code == "QUEUE_FULL" -> "La cola está llena. Puedes apoyar una canción que ya esté en ella."
    error is ApiFailure && error.code == "SONG_RECENTLY_PLAYED" -> "Esta canción sonó hace poco. Elige otra canción."
    error is ApiFailure && error.code == "SONG_UNAVAILABLE" -> "Esta canción no está disponible en el local."
    error is ApiFailure && error.code == "REQUEST_NOT_ACTIVE" -> "La solicitud ya no está activa. Actualiza la cola."
    error is ApiFailure && error.code == "REQUEST_NOT_FOUND" -> "La solicitud no se encontró en esta sesión. Actualiza la cola."
    error is ApiFailure && error.code == "INVALID_VIDEO_ID" -> "La canción no tiene un identificador válido."
    error is ApiFailure && (error.status == 502 || error.status == 503) -> "El proveedor de música no está disponible o su cuota se agotó. Inténtalo más tarde."
    error is ApiFailure && error.code == "ESTABLISHMENT_IN_MAINTENANCE" -> "El local está en mantenimiento. Espera a que el personal habilite el servicio y actualiza el estado."
    error is ApiFailure && error.code == "ESTABLISHMENT_PAUSED" -> "El local está pausado. Espera a que el personal reanude el servicio y actualiza el estado."
    error is ApiFailure && error.code == "SESSION_PAUSED" -> "La sesión está pausada. Puedes consultar la cola; espera a que el personal la reanude."
    error is ApiFailure && error.code == "OUTSIDE_SCHEDULE" -> "El local está fuera de horario. Consulta la próxima apertura y actualiza el estado antes de ingresar."
    error is ApiFailure && error.code == "NO_ACTIVE_SESSION" -> "El local aún no tiene una sesión abierta. Espera a que el personal la abra y actualiza el estado."
    error is ApiFailure && error.code == "SESSION_NOT_ACCEPTING" -> "La sesión no admite solicitudes ni votos ahora. Actualiza el estado; puedes seguir consultando la cola."
    error is ApiFailure && error.status == 409 -> "El servicio no admite esta acción ahora. Actualiza el estado del local."
    error is ApiFailure && error.status == 404 -> "No se encontró el establecimiento. Revisa el enlace."
    else -> "No se pudo conectar al servicio. Revisa la dirección y la conexión e inténtalo de nuevo."
}
