package org.tokend.template.features.tfa.view

import android.content.Context
import org.tokend.sdk.tfa.TfaVerifier
import org.tokend.template.R
import org.tokend.template.util.errorhandler.ErrorHandler

/**
 * TFA verification dialog requesting code from SMS.
 */
class TfaPhoneDialog(context: Context,
                     errorHandler: ErrorHandler,
                     tfaVerifierInterface: TfaVerifier.Interface)
    : TfaOtpDialog(context, errorHandler, tfaVerifierInterface) {

    override fun getMessage(): String {
        return context.getString(R.string.sms_tfa_dialog_message)
    }

    override fun getMaxCodeLength(): Int = 6
}