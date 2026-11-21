package cue.edu.co.yousong_movile

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

/** Pure rules behind the v2 home, search and mini-player (no Android, no network). */
class HomeV2StateTest {
    private fun song(id: String, genre: String?, cover: String? = "https://img.example/$id.jpg") =
        Song(id.padEnd(11, 'x'), "T $id", "A $id", true, "C", 200, cover, genre)

    @Test fun metaLineJoinsArtistGenreAndClockAndSkipsUnknowns() {
        assertEquals("Valle · Salsa · 3:34", songMetaLine("Valle", "Salsa", 214))
        assertEquals("Valle · 3:34", songMetaLine("Valle", null, 214))
        assertEquals("Valle · 3:34", songMetaLine("Valle", "  ", 214))
        assertEquals("Valle · Salsa", songMetaLine("Valle", "Salsa", null))
        assertEquals("Valle", songMetaLine("Valle", null, null))
        assertEquals("Salsa · 0:05", songMetaLine("", "Salsa", 5))
        assertEquals("Valle", songMetaLine("Valle", null, -1))
    }

    @Test fun greetingFollowsTheHourOfTheDay() {
        assertEquals(Greeting.Night, greeting(0))
        assertEquals(Greeting.Night, greeting(4))
        assertEquals(Greeting.Morning, greeting(5))
        assertEquals(Greeting.Morning, greeting(11))
        assertEquals(Greeting.Afternoon, greeting(12))
        assertEquals(Greeting.Afternoon, greeting(18))
        assertEquals(Greeting.Night, greeting(19))
        assertEquals(Greeting.Night, greeting(23))
    }

    @Test fun genreTilesPutTrendGenresFirstByFrequencyThenTheDefaults() {
        val trends = listOf(song("a", "Pop"), song("b", "Vallenato"), song("c", "Vallenato"), song("d", null), song("e", "Pop"),
            song("f", "Vallenato"))
        val tiles = genreTiles(trends, defaults = listOf("Salsa", "Pop", "Cumbia"))
        assertEquals(listOf("Vallenato", "Pop", "Salsa", "Cumbia"), tiles.map { it.label })
        assertEquals("https://img.example/b.jpg", tiles[0].cover)
        assertNull(tiles[2].cover)
    }

    @Test fun genreTilesMergeSpellingsIgnoringCaseAndAccentsAndAreCapped() {
        val trends = listOf(song("a", "Reggaeton"), song("b", "reggaetón", cover = null), song("c", "  "))
        val tiles = genreTiles(trends, defaults = listOf("Reggaetón", "Salsa", "Salsa", "Cumbia"), max = 2)
        assertEquals(listOf("Reggaeton", "Salsa"), tiles.map { it.label })
        assertEquals(genreKey("Reggaetón"), genreKey(" REGGAETON "))
    }

    @Test fun genreTilesWithoutTrendsAreTheColombianDefaults() {
        val tiles = genreTiles(emptyList())
        assertEquals(DefaultGenres.take(12), tiles.map { it.label })
        assertTrue(tiles.all { it.cover == null })
        assertTrue("Reggaetón" in tiles.map { it.label } && "Vallenato" in tiles.map { it.label })
    }

    @Test fun feedLoadsFailsAndKeepsOldDataOnAFailedRefresh() {
        var feed: Feed<List<String>> = Feed.Loading
        feed = feed.failed("sin red")
        assertEquals(Feed.Failed("sin red"), feed)
        feed = feed.reloading()
        assertEquals(Feed.Loading, feed)
        feed = feed.loaded(listOf("a"))
        assertEquals(Feed.Ready(listOf("a")), feed)
        feed = feed.reloading()
        assertEquals(Feed.Ready(listOf("a"), refreshing = true), feed)
        feed = feed.failed("cuota")
        assertEquals(Feed.Ready(listOf("a"), refreshing = false), feed)
    }

    @Test fun unavailableFeedStaysUnavailable() {
        val feed: Feed<List<String>> = Feed.Unavailable
        assertEquals(Feed.Unavailable, feed.reloading())
        assertEquals(Feed.Unavailable, feed.loaded(listOf("a")))
        assertEquals(Feed.Unavailable, feed.failed("x"))
    }

    @Test fun shelfMapsEachFeedStateToWhatTheCarouselDraws() {
        assertEquals(Shelf.Hidden, (Feed.Unavailable as Feed<List<Int>>).shelf { it })
        assertEquals(Shelf.Loading, (Feed.Loading as Feed<List<Int>>).shelf { it })
        assertEquals(Shelf.Failed("x"), (Feed.Failed("x") as Feed<List<Int>>).shelf { it })
        assertEquals(Shelf.Empty, Feed.Ready(emptyList<Int>()).shelf { it })
        assertEquals(Shelf.Items(listOf(1, 2)), Feed.Ready(listOf(1, 2), refreshing = true).shelf { it })
    }

    @Test fun playedFractionComesFromServerRemainingTime() {
        assertEquals(0.5f, playedFraction(200, 100)!!, 0.0001f)
        assertEquals(1f, playedFraction(200, 0)!!, 0.0001f)
        assertEquals(0f, playedFraction(200, 500)!!, 0.0001f)
        assertNull(playedFraction(null, 100))
        assertNull(playedFraction(0, 100))
        assertNull(playedFraction(200, -1))
    }

    @Test fun playedAtIsShownInTheViewersZone() {
        val bogota = TimeZone.getTimeZone("America/Bogota")
        assertEquals("22:41", localTime("2026-11-21T03:41:00Z", bogota, Locale.ROOT))
        assertNull(localTime("ayer", bogota, Locale.ROOT))
    }

    @Test fun highlightsPathValidatesSlugAndDays() {
        assertEquals("/api/public/establecimientos/sala-azul/destacadas?dias=30", DiscoveryContract.highlightsPath("sala-azul"))
        assertEquals("/api/public/establecimientos/sala-azul/destacadas?dias=365", DiscoveryContract.highlightsPath("sala-azul", 365))
        listOf(0, 366, -1).forEach { days ->
            assertThrows(IllegalArgumentException::class.java) { DiscoveryContract.highlightsPath("sala-azul", days) }
        }
        listOf("../x", "SALA", "a", "sala/azul").forEach { slug ->
            assertThrows(IllegalArgumentException::class.java) { DiscoveryContract.highlightsPath(slug) }
        }
    }

    @Test fun queueSongsBecomeDetailSongsWithTheirGenre() {
        val queued = QueueSong("Abcdef01_9-", "Luna", "Norte", false, 214, null, "Cumbia")
        assertEquals(Song("Abcdef01_9-", "Luna", "Norte", false, "", 214, null, "Cumbia"), queued.asSong())
    }
}
