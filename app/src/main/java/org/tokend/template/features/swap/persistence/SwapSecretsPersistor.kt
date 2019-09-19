package org.tokend.template.features.swap.persistence

import android.content.SharedPreferences
import org.tokend.sdk.utils.extentions.decodeBase64
import org.tokend.sdk.utils.extentions.encodeBase64String

class SwapSecretsPersistor(
        private val preferences: SharedPreferences
) {
    fun saveSecret(hash: String, byteArray: ByteArray) {
        preferences
                .edit()
                .putString(hash, byteArray.encodeBase64String())
                .apply()
    }

    fun loadSecret(hash: String): ByteArray? {
        return preferences
                .getString(hash, "")
                .takeIf(String::isNotEmpty)
                ?.let { it.decodeBase64() }
    }
}