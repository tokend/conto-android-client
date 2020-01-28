package org.tokend.template.features.send.model

import org.tokend.sdk.utils.extentions.encodeBase64String
import org.tokend.template.data.model.Asset
import org.tokend.wallet.Account
import org.tokend.wallet.NetworkParams
import org.tokend.wallet.Transaction
import org.tokend.wallet.TransactionBuilder
import org.tokend.wallet.xdr.Operation
import org.tokend.wallet.xdr.PaymentFeeData
import org.tokend.wallet.xdr.op_extensions.SimplePaymentOp
import java.io.Serializable
import java.math.BigDecimal
import java.security.SecureRandom

data class PaymentRequest(
        val amount: BigDecimal,
        val asset: Asset,
        val senderAccountId: String,
        val senderBalanceId: String,
        val recipient: PaymentRecipient,
        val fee: PaymentFee,
        val paymentSubject: String?,
        val actualPaymentSubject: String?,
        val reference: String = SecureRandom.getSeed(16).encodeBase64String()
) : Serializable {

    fun toTransaction(networkParams: NetworkParams,
                      signer: Account): Transaction {
        val operation = SimplePaymentOp(
                sourceBalanceId = senderBalanceId,
                destAccountId = recipient.accountId,
                amount = networkParams.amountToPrecised(amount),
                subject = paymentSubject ?: "",
                reference = reference,
                feeData = PaymentFeeData(
                        sourceFee = fee.senderFee.toXdrFee(networkParams),
                        destinationFee = fee.recipientFee.toXdrFee(networkParams),
                        sourcePaysForDest = fee.senderPaysForRecipient,
                        ext = PaymentFeeData.PaymentFeeDataExt.EmptyVersion()
                )
        )

        return TransactionBuilder(networkParams, senderAccountId)
                .addOperation(Operation.OperationBody.Payment(operation))
                .addSigner(signer)
                .build()
    }
}