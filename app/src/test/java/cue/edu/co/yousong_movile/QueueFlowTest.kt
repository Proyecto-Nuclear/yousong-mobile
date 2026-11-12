package cue.edu.co.yousong_movile

import org.junit.Assert.*
import org.junit.Test

class QueueFlowTest {
    @Test fun serializesMutationsAndRequiresReconciliationBeforeRepeating() {
        val flow = QueueFlow()
        assertNull(flow.beginMutation())
        assertTrue(flow.accept(snapshot(4)))
        val ticket = requireNotNull(flow.beginMutation())
        assertNull(flow.beginMutation())
        assertTrue(flow.owns(ticket))
        flow.finishMutation(ticket)
        assertNull(flow.beginMutation())
        assertFalse(flow.accept(snapshot(3)))
        assertNull(flow.beginMutation())
        assertTrue(flow.accept(snapshot(4)))
        assertNotNull(flow.beginMutation())
    }

    @Test fun closedPausedAndUnknownVoteNeverEnableActions() {
        val flow = QueueFlow()
        flow.accept(snapshot(1, ServiceState.PAUSADO))
        assertNull(flow.beginMutation())
        flow.accept(snapshot(2, ServiceState.CERRADO))
        assertNull(flow.beginMutation())
        assertFalse(canVote(ServiceState.ABIERTO, null, false))
        assertFalse(canVote(ServiceState.ABIERTO, true, false))
        assertFalse(canVote(ServiceState.ABIERTO, false, true))
        assertTrue(canVote(ServiceState.ABIERTO, false, false))
    }

    @Test fun invalidatedOwnerCannotCompleteIntoNewSession() {
        val old = QueueFlow()
        old.accept(snapshot(9))
        val ticket = requireNotNull(old.beginMutation())
        old.invalidate()
        assertFalse(old.owns(ticket))
        assertFalse(old.accept(snapshot(100)))
        val fresh = QueueFlow()
        assertTrue(fresh.accept(snapshot(1)))
        assertEquals(1L, fresh.snapshot?.version)
    }

    @Test fun inputPathsAreBoundedAndNeverAcceptHostsOrArbitraryIds() {
        assertEquals("/api/participante/catalogo/sugerencias?q=rock+%26+roll", QueueContract.suggestionsPath(" rock  & roll "))
        listOf("", "a", "a".repeat(101)).forEach { input ->
            assertThrows(IllegalArgumentException::class.java) { QueueContract.suggestionsPath(input) }
        }
        listOf("", "short", "https://host", "abcdefghij/").forEach { input ->
            assertThrows(IllegalArgumentException::class.java) { QueueContract.requireVideoId(input) }
            assertThrows(IllegalArgumentException::class.java) { QueueContract.votePath(input) }
        }
        QueueContract.requireVideoId("Abcdef01_9-")
        val id = "00000000-0000-0000-0000-000000000001"
        assertEquals("/api/participante/solicitudes/$id/votos", QueueContract.votePath(id))
        // Previous catalog pagination and normalization are unchanged.
        assertEquals("Luna azul", ClientContract.query(" Luna   azul "))
        assertEquals("/api/participante/catalogo/busqueda?q=rock+%26+roll&pagina=abc%3D", ClientContract.searchPath("rock & roll", "abc="))
    }

    @Test fun exactSpanishErrorsDistinguishAlreadyVotedForbiddenAndQuota() {
        assertEquals("Ya apoyaste esta canción en esta sesión.", errorLabel(ApiFailure(409, "ALREADY_VOTED")))
        assertEquals("No tienes permiso para esta acción.", errorLabel(ApiFailure(403, "FORBIDDEN")))
        assertEquals("La cuota del catálogo se agotó. Inténtalo más tarde.", errorLabel(ApiFailure(503, "CATALOG_QUOTA_EXCEEDED")))
        assertEquals("El catálogo de música no está configurado. Consulta al personal.", errorLabel(ApiFailure(503, "CATALOG_NOT_CONFIGURED")))
        assertEquals("El local agotó sus búsquedas de hoy. Consulta al personal.", errorLabel(ApiFailure(429, "SEARCH_BUDGET_EXHAUSTED")))
        assertEquals("Demasiadas solicitudes. Espera un momento antes de reintentar.", errorLabel(ApiFailure(429, "RATE_LIMITED")))
        assertEquals("La sesión terminó o la credencial caducó. Vuelve a unirte.", errorLabel(ApiFailure(401, "SESSION_ENDED")))
        assertEquals("La sesión terminó o la credencial caducó. Vuelve a unirte.", errorLabel(ApiFailure(401, "INVALID_CREDENTIAL")))
    }

    private fun snapshot(version: Long, state: ServiceState = ServiceState.ABIERTO) =
        QueueSnapshot(version, state, null, emptyList())
}
