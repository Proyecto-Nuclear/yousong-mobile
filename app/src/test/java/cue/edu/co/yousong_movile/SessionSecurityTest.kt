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

    @Test fun debugAllowsOnlyTheEmulatorOriginWithoutLanHostAndPreservesHttps() {
        assertEquals("http://10.0.2.2:8080", ClientContract.apiBase("http://10.0.2.2:8080", debug = true))
        assertNull(ClientContract.apiBase("http://10.0.2.2:8080", debug = false))
        listOf("http://10.0.2.2:8081", "http://localhost:8080", "http://192.168.1.21:8080", "http://10.0.2.2:8080/api", "http://u:p@10.0.2.2:8080").forEach {
            assertNull(ClientContract.apiBase(it, debug = true))
        }
        assertEquals("https://api.example", ClientContract.apiBase("https://api.example/", debug = true))
        assertEquals("sala-azul", ClientContract.slug("https://untrusted.example/s/sala-azul"))
    }
    @Test fun configuredLanHostIsAllowedOnlyInDebugOnPort8080() {
        val lan = "192.168.1.21"
        assertEquals("http://192.168.1.21:8080", ClientContract.apiBase("http://192.168.1.21:8080/", debug = true, lanHost = lan))
        assertNull(ClientContract.apiBase("http://192.168.1.21:8080", debug = false, lanHost = lan))
        listOf("http://192.168.1.21:8081", "http://192.168.1.22:8080", "http://192.168.1.21:8080/api",
            "http://u:p@192.168.1.21:8080", "http://192.168.1.21:8080?x=1").forEach {
            assertNull(ClientContract.apiBase(it, debug = true, lanHost = lan))
        }
        assertEquals("http://10.0.2.2:8080", ClientContract.apiBase("http://10.0.2.2:8080", debug = true, lanHost = lan))
    }
    @Test fun onlyPrivateIpv4AddressesCanBeTheLanHost() {
        listOf("10.0.0.5", "172.16.0.1", "172.31.255.254", "192.168.0.10").forEach {
            assertTrue(it, ClientContract.privateLanHost(it))
            assertEquals("http://$it:8080", ClientContract.apiBase("http://$it:8080", debug = true, lanHost = it))
        }
        listOf("", "8.8.8.8", "172.32.0.1", "172.15.0.1", "192.169.0.1", "localhost", "api.example",
            "192.168.1", "192.168.1.256", "192.168.01.1", "10.0.0.5:8080", " 10.0.0.5").forEach {
            assertFalse(it, ClientContract.privateLanHost(it))
            assertNull(ClientContract.apiBase("http://$it:8080", debug = true, lanHost = it))
        }
        assertFalse(ClientContract.privateLanHost("127.0.0.1"))
    }
    @Test fun pairedPhoneLoopbackIsDebugOnlyAndNeverBroadensLocalHttp() {
        val loopback = "http://127.0.0.1:8080"
        assertEquals(loopback, ClientContract.apiBase(loopback, debug = true))
        assertNull(ClientContract.apiBase(loopback, debug = false))
        listOf("http://127.0.0.1:8081", "http://127.0.0.2:8080", "http://localhost:8080",
            "http://127.0.0.1:8080/api", "http://user:pass@127.0.0.1:8080",
            "http://127.0.0.1:8080?x=1", "http://127.0.0.1:8080#fragment").forEach {
            assertNull(ClientContract.apiBase(it, debug = true))
        }
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
