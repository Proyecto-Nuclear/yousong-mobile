package cue.edu.co.yousong_movile

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Android's real org.json: trends, local rankings and the optional `genero` on every song shape. */
@RunWith(AndroidJUnit4::class)
class DiscoveryParsingTest {
    private val catalog = """{"videoId":"Abcdef01_9-","titulo":"Aire","artista":"Valle","artistaVerificado":true,
        "canal":"Valle oficial","duracionSegundos":227,"miniaturaUrl":"https://img.example/a.jpg","genero":"Salsa"}"""
    private val queueShape = """{"videoId":"Bbcdef01_9-","titulo":"Luna","artista":"Norte","artistaVerificado":false,
        "duracionSegundos":null,"miniaturaUrl":null}"""

    @Test fun trendsReadSongsWithAndWithoutGenreAndIgnoreUnknownFields() {
        val trends = parseTrends(JSONObject("""{"region":"CO","actualizadoAt":"2026-11-21T03:00:00.123456Z",
            "extra":1,"canciones":[$catalog,$queueShape]}"""))
        assertEquals("CO", trends.region)
        assertEquals(Song("Abcdef01_9-", "Aire", "Valle", true, "Valle oficial", 227, "https://img.example/a.jpg", "Salsa"),
            trends.songs[0])
        assertEquals(Song("Bbcdef01_9-", "Luna", "Norte", false, "", null, null, null), trends.songs[1])
    }

    @Test fun nullOrBlankGenreIsNoGenre() {
        listOf("null", "\"  \"").forEach { value ->
            val song = parseListedSong(JSONObject(catalog.replace("\"Salsa\"", value)))
            assertNull(song.genre)
        }
        assertEquals("Hip hop", parseListedSong(JSONObject(catalog.replace("\"Salsa\"", "\" Hip   hop \""))).genre)
    }

    @Test fun nonStringOrOversizedGenreRejectsTheResponse() {
        listOf("5", "{}", "[]", "\"${"x".repeat(61)}\"").forEach { value ->
            assertThrows(JSONException::class.java) { parseListedSong(JSONObject(catalog.replace("\"Salsa\"", value))) }
        }
    }

    @Test fun searchQueueAndSuggestionsCarryTheGenre() {
        assertEquals("Salsa", parseSearch(JSONObject("""{"resultados":[$catalog],"siguientePagina":null}""")).songs[0].genre)
        val queue = parseQueue(JSONObject("""{"colaVersion":1,"estado":"ABIERTO",
            "enReproduccion":{"solicitudId":null,"cancion":${catalog.replace("Abcdef01_9-", "Cbcdef01_9-")},
              "iniciadaAt":"2026-11-21T03:00:00Z","restanteSegundos":10},
            "cola":[{"solicitudId":"00000000-0000-4000-8000-0000000000aa","posicion":1,"votos":2,
              "cancion":$queueShape,"esperaEstimadaSegundos":0,"votado":false}]}"""))
        assertEquals("Salsa", queue.playing?.song?.genre)
        assertNull(queue.items[0].song.genre)
        val suggestions = parseSuggestions(JSONObject("""{"sugerencias":[{"tipo":"CANCION","texto":"Aire","cancion":$catalog}]}"""))
        assertEquals("Salsa", suggestions[0].song?.genre)
    }

    @Test fun trendsRejectBadShapesInsteadOfShowingAnything() {
        val songs = List(26) { catalog }.joinToString(",")
        listOf(
            """{"region":"CO","actualizadoAt":"2026-11-21T03:00:00Z","canciones":[$songs]}""",
            """{"region":"CO","actualizadoAt":"ayer","canciones":[]}""",
            """{"region":"","actualizadoAt":"2026-11-21T03:00:00Z","canciones":[]}""",
            """{"region":"CO","actualizadoAt":"2026-11-21T03:00:00Z"}""",
            """{"region":"CO","actualizadoAt":"2026-11-21T03:00:00Z","canciones":[{"videoId":"Abcdef01_9-"}]}""",
        ).forEach { body -> assertThrows(Exception::class.java) { parseTrends(JSONObject(body)) } }
        assertThrows(IllegalArgumentException::class.java) {
            parseTrends(JSONObject("""{"region":"CO","actualizadoAt":"2026-11-21T03:00:00Z",
                "canciones":[${catalog.replace("Abcdef01_9-", "../evil")}]}"""))
        }
    }

    @Test fun highlightsReadEveryRankingAndTheRecentPlays() {
        val highlights = parseHighlights(JSONObject("""{"periodoDias":30,
            "masVotadas":[{"cancion":$catalog,"votos":12}],
            "masPedidas":[{"cancion":$queueShape,"veces":3}],
            "masSonadas":[],
            "ultimasSonadas":[{"cancion":$queueShape,"sonoAt":"2026-11-21T03:41:00Z","enCurso":true}]}"""))
        assertEquals(30, highlights.periodDays)
        assertEquals(12L, highlights.mostVoted.single().count)
        assertEquals("Salsa", highlights.mostVoted.single().song.genre)
        assertEquals(3L, highlights.mostRequested.single().count)
        assertTrue(highlights.mostPlayed.isEmpty())
        assertEquals(RecentPlay(parseListedSong(JSONObject(queueShape)), "2026-11-21T03:41:00Z", true), highlights.recent.single())
    }

    @Test fun highlightsRejectBadShapes() {
        val eleven = List(11) { """{"cancion":$catalog,"votos":1}""" }.joinToString(",")
        val base = """"masPedidas":[],"masSonadas":[],"ultimasSonadas":[]"""
        listOf(
            """{"periodoDias":0,"masVotadas":[],$base}""",
            """{"periodoDias":30,"masVotadas":[$eleven],$base}""",
            """{"periodoDias":30,"masVotadas":[{"cancion":$catalog,"votos":-1}],$base}""",
            """{"periodoDias":30,"masVotadas":[{"cancion":$catalog}],$base}""",
            """{"periodoDias":30,"masVotadas":[],"masPedidas":[],"masSonadas":[],
                "ultimasSonadas":[{"cancion":$catalog,"sonoAt":"nunca","enCurso":false}]}""",
            """{"periodoDias":30,"masVotadas":[]}""",
        ).forEach { body -> assertThrows(JSONException::class.java) { parseHighlights(JSONObject(body)) } }
    }
}
