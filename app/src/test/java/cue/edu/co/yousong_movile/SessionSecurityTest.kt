package cue.edu.co.yousong_movile

import org.junit.Assert.*
import org.junit.Test

class SessionSecurityTest {
    private class Store : SessionRecordStore {
        var record: Pair<String, ParticipantSession>? = null
        var corrupt = false
        var clears = 0
        override fun read(): Pair<String, ParticipantSession>? { if (corrupt) error("Corrupt record"); return record }
        override fun write(origin: String, session: ParticipantSession) { record = origin to session }
        override fun clear() { record = null; clears++ }
    }
    private fun session(expiry: String = "2099-01-01T00:00:00Z") = ParticipantSession("synthetic-test-only", expiry, "Visitor", "Venue", "ABIERTO")

    @Test fun debugAllowsOnlyTheTwoExplicitDevelopmentOriginsAndPreservesHttps() {
        listOf("http://10.0.2.2:8080", "http://192.168.1.21:8080").forEach {
            assertEquals(it, ClientContract.apiBase(it, debug = true))
            assertNull(ClientContract.apiBase(it, debug = false))
        }
        listOf("http://10.0.2.2:8081", "http://localhost:8080", "http://192.168.1.22:8080", "http://10.0.2.2:8080/api", "http://u:p@10.0.2.2:8080").forEach {
            assertNull(ClientContract.apiBase(it, debug = true))
        }
        assertEquals("https://api.example", ClientContract.apiBase("https://api.example/", debug = true))
        assertEquals("sala-azul", ClientContract.slug("https://untrusted.example/s/sala-azul"))
    }
    @Test fun joinedSessionRestoresWithItsOriginalApiOrigin() {
        val store = Store(); val lifecycle = SessionLifecycle(store) { 0 }
        val session = session()
        lifecycle.joined("https://api.example", session)
        val restored = SessionLifecycle(store) { 0 }.restore()
        assertEquals("https://api.example", restored?.first)
        assertSame(session, restored?.second)
    }
    @Test fun corruptExpiredAndMalformedRecordsAreErased() {
        listOf("1970-01-01T00:00:00Z", "not-a-date").forEach { expiry ->
            val store = Store().apply { record = "https://api.example" to session(expiry) }
            assertNull(SessionLifecycle(store) { 1 }.restore())
            assertNull(store.record); assertEquals(1, store.clears)
        }
        val store = Store().apply { corrupt = true }
        assertNull(SessionLifecycle(store).restore()); assertEquals(1, store.clears)
    }
    @Test fun rejectedCredentialsAndSuccessfulLeaveEraseButFailedLeaveRetainsState() {
        listOf("SESSION_ENDED", "INVALID_CREDENTIAL").forEach { code ->
            val store = Store().apply { record = "https://api.example" to session() }
            assertTrue(SessionLifecycle(store).failure(401, code)); assertNull(store.record)
        }
        val store = Store().apply { record = "https://api.example" to session() }
        val lifecycle = SessionLifecycle(store)
        assertFalse(lifecycle.failure(503, "SESSION_ENDED")); assertNotNull(store.record)
        lifecycle.ended(); assertNull(store.record)
    }
    @Test fun pollingIntervalMatchesBackendContract() { assertTrue(SessionLifecycle.POLL_INTERVAL_MS in 5_000L..10_000L) }
}
