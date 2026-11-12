package cue.edu.co.yousong_movile

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Requires Android JSON; compiled tests are not device execution evidence. */
@RunWith(AndroidJUnit4::class)
class QueueParsingTest {
    private val song = """{"videoId":"Abcdef01_9-","titulo":"Aire","artista":"Valle","artistaVerificado":true,"duracionSegundos":null,"miniaturaUrl":null}"""

    @Test fun parsesParticipantQueueWithoutCatalogChannelOrInventedOrdering() {
        val result = parseQueue(JSONObject("""{"colaVersion":7,"estado":"ABIERTO",
          "enReproduccion":{"solicitudId":null,"cancion":$song,"iniciadaAt":"2026-06-01T00:00:00Z","restanteSegundos":92,"votado":false},
          "cola":[{"solicitudId":"request-b","posicion":2,"votos":9,"cancion":$song,"esperaEstimadaSegundos":125,"votado":true},
          {"solicitudId":"request-a","posicion":1,"votos":2,"cancion":$song,"esperaEstimadaSegundos":92,"votado":false}]}"""))
        assertEquals(7L, result.version)
        assertEquals(ServiceState.ABIERTO, result.state)
        assertNull(result.playing?.requestId)
        assertEquals(92L, result.playing?.remainingSeconds)
        assertEquals(listOf("request-b", "request-a"), result.items.map { it.requestId })
        assertEquals(125L, result.items[0].waitSeconds)
        assertEquals(true, result.items[0].voted)
        assertEquals(false, result.items[1].voted)
        assertNull(result.items[0].song.durationSeconds)
        assertNull(result.items[0].song.thumbnailUrl)
    }

    @Test fun missingParticipantVoteIsUnknownNotFalse() {
        val result = parseQueue(JSONObject("""{"colaVersion":1,"estado":"PAUSADO","enReproduccion":null,
          "cola":[{"solicitudId":"r","posicion":1,"votos":0,"cancion":$song,"esperaEstimadaSegundos":0}]}"""))
        assertNull(result.items.single().voted)
        assertFalse(canVote(ServiceState.ABIERTO, result.items.single().voted, false))
    }

    @Test fun readsBothMutationAcknowledgmentsIncludingNullPosition() {
        val created = parseSupport(JSONObject("""{"solicitudId":"r","votos":1,"votado":true,"posicion":1,"creada":true}"""))
        val vote = parseSupport(JSONObject("""{"solicitudId":"r","votos":2,"votado":true,"posicion":null,"creada":false}"""))
        assertTrue(created.created)
        assertEquals(1, created.position)
        assertEquals(2L, vote.votes)
        assertTrue(vote.voted)
        assertNull(vote.position)
        assertFalse(vote.created)
    }

    @Test fun suggestionsUseCatalogMetadataOrSearchText() {
        val catalogSong = JSONObject(song).put("canal", "Valle oficial")
        val result = parseSuggestions(JSONObject("""{"sugerencias":[{"tipo":"CANCION","texto":"Aire","cancion":$catalogSong},{"tipo":"BUSQUEDA","texto":"Aire vivo"}]}"""))
        assertEquals("Valle oficial", result[0].song?.channel)
        assertEquals("Aire vivo", result[1].text)
        assertNull(result[1].song)
        // Shared song parser still preserves search pagination and nullable metadata.
        val page = parseSearch(JSONObject("""{"resultados":[$catalogSong],"siguientePagina":"abc="}"""))
        assertEquals("abc=", page.nextPage)
        assertEquals(result[0].song, page.songs.single())
    }
}
