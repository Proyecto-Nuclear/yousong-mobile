package cue.edu.co.yousong_movile

import javax.crypto.KeyGenerator
import org.junit.Assert.*
import org.junit.Test

class SessionCipherTest {
    private fun cipher(): SessionCipher {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        return SessionCipher { key }
    }
    @Test fun roundTripUsesRandomIvAndNeverStoresPlaintext() {
        val cipher = cipher(); val bytes = "synthetic-participant-credential".toByteArray()
        val first = cipher.encrypt(bytes, "https://api.example")
        val second = cipher.encrypt(bytes, "https://api.example")
        assertFalse(first.contentEquals(bytes)); assertFalse(first.contentEquals(second))
        assertFalse(String(first, Charsets.ISO_8859_1).contains(String(bytes)))
        assertArrayEquals(bytes, cipher.decrypt(first, "https://api.example"))
    }
    @Test fun rejectsOtherOriginTamperingWrongKeyAndTruncatedRecord() {
        val cipher = cipher(); val record = cipher.encrypt("synthetic".toByteArray(), "https://api.example")
        fun rejects(block: () -> Unit) { try { block(); fail("Must reject unauthenticated record") } catch (_: Exception) { } }
        rejects { cipher.decrypt(record, "https://other.example") }
        rejects { cipher().decrypt(record, "https://api.example") }
        rejects { cipher.decrypt(record.copyOf(3), "https://api.example") }
        rejects { cipher.decrypt(record.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }, "https://api.example") }
    }
}
