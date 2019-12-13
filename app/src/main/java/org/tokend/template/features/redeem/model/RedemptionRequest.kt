package org.tokend.template.features.redeem.model

import org.tokend.template.data.model.history.SimpleFeeRecord
import org.tokend.template.features.send.model.PaymentFee
import org.tokend.wallet.*
import org.tokend.wallet.Transaction
import org.tokend.wallet.xdr.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.math.BigDecimal

/**
 * Holds data required to create and submit
 * payment transaction for asset redemption.
 */
class RedemptionRequest(
        val sourceAccountId: String,
        val assetCode: String,
        val amount: BigDecimal,
        val salt: Long,
        val timeBounds: TimeBounds,
        val signature: DecoratedSignature
) {
    fun serialize(networkParams: NetworkParams): ByteArray {
        val byteStream = ByteArrayOutputStream()
        DataOutputStream(byteStream).use { stream ->
            stream.write(Base32Check.decodeAccountId(sourceAccountId))
            stream.writeInt(assetCode.length)
            stream.write(assetCode.toByteArray(STRING_CHARSET))
            stream.writeLong(networkParams.amountToPrecised(amount))
            stream.writeLong(salt)
            stream.writeLong(timeBounds.minTime)
            stream.writeLong(timeBounds.maxTime)
            stream.write(signature.hint.wrapped)
            stream.write(signature.signature)
        }
        return byteStream.toByteArray()
    }

    fun toTransaction(senderBalanceId: String,
                      recipientAccountId: String,
                      networkParams: NetworkParams): Transaction {
        val zeroFee = SimpleFeeRecord(BigDecimal.ZERO, BigDecimal.ZERO)
        val fee = PaymentFee(zeroFee, zeroFee)

        val operation = PaymentOp(
                sourceBalanceID = PublicKeyFactory.fromBalanceId(senderBalanceId),
                destination = MovementDestination.Account(
                        accountID = PublicKeyFactory.fromAccountId(recipientAccountId)
                ),
                amount = networkParams.amountToPrecised(amount),
                subject = "",
                reference = salt.toString(),
                feeData = PaymentFeeData(
                        sourceFee = fee.senderFee.toXdrFee(networkParams),
                        destinationFee = fee.recipientFee.toXdrFee(networkParams),
                        sourcePaysForDest = false,
                        ext = PaymentFeeData.PaymentFeeDataExt.EmptyVersion()
                ),
                // TODO: Figure out
                securityType = 0,
                ext = PaymentOp.PaymentOpExt.EmptyVersion()
        )

        val transaction = TransactionBuilder(networkParams, sourceAccountId)
                .addOperation(Operation.OperationBody.Payment(operation))
                .setSalt(salt)
                .setTimeBounds(timeBounds)
                .build()

        transaction.addSignature(signature)

        return transaction
    }

    companion object {
        private val STRING_CHARSET = Charsets.UTF_8

        fun fromTransaction(transaction: Transaction,
                            assetCode: String): RedemptionRequest {
            val op = (transaction
                    .operations
                    .firstOrNull()
                    ?.body
                    as? Operation.OperationBody.Payment)
                    ?.paymentOp
                    ?.takeIf { transaction.operations.size == 1 }
                    ?: throw IllegalArgumentException(
                            "Redemption transaction must have only a single payment operation"
                    )

            val sourceAccountId = (transaction.sourceAccountId as? PublicKey.KeyTypeEd25519)
                    ?.ed25519
                    ?.wrapped
                    ?: throw IllegalArgumentException("Unknown source account public key type")

            val signature = transaction
                    .signatures
                    .firstOrNull()
                    .takeIf { transaction.signatures.size == 1 }
                    ?: throw IllegalArgumentException(
                            "Redemption transaction must have only a single signature"
                    )

            val salt = transaction.salt

            if (op.reference.toLongOrNull() != salt) {
                throw IllegalArgumentException("Redemption transaction salt must be equal " +
                        "to the payment operation reference")
            }

            return RedemptionRequest(
                    sourceAccountId = Base32Check.encodeAccountId(
                            sourceAccountId
                    ),
                    assetCode = assetCode,
                    salt = transaction.salt,
                    timeBounds = transaction.timeBounds,
                    amount = transaction.networkParams.amountFromPrecised(op.amount),
                    signature = signature
            )
        }

        fun fromSerialized(networkParams: NetworkParams,
                           serializedRequest: ByteArray): RedemptionRequest {
            try {
                DataInputStream(ByteArrayInputStream(serializedRequest)).use { stream ->
                    return RedemptionRequest(
                            sourceAccountId = Base32Check.encodeAccountId(
                                    ByteArray(32).also { stream.read(it) }
                            ),
                            assetCode = String(
                                    ByteArray(stream.readInt()).also { stream.read(it) },
                                    STRING_CHARSET
                            ),
                            amount = networkParams.amountFromPrecised(stream.readLong()),
                            salt = stream.readLong(),
                            timeBounds = TimeBounds(
                                    minTime = stream.readLong(),
                                    maxTime = stream.readLong()
                            ),
                            signature = DecoratedSignature(
                                    hint = XdrByteArrayFixed4(
                                            ByteArray(4).also { stream.read(it) }
                                    ),
                                    signature = ByteArray(stream.available()).also { stream.read(it) }
                            )
                    )
                }
            } catch (e: Exception) {
                throw RedemptionRequestFormatException(e)
            }
        }
    }
}