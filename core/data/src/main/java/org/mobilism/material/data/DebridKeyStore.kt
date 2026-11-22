package org.mobilism.material.data

import android.content.Context

/** Called on Dispatchers.IO under the repository mutex. No keys enter observable UI state. */
internal class DebridKeyStore(context: Context) {
    private val preferences = context.getSharedPreferences("material-debrid-keys", Context.MODE_PRIVATE)
    private val secrets = KeystoreText("mobilism-material-debrid-v1")

    fun read(provider: DebridProvider): String? {
        val value = preferences.getString(provider.name, null) ?: return null
        return secrets.decrypt(value)
    }

    fun owner(provider: DebridProvider): String? = preferences.getString(provider.name + "-owner", null)

    fun write(provider: DebridProvider, value: String, owner: String) {
        val encrypted = secrets.encrypt(value)
        check(preferences.edit().putString(provider.name, encrypted).putString(provider.name + "-owner", owner).commit()) { "The key could not be saved." }
    }

    fun remove(provider: DebridProvider) {
        check(preferences.edit().remove(provider.name).remove(provider.name + "-owner").commit()) { "The key could not be removed." }
    }
}
