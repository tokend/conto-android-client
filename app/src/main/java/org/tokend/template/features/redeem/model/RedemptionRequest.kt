package org.tokend.template.features.redeem.model

import org.tokend.wallet.Base32Check
import org.tokend.wallet.NetworkParams
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
        }
    }
}