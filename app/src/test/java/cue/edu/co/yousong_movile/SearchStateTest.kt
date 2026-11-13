package cue.edu.co.yousong_movile

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.*
import org.junit.Test

class SearchStateTest {
    @Test fun typingNeverCreatesASearch() {
        val state = SearchState()
        state.edit("Ai"); state.edit("Aire"); state.edit("Aire de")
        assertNull(state.submission)
        assertEquals(SearchPhase.Idle, state.phase)
    }

    @Test fun submitSearchesOnceAndResubmittingTheSameQueryIsFree() {
        val state = SearchState()
        state.edit("  Aire  ")
        state.submit()
        val first = state.submission
        assertEquals("Aire", first?.query)
        assertNull(first?.page)
        state.submit()
        assertSame(first, state.submission)
        state.edit("Luna"); state.submit()
        assertEquals("Luna", state.submission?.query)
    }

    @Test fun tooShortQueriesDoNotSearch() {
        val state = SearchState()
        state.edit("a"); state.submit()
        assertNull(state.submission)
    }

    @Test fun moreRequestsTheNextPageOnlyWhenThereIsOne() {
        val state = SearchState()
        state.edit("Aire"); state.submit()
        state.more()
        assertNull(state.submission?.page)
        state.nextPage = "p2"
        state.more()
        assertEquals("p2", state.submission?.page)
        assertEquals("Aire", state.submission?.query)
    }

    @Test fun retryAfterAnErrorRepeatsTheSameRequestAsANewEvent() {
        val state = SearchState()
        state.edit("Aire"); state.submit()
        val first = state.submission!!
        state.error = "Sin red"
        state.retry()
        assertNotEquals(first, state.submission)
        assertEquals(first.copy(seq = state.submission!!.seq), state.submission)
        state.submit() // with an error on screen, submitting again is allowed
        assertNotEquals(first.seq, state.submission!!.seq)
    }

    @Test fun clearingTheFieldDropsResultsWithoutSearching() {
        val state = SearchState()
        state.edit("Aire"); state.submit()
        state.songs = listOf(Song("Abcdef01_9-", "Aire", "Valle", true, "Valle", 200, null)); state.searched = true
        state.edit("")
        assertNull(state.submission)
        assertTrue(state.songs.isEmpty())
        assertEquals(SearchPhase.Idle, state.phase)
    }

    @Test fun onlyTheTypedTextIsSaved() {
        val state = SearchState()
        state.edit("Aire"); state.submit()
        state.songs = listOf(Song("Abcdef01_9-", "Aire", "Valle", true, "Valle", 200, null))
        val saved = with(SearchState.Saver) { SaverScope { true }.save(state) }
        assertEquals("Aire", saved)
        val restored = SearchState.Saver.restore(saved!!)!!
        assertEquals("Aire", restored.query)
        assertNull(restored.submission)
        assertTrue(restored.songs.isEmpty())
    }
}
