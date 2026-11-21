package cue.edu.co.yousong_movile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import cue.edu.co.yousong_movile.ui.theme.YousongmovileTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ScreensTest {
    @get:Rule val compose = createComposeRule()

    private val luna = QueueSong("Abcdef01_9-", "Luna azul", "Estudio Norte", true, 214, null)
    private val orbita = QueueSong("Bbcdef01_9-", "Órbita", "Satélite", true, 189, null)
    private fun item(position: Int, song: QueueSong, votes: Long, voted: Boolean?) =
        QueueItem("00000000-0000-4000-8000-00000000000$position", position, votes, song, 360, voted)
    private val snapshot = QueueSnapshot(1, ServiceState.ABIERTO, null,
        listOf(item(1, luna, 4, false), item(2, orbita, 1, true)))
    private fun ui(value: QueueSnapshot? = snapshot) = QueueUi(value, interactive = true, pendingId = null, loading = false, error = null)

    @Test fun pluralsReadNaturally() {
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
        assertEquals("1 voto", resources.getQuantityString(R.plurals.votes_count, 1, 1))
        assertEquals("3 votos", resources.getQuantityString(R.plurals.votes_count, 3, 3))
        assertEquals("1 canción", resources.getQuantityString(R.plurals.songs_count, 1, 1))
        assertEquals("8 canciones", resources.getQuantityString(R.plurals.songs_count, 8, 8))
    }

    @Test fun queueRowsAnnounceSongAndVotesAndOnlyVotableRowsAct() {
        val voted = mutableListOf<String>()
        compose.setContent { YousongmovileTheme { QueueScreen(ui(), { voted += it.song.title }, {}, {}) } }
        compose.onNodeWithText("Cola en vivo").assertExists()
        compose.onNodeWithText("2 canciones").assertExists()
        compose.onAllNodesWithText("Espera ~ 6 min", useUnmergedTree = true).assertCountEquals(2)
        compose.onNodeWithContentDescription("Votar por Luna azul, 4 votos").assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("Ya votaste por Órbita, 1 voto").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(listOf("Luna azul"), voted) }
    }

    @Test fun pausedQueueExplainsWhyVotingIsOff() {
        compose.setContent { YousongmovileTheme { QueueScreen(ui(snapshot.copy(state = ServiceState.PAUSADO)), {}, {}, {}) } }
        compose.onNodeWithText("En pausa").assertExists()
        compose.onNodeWithContentDescription("Votos en pausa para Luna azul").assertIsNotEnabled()
    }

    @Test fun offlineQueueOffersRetry() {
        var retried = false
        compose.setContent {
            YousongmovileTheme { QueueScreen(QueueUi(null, false, null, false, "Sin red"), {}, { retried = true }, {}) }
        }
        compose.onNodeWithText("Sin conexión con el local").assertExists()
        compose.onNodeWithText("Reintentar").performClick()
        compose.runOnIdle { assertTrue(retried) }
    }

    private val aire = Song("Cbcdef01_9-", "Aire de octubre", "Valle", true, "Valle", 227, null, "Salsa")
    private val trends = Feed.Ready(Trends("CO", "2026-11-21T03:00:00Z", listOf(aire,
        Song("Dbcdef01_9-", "Faro", "Costa Lenta", true, "Costa", 243, null, null))))
    private val highlights = Feed.Ready(Highlights(30, listOf(RankedSong(aire, 12)), emptyList(), emptyList(),
        listOf(RecentPlay(aire, "2026-11-21T03:41:00Z", false))))
    private val playing = PlayingSong(null, QueueSong("Ebcdef01_9-", "Neón", "Ciudad Baja", true, 200, null, "Pop"),
        "2026-11-21T03:00:00Z", 100, null)

    private fun home(snapshot: QueueSnapshot? = this.snapshot, trends: Feed<Trends> = this.trends,
        highlights: Feed<Highlights> = this.highlights, actions: HomeActions = HomeActions()) = compose.setContent {
        YousongmovileTheme { HomeScreen("La Sala Azul", ui(snapshot), Greeting.Night, trends, highlights, actions) }
    }

    private fun scrollTo(matcher: SemanticsMatcher) =
        compose.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(matcher)

    @Test fun homeGreetsShowsTheHeroAndVotesForWhatIsNext() {
        val voted = mutableListOf<String>()
        home(snapshot.copy(playing = playing), actions = HomeActions(onVote = { voted += it.song.title }))
        compose.onNodeWithText("Buenas noches").assertExists()
        compose.onNodeWithText("La Sala Azul").assertExists()
        compose.onNodeWithText("Sonando ahora", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Ciudad Baja · Pop · 3:20", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Sigue", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Votar por Luna azul, 4 votos").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf("Luna azul"), voted) }
    }

    @Test fun trendsCarouselRanksSongsAndOpensTheDetail() {
        val opened = mutableListOf<Song>()
        home(actions = HomeActions(onOpen = { opened += it }))
        scrollTo(hasText("Tendencias en Colombia"))
        compose.onNodeWithText("Tendencias en Colombia").assertExists()
        compose.onNodeWithContentDescription("Puesto 1 en Colombia", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Salsa · 3:47", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Puesto 2 en Colombia", useUnmergedTree = true).assertExists()
        compose.onAllNodes(hasText("Aire de octubre") and hasClickAction())[0].performClick()
        compose.runOnIdle { assertEquals("Aire de octubre", opened.single().title) }
    }

    @Test fun localRankingsShowVotesAndWhenSongsPlayed() {
        home()
        scrollTo(hasText("Lo más votado en La Sala Azul"))
        compose.onNodeWithText("12 votos", useUnmergedTree = true).assertExists()
        scrollTo(hasText("Últimas que sonaron"))
        compose.onNodeWithText("Sonó a las", substring = true, useUnmergedTree = true).assertExists()
    }

    @Test fun aFailingTrendsListShowsAQuietRetryAndTheRestStillWorks() {
        var retried = 0
        home(trends = Feed.Failed("La cuota del catálogo se agotó. Inténtalo más tarde."),
            actions = HomeActions(onRetryTrends = { retried++ }))
        compose.onNodeWithContentDescription("Votar por Luna azul, 4 votos").assertExists()
        scrollTo(hasText("No pudimos cargar esta lista"))
        compose.onNodeWithText("La cuota del catálogo se agotó. Inténtalo más tarde.").assertExists()
        compose.onNodeWithText("Reintentar").performClick()
        compose.runOnIdle { assertEquals(1, retried) }
        scrollTo(hasText("Lo más votado en La Sala Azul"))
    }

    @Test fun loadingAndEmptyCarouselsAreCalmAndUnavailableRankingsAreHidden() {
        home(trends = Feed.Loading, highlights = Feed.Unavailable)
        scrollTo(hasContentDescription("Cargando Tendencias en Colombia"))
        compose.onNodeWithText("Lo más votado", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Últimas que sonaron").assertDoesNotExist()
    }

    @Test fun emptyHomeInvitesTheFirstRequest() {
        var searched = false
        home(QueueSnapshot(1, ServiceState.ABIERTO, null, emptyList()), trends = Feed.Ready(Trends("CO", "2026-11-21T03:00:00Z",
            emptyList())), actions = HomeActions(onSearch = { searched = true }))
        compose.onNodeWithText("Aún no suena nada").assertExists()
        compose.onNodeWithText("Buscar canciones").performClick()
        compose.runOnIdle { assertTrue(searched) }
        scrollTo(hasText("YouTube aún no publica las tendencias de hoy.", substring = true))
    }

    @Test fun exploreTilesRunASearchForTheirGenre() {
        val genres = mutableListOf<String>()
        compose.setContent {
            YousongmovileTheme {
                SearchContent("", {}, {}, emptyList(), {}, SearchPhase.Idle, ui(), {}, {}, {}, {},
                    tiles = genreTiles(listOf(aire)), onGenre = { genres += it })
            }
        }
        compose.onNodeWithText("Explorar").assertExists()
        compose.onNodeWithText("Salsa").performClick()
        compose.onNodeWithText("Vallenato").assertExists()
        compose.runOnIdle { assertEquals(listOf("Salsa"), genres) }
    }

    @Test fun resultRowsShowArtistGenreAndDurationAndQuickRequest() {
        val quick = mutableListOf<Song>()
        compose.setContent {
            YousongmovileTheme {
                SearchContent("Aire", {}, {}, emptyList(), {}, SearchPhase.Results(listOf(aire), false, false), ui(), {},
                    { quick += it }, {}, {})
            }
        }
        compose.onNodeWithText("Valle · Salsa · 3:47", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Pedir Aire de octubre").performClick()
        compose.runOnIdle { assertEquals(listOf(aire), quick) }
    }

    @Test fun queueRowsShowTheGenreWhenKnown() {
        val withGenre = snapshot.copy(items = listOf(item(1, luna.copy(genre = "Cumbia"), 4, false)))
        compose.setContent { YousongmovileTheme { QueueScreen(ui(withGenre), {}, {}, {}) } }
        compose.onNodeWithContentDescription("Género: Cumbia", useUnmergedTree = true).assertExists()
    }

    @Test fun searchFieldClearsAndSubmitsFromTheKeyboard() {
        var submitted = 0
        compose.setContent {
            var query by remember { mutableStateOf("Aire") }
            YousongmovileTheme {
                SearchContent(query, { query = it }, { submitted++ }, emptyList(), {}, SearchPhase.Empty, ui(), {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("No encontramos esa canción").assertExists()
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.runOnIdle { assertEquals(1, submitted) }
        compose.onNodeWithContentDescription("Borrar búsqueda").performClick()
        compose.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText,
            androidx.compose.ui.text.AnnotatedString("")))
    }

    @Test fun suggestionsSeparateSongsFromSearchPhrases() {
        val song = Song("Abcdef01_9-", "Aire de octubre", "Valle", true, "Canal", 227, null)
        val picked = mutableListOf<Suggestion>()
        compose.setContent {
            YousongmovileTheme {
                SearchContent("Aire", {}, {}, listOf(Suggestion("Aire de octubre", song), Suggestion("aire acústico", null)),
                    { picked += it }, SearchPhase.Loading, ui(), {}, {}, {}, {})
            }
        }
        compose.onNodeWithContentDescription("Canción: Aire de octubre").assertExists()
        compose.onNodeWithContentDescription("Buscar: aire acústico").performClick()
        compose.runOnIdle { assertEquals("aire acústico", picked.single().text) }
    }

    @Test fun detailShowsTheGenreChip() {
        compose.setContent { YousongmovileTheme { SongDetailContent(aire, SongCta.Request, false) {} } }
        compose.onNodeWithContentDescription("Género: Salsa", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Aire de octubre").assertExists()
    }

    @Test fun detailPrimaryActionNamesWhatHappens() {
        val song = Song("Abcdef01_9-", "Luna azul", "Estudio Norte", false, "Canal", 214, null)
        var cta by mutableStateOf(SongCta.Request)
        var acted = 0
        compose.setContent { YousongmovileTheme { SongDetailContent(song, cta, false) { acted++ } } }
        compose.onNodeWithText("Artista según el canal de YouTube").assertExists()
        compose.onNodeWithText("Video", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Dura 3:34", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Pedir esta canción").performClick()
        cta = SongCta.Vote
        compose.onNodeWithText("Votar por esta canción").assertIsEnabled()
        cta = SongCta.Voted
        compose.onNodeWithText("Ya votaste por esta canción").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, acted) }
    }

    @Test fun consentRowsToggleAsOneTarget() {
        val policy = PrivacyPolicy("3", listOf(Purpose("F1", "Participar"), Purpose("F2", "Mejorar")))
        compose.setContent {
            var f1 by remember { mutableStateOf(false) }
            YousongmovileTheme { PrivacySection(policy, f1, false, true, { f1 = it }, {}) }
        }
        compose.onNodeWithText("Necesario para participar").assertIsOff().performClick().assertIsOn()
        compose.onNodeWithText("Opcional: ayudarnos a mejorar").assertIsOff()
        compose.onNodeWithText("F1", substring = true).assertDoesNotExist()
    }

    @Test fun establishmentShowsFriendlyStateNotTheRawEnum() {
        compose.setContent {
            YousongmovileTheme { EstablishmentCard(Establishment("La Sala Azul", "sala-azul", "PAUSADO", false), true) {} }
        }
        compose.onNodeWithText("En pausa").assertExists()
        compose.onNodeWithText("PAUSADO").assertDoesNotExist()
    }

    @Test fun sessionEndedOffersAWayBack() {
        var again = false
        compose.setContent { YousongmovileTheme { SessionEndedScreen(SESSION_CLOSED_NOTICE) { again = true } } }
        compose.onNodeWithText("La noche terminó").assertExists()
        compose.onNodeWithText("Escanear otro código").performClick()
        compose.runOnIdle { assertTrue(again) }
    }
}
