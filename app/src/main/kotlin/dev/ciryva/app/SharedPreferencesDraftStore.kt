package dev.ciryva.app

import android.content.SharedPreferences

/**
 * Prototype local-only draft storage; deliberately no network permission.
 * Synchronous commit makes the Save confirmation truthful.
 */
class SharedPreferencesDraftStore(
    private val preferences: SharedPreferences,
) : DraftStore {
    override fun read(): String = preferences.getString("local_draft", "") ?: ""

    override fun write(text: String): Boolean =
        preferences.edit().putString("local_draft", text).commit()

    override fun clear(): Boolean =
        preferences.edit().remove("local_draft").commit()
}
