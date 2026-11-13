package cue.edu.co.yousong_movile.gallery

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import cue.edu.co.yousong_movile.*
import cue.edu.co.yousong_movile.ui.theme.SheetShape
import cue.edu.co.yousong_movile.ui.theme.Spacing
import cue.edu.co.yousong_movile.ui.theme.YousongmovileTheme

/** Synthetic, offline sample data for the design gallery. No network, no credentials. */
internal object Samples {
    private fun song(id: String, title: String, artist: String, seconds: Int?, verified: Boolean = true) =
        QueueSong(id.padEnd(11, 'x').take(11), title, artist, verified, seconds, "fake://$id")

    val luna = song("luna", "Luna azul", "Estudio Norte", 214)
    val orbita = song("orbita", "Órbita", "Satélite", 189)
    val faro = song("faro", "Faro", "Costa Lenta", 243)
    val aire = song("aire", "Aire de octubre", "Valle", 227)
    private val extra = listOf(
        song("noche", "Lo que queda de la noche cuando cierran el bar y alguien pide otra", "Los Amigos de Siempre", 301),
        song("marea", "Marea", "Sal y Humo", 176, verified = false),
        song("neon", "Neón", "Ciudad Baja", null),
        song("vuelta", "Vuelta a casa", "Ruta 9", 258),
    )

    private fun item(index: Int, song: QueueSong, votes: Long, voted: Boolean?) = QueueItem(
        "00000000-0000-4000-8000-00000000000$index", index, votes, song, (index - 1) * 210L, voted)

    val items = listOf(item(1, luna, 5, true), item(2, orbita, 3, false), item(3, faro, 2, false)) +
        extra.mapIndexed { i, s -> item(i + 4, s, (2 - i % 2).toLong(), if (i == 1) null else false) } +
        item(8, aire, 0, false)

    val playing = PlayingSong("00000000-0000-4000-8000-000000000099", aire, "2026-11-13T21:00:00Z", 131, true)
    val queue = QueueSnapshot(12, ServiceState.ABIERTO, playing, items)
    val queueNoPlaying = queue.copy(playing = null)
    val empty = QueueSnapshot(3, ServiceState.ABIERTO, null, emptyList())
    val paused = queue.copy(state = ServiceState.PAUSADO)

    fun catalog(q: QueueSong) = Song(q.videoId, q.title, q.artist, q.verified, "Canal ${q.artist}", q.durationSeconds, q.thumbnailUrl)
    val songs = (listOf(aire, luna, orbita, faro) + extra).map(::catalog)
    val suggestions = listOf(Suggestion("Aire de octubre", catalog(aire)), Suggestion("aire acústico", null),
        Suggestion("aires de fiesta", null))

    val establishment = Establishment("La Sala Azul", "sala-azul", "ABIERTO", true)
    val policy = PrivacyPolicy("2026.11", listOf(
        Purpose("F1", "Usar un alias anónimo para pedir canciones y votar en este local."),
        Purpose("F2", "Responder preguntas breves sobre cómo te fue con la app."),
        Purpose("R1", "Tus votos se borran cuando termina la noche."),
    ))

    fun ui(snapshot: QueueSnapshot?, pending: String? = null, interactive: Boolean = true, error: String? = null) =
        QueueUi(snapshot, interactive, pending, false, error)
}

/** Paints a quiet geometric cover so screenshots show real image framing. */
internal val FakeCoverLoader: suspend (String) -> ImageBitmap? = { url ->
    val palettes = listOf(intArrayOf(0xFFB9C9AE.toInt(), 0xFF2F4A3A.toInt(), 0xFFE7EEDC.toInt()),
        intArrayOf(0xFFCBD6E6.toInt(), 0xFF3A4A6A.toInt(), 0xFFF2F4EE.toInt()),
        intArrayOf(0xFFE0A77A.toInt(), 0xFF5A3A2A.toInt(), 0xFFF4E4D0.toInt()),
        intArrayOf(0xFFC9B8D8.toInt(), 0xFF3E3352.toInt(), 0xFFEFE8F4.toInt()))
    val colors = palettes[Math.floorMod(url.hashCode(), palettes.size)]
    val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
    Canvas(bitmap).apply {
        drawColor(colors[0])
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors[1] }
        drawRect(RectF(28f, 64f, 228f, 228f), paint)
        paint.color = colors[2]
        drawArc(RectF(52f, 40f, 204f, 192f), 0f, 180f, true, paint)
        paint.color = colors[1]; paint.strokeWidth = 5f
        drawLine(78f, 200f, 178f, 200f, paint)
    }
    bitmap.asImageBitmap()
}

@Composable
internal fun GalleryTheme(dark: Boolean, content: @Composable () -> Unit) {
    YousongmovileTheme(darkTheme = dark) {
        CompositionLocalProvider(LocalCoverLoader provides FakeCoverLoader) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, content = content)
        }
    }
}

/** The connected shell without networking: top bar, optional now-playing bar, navigation. */
@Composable
internal fun GalleryShell(tab: Tab, snapshot: QueueSnapshot?, venue: String? = "La Sala Azul", snack: String? = null,
    content: @Composable () -> Unit) {
    val host = remember { SnackbarHostState() }
    if (snack != null) LaunchedEffect(snack) { host.showSnackbar(Notice(snack, success = true)) }
    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        topBar = { ShellTopBar(if (tab == Tab.Inicio) null else venue, false, false, {}, {}) },
        snackbarHost = { YouSongSnackbarHost(host) },
        bottomBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.background)) {
                snapshot?.playing?.let { NowPlayingBar(it, {}, Modifier.readableWidthCentered()) }
                ShellNavigation(tab, snapshot?.items?.size ?: 0) {}
            }
        }) { padding -> Box(Modifier.padding(padding).fillMaxSize()) { content() } }
}

/** Bottom sheet rendered inline (a real ModalBottomSheet lives in a popup the capture cannot see). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InlineSheet(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)), contentAlignment = Alignment.BottomCenter) {
        Surface(Modifier.fillMaxWidth(), shape = SheetShape, color = MaterialTheme.colorScheme.surfaceContainer) {
            Column {
                Box(Modifier.fillMaxWidth().padding(vertical = Spacing.xxs), contentAlignment = Alignment.Center) {
                    BottomSheetDefaults.DragHandle()
                }
                content()
            }
        }
    }
}
