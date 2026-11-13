package cue.edu.co.yousong_movile

import org.junit.Assert.*
import org.junit.Test

/** Availability is not credential validity. All credentials here are synthetic, test-only values. */
class SessionReadinessTest {
    @Test fun maintenanceQueueRemainsReadableButCannotMutateOrVote() {
        // Exercise the existing wire-state entry point, not a reference to a missing enum constant.
        val state = ServiceState.valueOf("MANTENIMIENTO")
        val flow = QueueFlow()
        val song = QueueSong("Abcdef01_9-", "Aire", "Valle", true, null, null)
        val item = QueueItem("00000000-0000-0000-0000-000000000001", 1, 7, song, 30, false)
        val snapshot = QueueSnapshot(9, state, null, listOf(item))
        assertTrue(flow.accept(snapshot))
        assertNull(flow.beginMutation())
        assertFalse(canVote(state, false, false))
        assertSame(snapshot, flow.snapshot)
        assertEquals(7L, flow.snapshot?.items?.single()?.votes)
        assertEquals(1, flow.snapshot?.items?.size)
        assertTrue(flow.accept(snapshot.copy(version = 10, state = ServiceState.ABIERTO)))
        assertNotNull(flow.beginMutation())
    }

    @Test fun maintenanceHasSpecificFeedbackInsteadOfAnExpiredSessionMessage() {
        assertEquals("El local está en mantenimiento. Espera a que el personal habilite el servicio y actualiza el estado.",
            errorLabel(ApiFailure(409, "ESTABLISHMENT_IN_MAINTENANCE")))
    }

    @Test fun establishmentPauseHasSpecificFeedbackInsteadOfGenericConflict() {
        assertEquals("El local está pausado. Espera a que el personal reanude el servicio y actualiza el estado.",
            errorLabel(ApiFailure(409, "ESTABLISHMENT_PAUSED")))
    }

    @Test fun sessionPauseHasSpecificFeedbackInsteadOfAnExpiredSessionMessage() {
        assertEquals("La sesión está pausada. Puedes consultar la cola; espera a que el personal la reanude.",
            errorLabel(ApiFailure(409, "SESSION_PAUSED")))
    }

    @Test fun outsideScheduleExplainsWaitingWithoutClaimingSessionEnded() {
        assertEquals("El local está fuera de horario. Consulta la próxima apertura y actualiza el estado antes de ingresar.",
            errorLabel(ApiFailure(409, "OUTSIDE_SCHEDULE")))
    }

    @Test fun noActiveSessionExplainsTheOwnerOpeningPrerequisite() {
        assertEquals("El local aún no tiene una sesión abierta. Espera a que el personal la abra y actualiza el estado.",
            errorLabel(ApiFailure(409, "NO_ACTIVE_SESSION")))
    }

    @Test fun sessionNotAcceptingExplainsReadOnlyQueueWithoutReportingSuccess() {
        assertEquals("La sesión no admite solicitudes ni votos ahora. Actualiza el estado; puedes seguir consultando la cola.",
            errorLabel(ApiFailure(409, "SESSION_NOT_ACCEPTING")))
    }

    @Test fun availabilityRejectionsRetainTheOriginalCredentialAndStoredData() {
        val session = ParticipantSession("synthetic-test-only", "2099-01-01T00:00:00Z", "Visitante", "Local", "ABIERTO")
        val record = "https://api.example" to session
        val store = object : SessionRecordStore {
            var stored: Pair<String, ParticipantSession>? = record
            var writes = 0
            var clears = 0
            override fun read() = stored
            override fun write(origin: String, session: ParticipantSession) { writes++; stored = origin to session }
            override fun clear() { clears++; stored = null }
        }
        val lifecycle = SessionLifecycle(store) { 0 }
        listOf("ESTABLISHMENT_IN_MAINTENANCE", "ESTABLISHMENT_PAUSED", "SESSION_PAUSED",
            "OUTSIDE_SCHEDULE", "NO_ACTIVE_SESSION", "SESSION_NOT_ACCEPTING").forEach { code ->
            assertFalse(lifecycle.failure(409, code))
            assertSame(record, store.stored)
            assertSame(session, lifecycle.restore()?.second)
            assertEquals(0, store.writes)
            assertEquals(0, store.clears)
        }
        assertTrue(lifecycle.failure(401, "SESSION_ENDED"))
        assertNull(store.stored)
        assertEquals(1, store.clears)
    }

    @Test fun explicitRefreshOwnershipCannotAutoJoinOrUnlockANewerLookup() {
        val flow = EntryFlow()
        val refresh = requireNotNull(flow.begin())
        assertNull(flow.begin())
        assertTrue(flow.finish(refresh))
        // Finishing a read does not start a join; a separate explicit action owns the next ticket.
        assertFalse(flow.owns(refresh))
        val explicitJoin = requireNotNull(flow.begin())
        assertTrue(flow.owns(explicitJoin))
        flow.reset()
        val newerLookup = requireNotNull(flow.begin())
        assertFalse(flow.finish(explicitJoin))
        assertTrue(flow.owns(newerLookup))
        assertNull(flow.begin())
    }

    @Test fun pausedAndClosedSnapshotsKeepQueueDataAndRequireAnAuthoritativeReopen() {
        val flow = QueueFlow()
        val song = QueueSong("Abcdef01_9-", "Aire", "Valle", false, null, null)
        val item = QueueItem("00000000-0000-0000-0000-000000000001", 1, 7, song, 12, false)
        listOf(ServiceState.PAUSADO, ServiceState.CERRADO).forEachIndexed { index, state ->
            val snapshot = QueueSnapshot(index + 4L, state, null, listOf(item))
            assertTrue(flow.accept(snapshot))
            assertNull(flow.beginMutation())
            assertSame(snapshot, flow.snapshot)
            assertEquals(listOf(item), flow.snapshot?.items)
            assertFalse(canVote(state, false, false))
        }
        assertFalse(flow.accept(QueueSnapshot(3, ServiceState.ABIERTO, null, emptyList())))
        assertNull(flow.beginMutation())
        assertTrue(flow.accept(QueueSnapshot(6, ServiceState.ABIERTO, null, listOf(item))))
        assertNotNull(flow.beginMutation())
    }
}
