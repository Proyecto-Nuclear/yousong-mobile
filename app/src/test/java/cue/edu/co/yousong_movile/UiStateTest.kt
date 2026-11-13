package cue.edu.co.yousong_movile

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class UiStateTest {
    private fun song(id: String) = QueueSong(id, "Luna azul", "Estudio Norte", true, 214, null)
    private fun item(id: String, votes: Long, voted: Boolean?, position: Int = 1) =
        QueueItem("00000000-0000-4000-8000-00000000000$position", position, votes, song(id), 0, voted)
    private fun catalog(id: String) = Song(id, "Luna azul", "Estudio Norte", true, "Canal", 214, null)

    @Test fun clockLabelFormatsMinutesAndPaddedSeconds() {
        assertEquals("3:34", clockLabel(214))
        assertEquals("0:05", clockLabel(5))
        assertEquals("0:00", clockLabel(0))
        assertEquals("61:01", clockLabel(3661L))
        assertNull(clockLabel(null as Int?))
        assertNull(clockLabel(-1))
    }

    @Test fun durationLabelKeepsItsFallbackForUnknownDurations() {
        assertEquals("3:34", durationLabel(214))
        assertEquals("Duración no disponible", durationLabel(null))
    }

    @Test fun waitRoundsUpToWholeMinutes() {
        assertEquals(0, waitMinutes(0))
        assertEquals(1, waitMinutes(1))
        assertEquals(1, waitMinutes(60))
        assertEquals(2, waitMinutes(61))
        assertEquals(6, waitMinutes(360))
        assertNull(waitMinutes(-5))
    }

    @Test fun backFromSecondaryTabsGoesHomeAndHomeLeaves() {
        assertEquals(Tab.Inicio, backTarget(Tab.Buscar))
        assertEquals(Tab.Inicio, backTarget(Tab.Cola))
        assertNull(backTarget(Tab.Inicio))
    }

    @Test fun voteStateDistinguishesVotedPendingAndUnavailable() {
        val open = ServiceState.ABIERTO
        assertEquals(VoteState.Votable, voteState(false, open, interactive = true, pendingThis = false))
        assertEquals(VoteState.Voted, voteState(true, open, interactive = true, pendingThis = false))
        assertEquals(VoteState.Voted, voteState(true, ServiceState.PAUSADO, interactive = false, pendingThis = false))
        assertEquals(VoteState.Pending, voteState(false, open, interactive = true, pendingThis = true))
        assertEquals(VoteState.Unavailable(UnavailableReason.Unknown), voteState(null, open, interactive = true, pendingThis = false))
        assertEquals(VoteState.Unavailable(UnavailableReason.Paused), voteState(false, ServiceState.PAUSADO, interactive = true, pendingThis = false))
        assertEquals(VoteState.Unavailable(UnavailableReason.NotReady), voteState(false, open, interactive = false, pendingThis = false))
    }

    @Test fun anotherRowsPendingVoteDoesNotDisableThisRowsLook() {
        // Only the targeted row shows progress; the global pending flag is not an input.
        assertEquals(VoteState.Votable, voteState(false, ServiceState.ABIERTO, interactive = true, pendingThis = false))
    }

    @Test fun songCtaFollowsQueueMembershipAndVote() {
        val queued = QueueSnapshot(1, ServiceState.ABIERTO, null, listOf(item("Abcdef01_9-", 3, false)))
        assertEquals(SongCta.Request, songCta(catalog("Zzzzzz01_9-"), queued, interactive = true))
        assertEquals(SongCta.Vote, songCta(catalog("Abcdef01_9-"), queued, interactive = true))
        val voted = queued.copy(items = listOf(item("Abcdef01_9-", 3, true)))
        assertEquals(SongCta.Voted, songCta(catalog("Abcdef01_9-"), voted, interactive = false))
        assertEquals(SongCta.Unavailable, songCta(catalog("Zzzzzz01_9-"), queued.copy(state = ServiceState.PAUSADO), interactive = true))
        assertEquals(SongCta.Unavailable, songCta(catalog("Zzzzzz01_9-"), queued, interactive = false))
        assertEquals(SongCta.Unavailable, songCta(catalog("Zzzzzz01_9-"), null, interactive = true))
        val unknown = queued.copy(items = listOf(item("Abcdef01_9-", 3, null)))
        assertEquals(SongCta.Unavailable, songCta(catalog("Abcdef01_9-"), unknown, interactive = true))
    }

    @Test fun favouriteIsMostVotedAndTiesKeepServerOrder() {
        val first = item("Aaaaaa01_9-", 4, false, 1)
        val second = item("Bbbbbb01_9-", 4, false, 2)
        val third = item("Cccccc01_9-", 2, false, 3)
        assertEquals(first, favourite(QueueSnapshot(1, ServiceState.ABIERTO, null, listOf(first, second, third))))
        assertEquals(second, favourite(QueueSnapshot(1, ServiceState.ABIERTO, null, listOf(third, second))))
        assertNull(favourite(QueueSnapshot(1, ServiceState.ABIERTO, null, listOf(item("Aaaaaa01_9-", 0, false)))))
        assertNull(favourite(null))
    }

    @Test fun serviceChipNamesEveryKnownState() {
        assertEquals(ServiceChip.Live, serviceChip("ABIERTO"))
        assertEquals(ServiceChip.Paused, serviceChip("PAUSADO"))
        assertEquals(ServiceChip.Closed, serviceChip("CERRADO"))
        assertEquals(ServiceChip.Maintenance, serviceChip("MANTENIMIENTO"))
        assertNull(serviceChip("FUTURO"))
        assertNull(serviceChip(ServiceState.UNKNOWN))
    }

    @Test fun openingIsDescribedRelativeToTheLocalDay() {
        val bogota = TimeZone.getTimeZone("America/Bogota")
        val es = Locale.forLanguageTag("es")
        val now = expiresAtMillis("2026-11-13T15:00:00Z")!! // 10:00 in Bogotá
        assertEquals(Opening(0, "18:00", "viernes 13"), opening("2026-11-13T23:00:00Z", now, bogota, es))
        assertEquals(1, opening("2026-11-14T23:00:00Z", now, bogota, es)?.dayOffset)
        assertEquals(2, opening("2026-11-20T23:00:00Z", now, bogota, es)?.dayOffset)
        assertNull(opening("not-a-date", now, bogota, es))
        assertNull(opening(null, now, bogota, es))
    }
}
