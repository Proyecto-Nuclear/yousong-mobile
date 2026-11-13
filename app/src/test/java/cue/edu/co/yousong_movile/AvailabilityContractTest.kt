package cue.edu.co.yousong_movile

import org.junit.Assert.*
import org.junit.Test

class AvailabilityContractTest {
    @Test fun metadataAcceptsOnlyAnExactSlugNeverAUrlOrAnotherJsonType() {
        assertEquals("sala-azul", validatedQrSlug("sala-azul"))
        listOf(null, 123, true, "", "xy", " sala-azul ", "SALA-AZUL", "https://host/s/sala-azul",
            "sala/azul", "a".repeat(81)).forEach { assertNull(validatedQrSlug(it)) }
    }

    @Test fun legacyIdentityCannotBeRecoveredByNameOrAnUnverifiedFreshQr() {
        val readiness = SessionReadiness(null)
        assertFalse(readiness.accept(Establishment("Same name", "sala-azul", "ABIERTO", true)))
        assertFalse(readiness.canAct(ServiceState.ABIERTO, "ABIERTO"))
        assertNull(readiness.availability)
        assertEquals("No pudimos confirmar el local de esta sesión. Puedes ver la cola; para pedir canciones, sal del local y vuelve a escanear su QR.", readiness.label)
    }

    @Test fun onlyMatchingExplicitlyAvailableEstablishmentEnablesActions() {
        val readiness = SessionReadiness("sala-azul")
        assertFalse(readiness.canAct(ServiceState.ABIERTO, "ABIERTO"))
        assertFalse(readiness.accept(Establishment("Same name", "otro-local", "ABIERTO", true)))
        assertFalse(readiness.canAct(ServiceState.ABIERTO, "ABIERTO"))
        assertTrue(readiness.accept(Establishment("Venue", "sala-azul", "ABIERTO", true)))
        assertTrue(readiness.canAct(ServiceState.ABIERTO, "ABIERTO"))
        assertFalse(readiness.canAct(ServiceState.PAUSADO, "ABIERTO"))
        assertFalse(readiness.canAct(ServiceState.ABIERTO, "PAUSADO"))
        assertFalse(readiness.canAct(null, "ABIERTO"))
        assertFalse(readiness.canAct(ServiceState.ABIERTO, "FUTURO"))
        readiness.unavailable()
        assertFalse(readiness.canAct(ServiceState.ABIERTO, "ABIERTO"))
        assertNull(readiness.availability)
    }

    @Test fun availabilityChangesIndependentlyOfAnUnchangedQueueVersionAndNeverErasesQueueData() {
        val flow = QueueFlow()
        val snapshot = QueueSnapshot(7, ServiceState.ABIERTO, null, emptyList())
        assertTrue(flow.accept(snapshot))
        val readiness = SessionReadiness("sala-azul")
        listOf("PAUSADO", "MANTENIMIENTO", "CERRADO", "FUTURO").forEach { service ->
            assertTrue(readiness.accept(Establishment("Venue", "sala-azul", service, true)))
            assertFalse(readiness.canAct(flow.snapshot?.state, "ABIERTO"))
            assertSame(snapshot, flow.snapshot)
            assertEquals(7L, flow.snapshot?.version)
        }
        readiness.accept(Establishment("Venue", "sala-azul", "ABIERTO", false))
        assertFalse(readiness.canAct(flow.snapshot?.state, "ABIERTO"))
        readiness.accept(Establishment("Venue", "sala-azul", "ABIERTO", true))
        assertTrue(readiness.canAct(flow.snapshot?.state, "ABIERTO"))
    }

    @Test fun entryUsesBothAuthoritativeAvailabilityFieldsAndDisplaysOnlyARealNextOpening() {
        val closed = Establishment("Venue", "sala-azul", "CERRADO", false, "2026-11-12T18:00:00Z")
        assertFalse(canParticipate(closed))
        assertEquals("El local no admite participantes ahora. Espera a que el servicio esté disponible y actualiza el estado. Próxima apertura: 2026-11-12T18:00:00Z.", availabilityLabel(closed))
        assertFalse(availabilityLabel(closed.copy(nextOpening = null)).contains("Próxima apertura"))
        assertFalse(availabilityLabel(closed.copy(nextOpening = "not-a-date")).contains("not-a-date"))
        assertTrue(canParticipate(closed.copy(service = "ABIERTO", canJoin = true)))
        assertFalse(canParticipate(closed.copy(service = "MANTENIMIENTO", canJoin = true)))
    }

    @Test fun refreshPreservesOnlyExplicitConsentForTheSamePolicyAndDoesNotJoin() {
        assertTrue(retainsConsent(PrivacyPolicy("1", emptyList()), PrivacyPolicy("1", emptyList())))
        assertFalse(retainsConsent(PrivacyPolicy("1", emptyList()), PrivacyPolicy("2", emptyList())))
        assertFalse(retainsConsent(null, PrivacyPolicy("1", emptyList())))
        // Refresh owns a read ticket only; the existing explicit join gate remains separate.
        val flow = EntryFlow()
        val refresh = requireNotNull(flow.begin())
        assertNull(flow.begin())
        assertTrue(flow.finish(refresh))
        assertFalse(flow.owns(refresh))
    }
}
