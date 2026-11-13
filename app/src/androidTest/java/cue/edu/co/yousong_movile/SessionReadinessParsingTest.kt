package cue.edu.co.yousong_movile

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Android JSON contracts: compilation is not device execution evidence. */
@RunWith(AndroidJUnit4::class)
class SessionReadinessParsingTest {
    @Test fun publicStatusReadsOptionalNextOpeningWithoutInventingOne() {
        val json = JSONObject("""{"nombre":"Venue","qrSlug":"sala-azul","servicio":"CERRADO","puedeParticipar":false,"proximaApertura":"2026-11-12T18:00:00Z"}""")
        val result = parseEstablishment(json)
        assertEquals("2026-11-12T18:00:00Z", result.nextOpening)
        assertFalse(canParticipate(result))
        json.remove("proximaApertura")
        assertNull(parseEstablishment(json).nextOpening)
        json.put("proximaApertura", JSONObject.NULL)
        assertNull(parseEstablishment(json).nextOpening)
        json.put("proximaApertura", 123)
        assertNull(parseEstablishment(json).nextOpening)
        json.put("proximaApertura", "not-a-date")
        assertNull(parseEstablishment(json).nextOpening)
    }

    @Test fun publicMaintenanceAndUnknownStatesRemainReadOnlyAndQrHostsAreRejected() {
        val json = JSONObject("""{"nombre":"Venue","qrSlug":"sala-azul","servicio":"MANTENIMIENTO","puedeParticipar":true}""")
        assertFalse(canParticipate(parseEstablishment(json)))
        json.put("servicio", "FUTURO")
        assertFalse(canParticipate(parseEstablishment(json)))
        json.put("qrSlug", "https://host/s/sala-azul")
        assertThrows(IllegalArgumentException::class.java) { parseEstablishment(json) }
        json.put("qrSlug", 123)
        assertThrows(IllegalArgumentException::class.java) { parseEstablishment(json) }
    }

    @Test fun unknownQueueStateDoesNotBecomeOpenOrAnExpiredCredential() {
        val snapshot = parseQueue(JSONObject("""{"colaVersion":1,"estado":"FUTURO","enReproduccion":null,"cola":[]}"""))
        assertEquals(ServiceState.UNKNOWN, snapshot.state)
        assertFalse(canVote(snapshot.state, false, false))
        assertTrue(snapshot.items.isEmpty())
        assertNull(snapshot.playing)
    }
}
