package org.tokend.template.features.recovery.view

import android.content.Context
import android.support.annotation.StyleRes
import android.support.v7.app.AlertDialog
import org.jetbrains.anko.browse
import org.tokend.template.R
import org.tokend.template.data.model.AccountRecord
import org.tokend.template.data.repository.AccountRepository

class KycRecoveryStatusDialogFactory(
        private val context: Context,
        @StyleRes
        private val style: Int = R.style.AlertDialogStyle
) {
    fun getStatusDialog(status: AccountRecord.KycRecoveryStatus,
                        webClientUrl: String,
                        builderCustomization: AlertDialog.Builder.() -> Unit = {}): AlertDialog {
        return AlertDialog.Builder(context, style)
                .setTitle(R.string.kyc_recovery_status_dialog_title)
                .setMessage(when (status) {
                    AccountRecord.KycRecoveryStatus.PENDING ->
                        R.string.kyc_recovery_pending_message
                    AccountRecord.KycRecoveryStatus.REJECTED,
                    AccountRecord.KycRecoveryStatus.PERMANENTLY_REJECTED ->
                        R.string.kyc_recovery_rejected_message
                    else ->
                        R.string.kyc_recovery_initiated_message
                })
                .setPositiveButton(R.string.ok, null)
                .apply {
                    if (status == AccountRecord.KycRecoveryStatus.INITIATED) {
                        setNeutralButton(R.string.open_action) { _, _ ->
                            context.browse(webClientUrl, true)
                        }
                    }
                }
                .apply(builderCustomization)
                .create()
    }

    fun getStatusDialog(accountRepository: AccountRepository,
                        webClientUrl: String,
                        builderCustomization: AlertDialog.Builder.() -> Unit = {}): AlertDialog? =
            accountRepository.item?.kycRecoveryStatus?.let {
                getStatusDialog(it, webClientUrl, builderCustomization)
            }

    fun getStatusDialog(account: AccountRecord,
                        webClientUrl: String,
                        builderCustomization: AlertDialog.Builder.() -> Unit = {}): AlertDialog =
            getStatusDialog(account.kycRecoveryStatus, webClientUrl, builderCustomization)
}