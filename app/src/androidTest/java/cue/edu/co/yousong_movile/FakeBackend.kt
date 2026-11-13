package cue.edu.co.yousong_movile

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Minimal participant API on 127.0.0.1:8080 (the only debug cleartext origin the client accepts).
 * Synthetic data only; it never sees a real credential.
 */
internal class FakeBackend : AutoCloseable {
    val requests: MutableList<String> = Collections.synchronizedList(mutableListOf())
    /** When set, queue reads block until it is counted down: a poll stays "in flight". */
    @Volatile var holdQueue: CountDownLatch? = null
    @Volatile var queueVotes = 4L
    private val server = ServerSocket(8080, 50, InetAddress.getByName("127.0.0.1")).apply { reuseAddress = true }
    private val worker = thread(isDaemon = true) {
        while (!server.isClosed) {
            val socket = runCatching { server.accept() }.getOrNull() ?: break
            // A cancelled client request closes its socket mid-response; that is not a test failure.
            thread(isDaemon = true) { runCatching { socket.use(::serve) } }
        }
    }

    fun count(prefix: String): Int = synchronized(requests) { requests.count { it.startsWith(prefix) } }

    private fun serve(socket: Socket) {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
        val line = reader.readLine() ?: return
        var length = 0
        while (true) {
            val header = reader.readLine() ?: break
            if (header.isEmpty()) break
            if (header.startsWith("Content-Length:", ignoreCase = true)) length = header.substringAfter(':').trim().toInt()
        }
        if (length > 0) CharArray(length).let { reader.read(it, 0, length) }
        val (method, target) = line.split(" ").let { it[0] to it[1] }
        requests += "$method $target"
        val body = route(method, target.substringBefore('?'))
        val bytes = body.toByteArray(Charsets.UTF_8)
        socket.getOutputStream().apply {
            write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n").toByteArray())
            write(bytes); flush()
        }
    }

    private fun route(method: String, path: String): String = when {
        path == "/api/participante/sesion" ->
            """{"establecimiento":{"nombre":"La Sala Azul"},"seudonimo":"p-6e8d8c","servicio":"ABIERTO"}"""
        path == "/api/public/establecimientos/sala-azul" ->
            """{"nombre":"La Sala Azul","qrSlug":"sala-azul","servicio":"ABIERTO","puedeParticipar":true}"""
        path == "/api/participante/cola" -> {
            holdQueue?.await(20, TimeUnit.SECONDS)
            // Same version on every read: equal versions must still be accepted.
            """{"colaVersion":1,"estado":"ABIERTO","enReproduccion":null,"cola":[
              {"solicitudId":"$REQUEST_ID","posicion":1,"votos":$queueVotes,"cancion":$LUNA,"esperaEstimadaSegundos":0,"votado":false}]}"""
        }
        method == "POST" && path == "/api/participante/solicitudes/$REQUEST_ID/votos" ->
            """{"solicitudId":"$REQUEST_ID","votos":5,"votado":true,"posicion":1,"creada":false}"""
        method == "POST" && path == "/api/participante/solicitudes" ->
            """{"solicitudId":"00000000-0000-4000-8000-0000000000bb","votos":1,"votado":true,"posicion":2,"creada":true}"""
        path == "/api/participante/catalogo/busqueda" -> """{"resultados":[$AIRE_CATALOG],"siguientePagina":null}"""
        path == "/api/participante/catalogo/sugerencias" -> """{"sugerencias":[]}"""
        else -> "{}"
    }

    override fun close() { server.close(); worker.join(2_000) }

    companion object {
        const val BASE = "http://127.0.0.1:8080"
        const val REQUEST_ID = "00000000-0000-4000-8000-0000000000aa"
        private const val LUNA = """{"videoId":"Abcdef01_9-","titulo":"Luna azul","artista":"Estudio Norte","artistaVerificado":true,"duracionSegundos":214,"miniaturaUrl":null}"""
        private const val AIRE_CATALOG = """{"videoId":"Aire0001_9-","titulo":"Aire de octubre","artista":"Valle","artistaVerificado":true,"canal":"Valle","duracionSegundos":227,"miniaturaUrl":null}"""

        fun session() = ParticipantSession("synthetic-test-only", "2099-01-01T00:00:00Z", "p-6e8d8c", "La Sala Azul",
            "ABIERTO", "sala-azul")
    }
}
