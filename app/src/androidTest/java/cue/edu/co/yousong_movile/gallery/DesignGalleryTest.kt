package cue.edu.co.yousong_movile.gallery

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import cue.edu.co.yousong_movile.*
import cue.edu.co.yousong_movile.R
import cue.edu.co.yousong_movile.ui.icons.YouSongIcons
import cue.edu.co.yousong_movile.ui.theme.Spacing
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

/**
 * Design gallery: renders every screen and state with synthetic data into PNGs under
 * `Android/data/cue.edu.co.yousong_movile/files/gallery/after`. Run on demand with
 * `adb shell am instrument -e class cue.edu.co.yousong_movile.gallery.DesignGalleryTest ...`.
 */
class DesignGalleryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun captureAll() {
        val folder = InstrumentationRegistry.getArguments().getString("gallery_folder") ?: "after"
        val gallery = Gallery(compose, folder)
        val offline = "No pudimos conectar con el local. Revisa tu conexión e inténtalo de nuevo."
        for (dark in listOf(true, false)) {
            val mode = if (dark) "dark" else "light"
            fun shot(name: String, frames: List<Frame> = PhoneFrames, body: @androidx.compose.runtime.Composable () -> Unit) =
                gallery.shot("$name-$mode", frames) { GalleryTheme(dark, body) }

            shot("01-inicio", PhoneFrames + SmallFrame + TabletFrame) {
                GalleryShell(Tab.Inicio, Samples.queue) { Home(Samples.queue) }
            }
            shot("01b-inicio-carruseles", PhoneFrames + TabletFrame) {
                GalleryShell(Tab.Inicio, Samples.queue) { Home(Samples.queue, scrolledTo = 2) }
            }
            shot("01c-inicio-locales") {
                GalleryShell(Tab.Inicio, Samples.queue) { Home(Samples.queue, scrolledTo = 4) }
            }
            shot("01d-inicio-sin-reproduccion") {
                GalleryShell(Tab.Inicio, Samples.queueNoPlaying) { Home(Samples.queueNoPlaying) }
            }
            shot("02-inicio-vacio") {
                GalleryShell(Tab.Inicio, Samples.empty) {
                    Home(Samples.empty, highlights = Feed.Ready(Highlights(30, emptyList(), emptyList(), emptyList(), emptyList())))
                }
            }
            shot("02b-inicio-cargando") {
                GalleryShell(Tab.Inicio, Samples.queue) { Home(Samples.queue, Feed.Loading, Feed.Loading, scrolledTo = 1) }
            }
            shot("02c-inicio-error") {
                GalleryShell(Tab.Inicio, Samples.queue) {
                    Home(Samples.queue, Feed.Failed("La cuota del catálogo se agotó. Inténtalo más tarde."), Feed.Failed(offline),
                        scrolledTo = 1)
                }
            }
            shot("02d-inicio-sin-conexion") {
                GalleryShell(Tab.Inicio, null) {
                    HomeScreen("La Sala Azul", Samples.ui(null, error = offline), Greeting.Night, Feed.Loading, Feed.Loading, HomeActions())
                }
            }
            shot("03-buscar-explorar", PhoneFrames + TabletFrame) { GalleryShell(Tab.Buscar, Samples.queue) { Search("", SearchPhase.Idle) } }
            shot("03b-explorar-sin-tendencias") {
                GalleryShell(Tab.Buscar, Samples.queue) { Search("", SearchPhase.Idle, tiles = genreTiles(emptyList())) }
            }
            shot("04-buscar-cargando") { GalleryShell(Tab.Buscar, Samples.queue) { Search("Aire", SearchPhase.Loading) } }
            shot("05-buscar-sugerencias") {
                GalleryShell(Tab.Buscar, Samples.queue) { Search("Aire", SearchPhase.Loading, Samples.suggestions) }
            }
            shot("06-buscar-resultados", PhoneFrames + TabletFrame) {
                GalleryShell(Tab.Buscar, Samples.queue) {
                    Search("Aire", SearchPhase.Results(Samples.songs, hasMore = true, loadingMore = false), pending = Samples.songs[2].videoId)
                }
            }
            shot("07-buscar-sin-resultados") {
                GalleryShell(Tab.Buscar, Samples.queue, snack = "Voto registrado para «Luna azul».") {
                    Search("NoExisteEnDemo", SearchPhase.Empty)
                }
            }
            shot("08-buscar-error") { GalleryShell(Tab.Buscar, Samples.queue) { Search("Aire", SearchPhase.Failed(offline)) } }
            shot("09-detalle") {
                InlineSheet { SongDetailContent(Samples.songs[4].copy(videoId = "nuevaxxxxxx"), SongCta.Request, false) {} }
            }
            shot("10-detalle-votar") {
                InlineSheet { SongDetailContent(Samples.catalog(Samples.orbita), SongCta.Vote, false) {} }
            }
            shot("11-confirmacion") {
                GalleryShell(Tab.Buscar, Samples.queue) {
                    ConfirmationScreen(Confirmation(ActionTarget("x", "Aire de octubre", "Valle", "fake://aire"), 4), {}, {})
                }
            }
            shot("12-cola", PhoneFrames + SmallFrame + TabletFrame) {
                GalleryShell(Tab.Cola, Samples.queue, snack = "Voto registrado para «Luna azul».") {
                    QueueScreen(Samples.ui(Samples.queue, pending = Samples.items[2].requestId), {}, {}, {})
                }
            }
            shot("12b-cola-estados") {
                val ui = Samples.ui(Samples.queue, pending = Samples.items[2].requestId)
                Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Samples.items.forEachIndexed { index, item ->
                        QueueRow(item, ui.voteStateFor(item.voted, item.requestId), head = index == 0, onVote = {})
                    }
                }
            }
            shot("12c-cola-pausada-filas") {
                val ui = Samples.ui(Samples.paused)
                Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Samples.items.take(4).forEachIndexed { index, item ->
                        QueueRow(item, ui.voteStateFor(item.voted, item.requestId), head = index == 0, onVote = {})
                    }
                }
            }
            shot("13-cola-sin-reproduccion") {
                GalleryShell(Tab.Cola, Samples.queueNoPlaying) { QueueScreen(Samples.ui(Samples.queueNoPlaying), {}, {}, {}) }
            }
            shot("14-cola-pausa") { GalleryShell(Tab.Cola, Samples.paused) { QueueScreen(Samples.ui(Samples.paused), {}, {}, {}) } }
            shot("15-cola-vacia") { GalleryShell(Tab.Cola, Samples.empty) { QueueScreen(Samples.ui(Samples.empty), {}, {}, {}) } }
            shot("16-sin-conexion") {
                GalleryShell(Tab.Cola, null) { QueueScreen(Samples.ui(null, error = offline), {}, {}, {}) }
            }
            shot("17-noche-termino") { SessionEndedScreen(SESSION_CLOSED_NOTICE) {} }
            shot("18-pausa") {
                FullScreenState(YouSongIcons.Pause, stringResource(R.string.state_paused_title),
                    stringResource(R.string.state_paused_body), stringResource(R.string.action_refresh), {})
            }
            shot("19-mantenimiento") {
                FullScreenState(YouSongIcons.Maintenance, stringResource(R.string.state_maintenance_title),
                    stringResource(R.string.state_maintenance_body), stringResource(R.string.action_refresh), {})
            }
            shot("20-cerrado") {
                FullScreenState(YouSongIcons.Night, stringResource(R.string.state_closed_title),
                    stringResource(R.string.state_closed_body), stringResource(R.string.state_scan_again), {})
            }
            shot("21-camara") {
                FullScreenState(YouSongIcons.NoCamera, stringResource(R.string.state_camera_title),
                    stringResource(R.string.state_camera_body), stringResource(R.string.state_camera_action), {})
            }
            shot("22-entrada") { EntryScreen("https://api.example.test", true, null) {} }
            shot("23-entrada-local") {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                    EstablishmentCard(Samples.establishment, true) {}
                    PrivacySection(Samples.policy, f1 = true, f2 = false, enabled = true, onF1 = {}, onF2 = {})
                    Button(onClick = {}, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text(stringResource(R.string.join_action))
                    }
                }
            }
            shot("24-entrada-cerrado") {
                Column(Modifier.padding(Spacing.lg)) {
                    EstablishmentCard(Samples.establishment.copy(service = "CERRADO", canJoin = false,
                        nextOpening = "2026-11-14T23:00:00Z"), true) {}
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Search(query: String, phase: SearchPhase, suggestions: List<Suggestion> = emptyList(), pending: String? = null,
        tiles: List<GenreTile> = Samples.tiles) =
        SearchContent(query, {}, {}, suggestions, {}, phase, Samples.ui(Samples.queue, pending = pending), {}, {}, {}, {},
            tiles = tiles)

    @androidx.compose.runtime.Composable
    private fun Home(queue: QueueSnapshot, trends: Feed<Trends> = Samples.trends, highlights: Feed<Highlights> = Samples.highlights,
        scrolledTo: Int = 0) =
        HomeScreen("La Sala Azul", Samples.ui(queue), Greeting.Night, trends, highlights, HomeActions(),
            zone = java.util.TimeZone.getTimeZone("America/Bogota"),
            listState = androidx.compose.foundation.lazy.rememberLazyListState(scrolledTo))

    private fun Int.dp() = androidx.compose.ui.unit.Dp(this.toFloat())
}
