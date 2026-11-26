package cue.edu.co.yousong_movile

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** The only persistent participant state is an authenticated encrypted, non-backup record. */
class KeystoreSessionStore(context: Context) : SessionRecordStore {
    private val file = AtomicFile(File(context.noBackupFilesDir, "participant-session-v1"))
    private val cipher = SessionCipher {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        ).apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build())
        }.generateKey()
    }

    override fun read(): Pair<String, ParticipantSession>? {
        if (!file.baseFile.exists()) return null
        return file.openRead().use { stream ->
            require(stream.channel.size() <= 65_536)
            val input = DataInputStream(stream)
            val origin = input.readUTF()
            require(ClientContract.apiBase(origin, BuildConfig.DEBUG, BuildConfig.LAN_API_HOST) == origin)
            val json = JSONObject(String(cipher.decrypt(input.readBytes(), origin), Charsets.UTF_8))
            origin to ParticipantSession(json.getString("token"), json.getString("expiresAt"),
                json.getString("pseudonym"), json.getString("name"), json.getString("service"),
                validatedQrSlug(json.opt("qrSlug")))
        }
    }

    override fun write(origin: String, session: ParticipantSession) {
        require(ClientContract.apiBase(origin, BuildConfig.DEBUG, BuildConfig.LAN_API_HOST) == origin)
        val payload = JSONObject().put("token", session.token).put("expiresAt", session.expiresAt)
            .put("pseudonym", session.pseudonym).put("name", session.name).put("service", session.service)
            .apply { validatedQrSlug(session.qrSlug)?.let { put("qrSlug", it) } }
            .toString().toByteArray(Charsets.UTF_8)
        val encrypted = try { cipher.encrypt(payload, origin) } finally { payload.fill(0) }
        val stream = file.startWrite()
        try {
            val output = DataOutputStream(stream)
            output.writeUTF(origin); output.write(encrypted); output.flush()
            file.finishWrite(stream)
        } catch (failure: Exception) { file.failWrite(stream); throw failure }
    }

    override fun clear() { file.delete() }
    private companion object { const val ALIAS = "yousong.participant.session.v1" }
}
