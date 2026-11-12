package cue.edu.co.yousong_movile

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Uses Android's actual JSON implementation, not the host JVM's nonfunctional android.jar stubs. */
@RunWith(AndroidJUnit4::class)
class SearchParsingTest {
    @Test fun readsExactBackendNamesAndNullableMetadata() {
        val page = parseSearch(JSONObject("""{
          "resultados":[
            {"videoId":"video-1","titulo":"Aire","artista":"Valle","artistaVerificado":true,
             "canal":"Valle oficial","duracionSegundos":227,"miniaturaUrl":"https://images.example/cover.jpg"},
            {"videoId":"video-2","titulo":"Noche","artista":"Canal","artistaVerificado":false,
             "canal":"Canal","duracionSegundos":null,"miniaturaUrl":null}
          ],"siguientePagina":"abc="
        }"""))
        assertEquals("abc=", page.nextPage)
        assertEquals(Song("video-1", "Aire", "Valle", true, "Valle oficial", 227,
            "https://images.example/cover.jpg"), page.songs[0])
        assertFalse(page.songs[1].verified)
        assertNull(page.songs[1].durationSeconds)
        assertNull(page.songs[1].thumbnailUrl)
    }

    @Test fun emptyPageDoesNotCreateSongsOrNextPage() {
        val page = parseSearch(JSONObject("""{"resultados":[],"siguientePagina":null}"""))
        assertTrue(page.songs.isEmpty())
        assertNull(page.nextPage)
    }

    @Test(expected = JSONException::class)
    fun incompatibleResponseFailsRatherThanSimulatingSuccess() {
        parseSearch(JSONObject("""{"songs":[],"next":null}"""))
    }
}
