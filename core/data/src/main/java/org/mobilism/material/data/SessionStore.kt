package org.mobilism.material.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Only tokens are persisted, never the user's password. Backups are disabled in the host. */
internal class SessionStore(context: Context) {
    private val preferences = context.getSharedPreferences("material-session", Context.MODE_PRIVATE)

    suspend fun read(): Session? = withContext(Dispatchers.IO) {
        val stored = preferences.getString("encrypted-session", null) ?: return@withContext null
        try {
            val parts = stored.split(':', limit = 2)
            require(parts.size == 2)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, decode(parts[0])))
            val json = JSONObject(String(cipher.doFinal(decode(parts[1])), Charsets.UTF_8))
            val token = json.getString("accessToken")
            require(token.isNotBlank())
            Session(json.getString("username"), token, json.optString("refreshToken").ifBlank { null })
        } catch (_: java.security.GeneralSecurityException) {
            preferences.edit().remove("encrypted-session").commit()
            null
        } catch (_: IllegalArgumentException) {
            preferences.edit().remove("encrypted-session").commit()
            null
        } catch (_: org.json.JSONException) {
            preferences.edit().remove("encrypted-session").commit()
            null
        }
    }

    suspend fun write(session: Session) = withContext(Dispatchers.IO) {
        val json = JSONObject().put("username", session.username).put("accessToken", session.accessToken)
        session.refreshToken?.let { json.put("refreshToken", it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = encode(cipher.iv) + ":" + encode(cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8)))
        check(preferences.edit().putString("encrypted-session", encrypted).putBoolean("guest", false).commit()) {
            "Unable to save the session."
        }
    }

    suspend fun isGuest(): Boolean = withContext(Dispatchers.IO) { preferences.getBoolean("guest", false) }

    suspend fun clear(guest: Boolean = false) = withContext(Dispatchers.IO) {
        check(preferences.edit().remove("encrypted-session").putBoolean("guest", guest).commit()) {
            "Unable to clear the session."
        }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
        }.generateKey()
    }

    private fun encode(value: ByteArray): String = Base64.encodeToString(value, Base64.NO_WRAP)
    private fun decode(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)

    private companion object { const val ALIAS = "mobilism-material-session-v1" }
}
