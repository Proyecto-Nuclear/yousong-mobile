package cue.edu.co.yousong_movile

import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.DataOutputStream
import java.security.KeyStore
import javax.crypto.SecretKey
import org.json.JSONObject

/** Compile-only until an explicitly authorized disposable test target is available. */
@RunWith(AndroidJUnit4::class)
class SessionStorageTest {
    @Test fun legacyAndMalformedOptionalIdentityRetainTheCredentialWithoutTrustingAFreshQr() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val scratch = File(target.cacheDir, "session-test-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(target) { override fun getNoBackupFilesDir() = scratch }
        try {
            val store = KeystoreSessionStore(context)
            val origin = "https://api.example"
            val session = ParticipantSession("synthetic-credential-only", "2099-01-01T00:00:00Z", "Guest", "Venue", "ABIERTO")
            store.write(origin, session) // An omitted slug has exactly the original payload fields.
            assertNull(store.read()?.second?.qrSlug)
            val cipher = SessionCipher {
                KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                    .getKey("yousong.participant.session.v1", null) as SecretKey
            }
            val payload = JSONObject().put("token", session.token).put("expiresAt", session.expiresAt)
                .put("pseudonym", session.pseudonym).put("name", session.name).put("service", session.service)
            val file = File(scratch, "participant-session-v1")
            // Authenticated old/malformed metadata fixtures: never read application records or real credentials.
            listOf(JSONObject.NULL, 123, true, "", "https://host/s/sala-azul", " sala-azul ").forEach { value ->
                payload.put("qrSlug", value)
                DataOutputStream(file.outputStream()).use {
                    it.writeUTF(origin)
                    it.write(cipher.encrypt(payload.toString().toByteArray(Charsets.UTF_8), origin))
                }
                val restored = SessionLifecycle(store) { 0 }.restore()
                assertEquals(session.token, restored?.second?.token)
                assertNull(restored?.second?.qrSlug)
                assertTrue(file.exists())
                val readiness = SessionReadiness(restored?.second?.qrSlug)
                assertFalse(readiness.accept(Establishment("Venue", "sala-azul", "ABIERTO", true)))
                assertFalse(readiness.canAct(ServiceState.ABIERTO, "ABIERTO"))
            }
            SessionLifecycle(store).ended()
            assertFalse(file.exists())
        } finally { scratch.deleteRecursively() }
    }

    @Test fun nativeKeystoreRoundTripCorruptionAndErasure() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val scratch = File(target.cacheDir, "session-test-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(target) { override fun getNoBackupFilesDir() = scratch }
        try {
            val store = KeystoreSessionStore(context)
            val session = ParticipantSession("synthetic-credential-only", "2099-01-01T00:00:00Z", "Guest", "Venue", "ABIERTO", "sala-azul")
            store.write("https://api.example", session)
            val file = File(scratch, "participant-session-v1")
            assertFalse(file.readText(Charsets.ISO_8859_1).contains(session.token))
            assertFalse(file.readText(Charsets.ISO_8859_1).contains("sala-azul"))
            val restored = KeystoreSessionStore(context).read()
            assertEquals("https://api.example", restored?.first)
            assertEquals(session.token, restored?.second?.token)
            assertEquals("sala-azul", restored?.second?.qrSlug)
            file.writeBytes(file.readBytes().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() })
            assertNull(SessionLifecycle(store).restore())
            assertFalse(file.exists())
            store.write("https://api.example", session)
            SessionLifecycle(store).ended()
            assertNull(store.read())
        } finally { scratch.deleteRecursively() }
    }
}
