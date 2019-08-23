package org.tokend.template.features.tfa.view

import android.content.Context
import android.text.Html
import android.text.InputType
import android.text.method.LinkMovementMethod
import org.tokend.sdk.tfa.TfaVerifier
import org.tokend.template.R
import org.tokend.template.util.errorhandler.ErrorHandler

class TfaTelegramDialog(context: Context,
                        errorHandler: ErrorHandler,
                        tfaVerifierInterface: TfaVerifier.Interface,
                        private val botUrl: String)
    : TfaOtpDialog(context, errorHandler, tfaVerifierInterface) {

    override fun beforeDialogShow() {
        super.beforeDialogShow()

        inputEditText.apply {
            filters = arrayOf()
            inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        }

        messageTextView.movementMethod = LinkMovementMethod.getInstance()
    }

    @Suppress("DEPRECATION")
    override fun getMessage(): CharSequence {
        return Html.fromHtml(
                context.getString(
                        R.string.template_telegram_otp_dialog_message_bot_url,
                        botUrl
                )
        )
    }

    override fun getMaxCodeLength(): Int = -1
}