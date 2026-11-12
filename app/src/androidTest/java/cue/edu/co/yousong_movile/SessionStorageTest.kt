package cue.edu.co.yousong_movile

import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Compile-only until an explicitly authorized disposable test target is available. */
@RunWith(AndroidJUnit4::class)
class SessionStorageTest {
    @Test fun nativeKeystoreRoundTripCorruptionAndErasure() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val scratch = File(target.cacheDir, "session-test-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(target) { override fun getNoBackupFilesDir() = scratch }
        try {
            val store = KeystoreSessionStore(context)
            val session = ParticipantSession("synthetic-credential-only", "2099-01-01T00:00:00Z", "Guest", "Venue", "ABIERTO")
            store.write("https://api.example", session)
            val file = File(scratch, "participant-session-v1")
            assertFalse(file.readText(Charsets.ISO_8859_1).contains(session.token))
            val restored = KeystoreSessionStore(context).read()
            assertEquals("https://api.example", restored?.first)
            assertEquals(session.token, restored?.second?.token)
            file.writeBytes(file.readBytes().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() })
            assertNull(SessionLifecycle(store).restore())
            assertFalse(file.exists())
            store.write("https://api.example", session)
            SessionLifecycle(store).ended()
            assertNull(store.read())
        } finally { scratch.deleteRecursively() }
    }
}
