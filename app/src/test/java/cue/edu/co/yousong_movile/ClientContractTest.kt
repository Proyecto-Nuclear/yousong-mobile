package cue.edu.co.yousong_movile

import org.junit.Assert.*
import org.junit.Test

class ClientContractTest {
    @Test fun acceptsSlugAndOnlyExactQrPaths() {
        assertEquals("sala-azul", ClientContract.slug(" sala-azul "))
        assertEquals("sala-azul", ClientContract.slug("https://music.example/s/sala-azul"))
        listOf("", "../admin", "https://music.example/other/sala", "https://u:p@host/s/sala", "https://host/s/a%2Fb").forEach {
            assertNull(ClientContract.slug(it))
        }
    }

    @Test fun validatesApiOriginWithoutCredentialsOrCleartext() {
        assertEquals("https://api.example", ClientContract.apiBase("https://api.example/"))
        listOf("http://api.example", "https://u:p@api.example", "https://api.example?token=x", "https://api.example/api").forEach {
            assertNull(ClientContract.apiBase(it))
        }
    }

    @Test fun searchUsesExactParticipantContractAndEncodedPage() {
        assertEquals("/api/participante/catalogo/busqueda?q=rock+%26+roll&pagina=abc%3D", ClientContract.searchPath("rock & roll", "abc="))
        assertEquals("Luna azul", ClientContract.query("  Luna   azul  "))
        assertNull(ClientContract.query("a"))
        assertNull(ClientContract.query("a".repeat(101)))
    }

    @Test fun formatsRealDurationsWithoutInventingUnknownValues() {
        assertEquals("3:47", durationLabel(227))
        assertEquals("60:00", durationLabel(3600))
        assertEquals("Duración no disponible", durationLabel(null))
    }
}
