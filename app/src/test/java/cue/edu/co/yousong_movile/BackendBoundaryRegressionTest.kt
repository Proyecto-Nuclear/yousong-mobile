package cue.edu.co.yousong_movile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackendBoundaryRegressionTest {
    @Test fun preservesExplicitBackendSlugsInRawAndQrForms() {
        // CrearEstablecimientoRequest accepts [a-z0-9-]{3,80}; explicit slugs are not normalized.
        listOf("sala--azul", "-sala", "sala-", "---", "a09", "a".repeat(80)).forEach { slug ->
            assertEquals(slug, ClientContract.slug(slug))
            assertEquals(slug, ClientContract.slug("https://music.example/s/$slug"))
        }
    }

    @Test fun rejectsSlugsOutsideBackendLengthAndCharacterBoundaries() {
        listOf("", "a", "aa", "a".repeat(81), "Sala", "sala_azul", "salá", "sala azul",
            "a/b", "a%2Fb", "../sala").forEach { slug ->
            assertNull("Raw slug: $slug", ClientContract.slug(slug))
            assertNull("QR slug: $slug", ClientContract.slug("https://music.example/s/$slug"))
        }
    }

    @Test fun preservesNormalSlugsAndPastedLinkWhitespaceHandling() {
        assertEquals("sala-azul", ClientContract.slug(" sala-azul "))
        assertEquals("sala-azul", ClientContract.slug(" https://music.example/s/sala-azul "))
        assertEquals("sala-azul", ClientContract.slug("http://music.example/s/sala-azul"))
    }

    @Test fun rejectsUnsafeOrNonExactQrPathsWithoutDecodingOrNormalization() {
        listOf(
            "https://music.example/other/sala-azul",
            "https://music.example/s/sala-azul/extra",
            "https://music.example/s/sala-azul/",
            "https://music.example/s/../sala-azul",
            "https://music.example/s/%73ala-azul",
            "https://music.example/s/sala%2Fazul",
            "https://music.example/s/sala-azul?token=value",
            "https://music.example/s/sala-azul#fragment",
            "https://user:password@music.example/s/sala-azul",
            "https:///s/sala-azul",
            "ftp://music.example/s/sala-azul",
            "/s/sala-azul"
        ).forEach { link -> assertNull(link, ClientContract.slug(link)) }
    }
}
