package cue.edu.co.yousong_movile

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Versioned AES/GCM envelope; origin is authenticated, never a credential destination. */
internal class SessionCipher(private val key: () -> SecretKey) {
    fun encrypt(plaintext: ByteArray, origin: String): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(origin.toByteArray(Charsets.UTF_8))
        require(cipher.iv.size == 12)
        return byteArrayOf(1) + cipher.iv + cipher.doFinal(plaintext)
    }
    fun decrypt(record: ByteArray, origin: String): ByteArray {
        require(record.size >= 29 && record[0] == 1.toByte())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, record.copyOfRange(1, 13)))
        cipher.updateAAD(origin.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(record.copyOfRange(13, record.size))
    }
}
