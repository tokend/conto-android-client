package org.tokend.template.features.nfcpayment.model

import org.tokend.sdk.utils.extentions.encodeHexString

sealed class ClientToPosResponse {
    object Empty: ClientToPosResponse() {
        fun isIt(responseBytes: ByteArray) = responseBytes.isEmpty()

        override val data = byteArrayOf()
    }

    object Ok : ClientToPosResponse() {
        private val HEADER = byteArrayOf(0x20)
        fun isIt(responseBytes: ByteArray) = responseBytes.contentEquals(HEADER)

        override val data = HEADER
    }

    class PaymentTransaction(val transactionEnvelopeXdr: ByteArray) : ClientToPosResponse() {
        override val data = HEADER + transactionEnvelopeXdr

        companion object {
            private val HEADER = byteArrayOf(0x22)

            fun isIt(responseBytes: ByteArray) =
                    responseBytes.size > HEADER.size
                            && responseBytes.sliceArray(HEADER.indices).contentEquals(
                            HEADER
                    )

            fun fromBytes(responseBytes: ByteArray): PaymentTransaction {
                return PaymentTransaction(
                        transactionEnvelopeXdr = responseBytes.sliceArray(HEADER.size until responseBytes.size)
                )
            }
        }
    }

    abstract val data: ByteArray

    companion object {
        fun fromBytes(responseBytes: ByteArray): ClientToPosResponse {
            return when {
                Empty.isIt(responseBytes) -> Empty
                Ok.isIt(responseBytes) -> Ok
                PaymentTransaction.isIt(responseBytes) -> PaymentTransaction.fromBytes(responseBytes)

                else -> throw IllegalArgumentException("Unknown response ${responseBytes.encodeHexString()}")
            }
        }
    }
}