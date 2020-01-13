package org.tokend.template.features.nfcpayment.logic

import android.content.Context
import org.jetbrains.anko.defaultSharedPreferences

class NfcPaymentConfirmationManager(context: Context) {
    private val preferences = context.defaultSharedPreferences

    /**
     * If enabled user will have to manually confirm NFC payments
     */
    var isConfirmationRequired
        get() = preferences.getBoolean(PREFERENCE_KEY, ENABLED_BY_DEFAULT)
        set(value) {
            preferences.edit().putBoolean(PREFERENCE_KEY, value).apply()
        }

    companion object {
        private const val ENABLED_BY_DEFAULT = true
        const val PREFERENCE_KEY = "confirm_nfc_payments"
    }
}