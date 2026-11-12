package cue.edu.co.yousong_movile

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

@Composable
internal fun SongDetail(song: Song, actions: SongActions, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Detalle de la canción") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SongThumbnail(song.thumbnailUrl, song.title, Modifier.fillMaxWidth().height(220.dp))
                Text(song.title, style = MaterialTheme.typography.headlineSmall)
                Text("${song.artist} · ${durationLabel(song.durationSeconds)}")
                Text(if (song.verified) "Artista verificado" else "Artista identificado a partir del canal")
                Text("Canal: ${song.channel}")
                Text("Video: ${song.videoId}")
                Text("Metadatos del catálogo conectado. Sin reproducción en la aplicación.")
                Text("Solicitar una canción registra tu primer voto; si ya está activa, la apoyas una vez por sesión.")
                actions.notice?.let { Text(it) }
                actions.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = { actions.request(song) }, enabled = actions.canRequest(song),
                    modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (actions.pending) "Procesando…" else "Solicitar o apoyar canción")
                }
                if (!actions.ready) Text("Actualiza la cola para habilitar nuevas acciones.")
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } })
}

@Composable
internal fun SongThumbnail(url: String?, title: String, modifier: Modifier = Modifier) {
    val image by produceState<ImageBitmap?>(null, url) {
        value = null
        // Thumbnails are anonymous, HTTPS-only, bounded and never inherit the participant bearer.
        value = withContext(Dispatchers.IO) { loadThumbnail(url) }
    }
    val bitmap = image
    if (bitmap != null) Image(bitmap, contentDescription = "Miniatura de $title", modifier = modifier, contentScale = ContentScale.Crop)
    else Surface(modifier, color = MaterialTheme.colorScheme.surfaceVariant) {
        Box(contentAlignment = Alignment.Center) { Text("Miniatura no disponible", Modifier.padding(12.dp)) }
    }
}

private fun loadThumbnail(raw: String?): ImageBitmap? {
    return runCatching {
        val uri = raw?.let { URI(it) } ?: return null
        if (uri.scheme != "https" || uri.host == null || uri.userInfo != null) return null
        val connection = URL(raw).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.useCaches = false
            if (connection.responseCode != 200) return null
            val bytes = connection.inputStream.use { it.readBytesBounded(2 * 1024 * 1024) }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth !in 1..4096 || bounds.outHeight !in 1..4096) return null
            val options = BitmapFactory.Options().apply {
                inSampleSize = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 512)
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
        } finally { connection.disconnect() }
    }.getOrNull()
}

internal fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(output.size() + count <= limit)
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
