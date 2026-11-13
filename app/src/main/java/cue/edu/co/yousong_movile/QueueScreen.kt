package cue.edu.co.yousong_movile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Display server order, positions and estimates verbatim; never simulate a player. */
@Composable
internal fun QueueScreen(actions: SongActions) {
    Text("Canciones en cola", style = MaterialTheme.typography.headlineSmall)
    if (actions.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    TextButton(onClick = actions::refresh, enabled = !actions.loading && !actions.pending) { Text("Actualizar cola") }
    val queue = actions.snapshot
    if (queue == null && !actions.loading && actions.error == null) Text("La cola todavía no está disponible.")
    queue?.let {
        Text("Estado: ${it.state} · Versión ${it.version}")
        if (it.state == ServiceState.PAUSADO) Text("El local no acepta solicitudes ni votos mientras está pausado.")
        Text("Ahora en reproducción", style = MaterialTheme.typography.titleLarge)
        val playing = it.playing
        if (playing == null) Text("El local no tiene una reproducción activa.")
        else QueueSongCard(playing.song) {
            Text("Restante informado: ${playing.remainingSeconds} s")
            Text("Inicio: ${playing.startedAt}")
            playing.requestId?.let { id ->
                VoteButton(actions, id, playing.voted, null)
            }
        }
        Text("La reproducción la controla el operador; esta aplicación no emite audio.")
        if (it.items.isEmpty()) Text("No hay canciones en espera. Busca una canción para solicitarla.")
        it.items.forEach { item ->
            QueueSongCard(item.song) {
                Text("Posición ${item.position} · ${item.votes} votos")
                Text("Espera estimada por el local: ${item.waitSeconds} s")
                VoteButton(actions, item.requestId, item.voted, item.position)
            }
        }
    }
}

@Composable
private fun QueueSongCard(song: QueueSong, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SongThumbnail(song.thumbnailUrl, song.title, Modifier.fillMaxWidth().height(140.dp))
            Text(song.title, style = MaterialTheme.typography.titleMedium)
            Text(song.artist)
            Text(if (song.verified) "Artista verificado" else "Artista identificado a partir del canal")
            Text(durationLabel(song.durationSeconds))
            content()
        }
    }
}

@Composable
private fun VoteButton(actions: SongActions, id: String, voted: Boolean?, position: Int?) {
    Button(onClick = { actions.vote(id, voted) },
        enabled = actions.canSupport(voted),
        modifier = Modifier.heightIn(min = 48.dp)) {
        Text(when {
            voted == true -> "Ya votaste"
            voted == null -> "Voto no disponible: actualiza la cola"
            actions.pending -> "Procesando…"
            position == null -> "Votar canción en reproducción"
            else -> "Votar canción en posición $position"
        })
    }
}
