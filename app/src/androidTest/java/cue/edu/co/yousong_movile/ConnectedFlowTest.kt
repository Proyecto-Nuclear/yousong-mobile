package cue.edu.co.yousong_movile

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import cue.edu.co.yousong_movile.ui.theme.YousongmovileTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch

/** The connected shell against a local fake backend: outcomes, confirmation and search budget. */
class ConnectedFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var backend: FakeBackend

    @Before fun setUp() { backend = FakeBackend() }

    private fun start() = compose.setContent {
        YousongmovileTheme { ConnectedScreen(ParticipantApi(FakeBackend.BASE), FakeBackend.session(), null) {} }
    }

    @After fun tearDown() { backend.holdQueue?.countDown(); backend.close() }

    private fun waitForText(text: String, substring: Boolean = false) = compose.waitUntil(15_000) {
        compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitForDescription(text: String) = compose.waitUntil(15_000) {
        compose.onAllNodes(hasContentDescription(text)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun searches() = backend.count("GET /api/participante/catalogo/busqueda")

    @Test fun votingShowsASnackbarNamingTheSong() {
        start()
        waitForDescription("Votar por Luna azul, 4 votos")
        compose.onAllNodes(hasContentDescription("Votar por Luna azul, 4 votos"))[0].performClick()
        waitForText("Voto registrado para «Luna azul».")
        assertEquals(1, backend.count("POST"))
    }

    @Test fun aTapRefusedDuringAPollExplainsWhatHappened() {
        start()
        waitForDescription("Votar por Luna azul, 4 votos")
        compose.waitForIdle()
        val hold = CountDownLatch(1)
        backend.holdQueue = hold
        compose.onNodeWithContentDescription("Actualizar la cola").performClick()
        compose.onAllNodes(hasContentDescription("Votar por Luna azul, 4 votos"))[0].performClick()
        waitForText("Un momento, estamos actualizando la cola. Inténtalo de nuevo.")
        assertEquals(0, backend.count("POST"))
        backend.holdQueue = null
        hold.countDown()
    }

    @Test fun searchRunsOnlyOnSubmitAndSurvivesTheRequestConfirmation() {
        start()
        waitForDescription("Votar por Luna azul, 4 votos")
        compose.onNodeWithText("Buscar").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Aire")
        compose.mainClock.advanceTimeBy(1_500)
        Thread.sleep(500)
        assertEquals("typing alone must not search", 0, searches())
        compose.onNode(hasSetTextAction()).performImeAction()
        waitForText("Aire de octubre")
        assertEquals(1, searches())

        compose.onAllNodes(hasText("Aire de octubre"))[0].performClick()
        waitForText("Pedir esta canción")
        compose.onNodeWithText("Pedir esta canción").performClick()
        waitForText("Ya está en la cola.")
        compose.onNodeWithText("Va en el puesto 2", substring = true).assertExists()

        compose.onNodeWithText("Seguir buscando").performClick()
        waitForText("Aire de octubre")
        compose.onNode(hasSetTextAction()).assert(hasText("Aire"))

        // Leaving the tab and coming back keeps the results without paying for another search.
        compose.onNodeWithText("Inicio").performClick()
        compose.onNodeWithText("Buscar").performClick()
        waitForText("Aire de octubre")
        compose.waitForIdle()
        assertEquals(1, searches())
    }

    @Test fun leaveDialogShowsWhoYouAre() {
        start()
        waitForDescription("Salir del local")
        compose.onNodeWithContentDescription("Salir del local").performClick()
        waitForText("Saldrás como p-6e8d8c")
        compose.onNodeWithText("Salir").assertIsEnabled()
    }

    @Test fun statusErrorShowsBeforeTheFirstQueueLoads() {
        backend.close()
        // Nothing answers: status and queue both fail before any snapshot exists.
        start()
        compose.onNodeWithText("Buscar").performClick()
        waitForText("No pudimos conectar con el local", substring = true)
        compose.onAllNodes(hasText("Reintentar"))[0].assertExists()
    }

    @Test fun homeShowsColombianTrendsFromThePublicEndpointWithoutTheCredential() {
        start()
        waitForText("Calle 85")
        compose.onNodeWithText("Reggaetón · 3:32", useUnmergedTree = true).assertExists()
        compose.waitUntil(15_000) { backend.count("GET /api/public/establecimientos/sala-azul/destacadas?dias=30") == 1 }
        assertEquals(1, backend.count("GET /api/public/tendencias"))
        assertTrue(backend.authorized.none { it.startsWith("/api/public/") })
        assertTrue(backend.authorized.contains("/api/participante/cola"))
    }

    @Test fun failingTrendsLeaveTheQueueUsableAndRetryOnRequest() {
        backend.trendsUp = false
        start()
        waitForText("No pudimos cargar esta lista")
        compose.onAllNodes(hasContentDescription("Votar por Luna azul, 4 votos"))[0].assertIsEnabled()
        backend.trendsUp = true
        compose.onAllNodes(hasText("Reintentar"))[0].performClick()
        waitForText("Calle 85")
        assertEquals(2, backend.count("GET /api/public/tendencias"))
    }
}
