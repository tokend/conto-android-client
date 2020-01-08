package org.tokend.template.features.nfcpayment.model

import org.tokend.sdk.utils.extentions.encodeHexString
import org.tokend.wallet.Base32Check
import java.io.*
import java.security.SecureRandom

class RawPosPaymentRequest(
        val precisedAmount: Long,
        val assetCode: String,
        val destinationBalanceId: String,
        val reference: ByteArray = SecureRandom.getSeed(REFERENCE_SIZE)
) : Serializable {
    init {
        require(reference.size == REFERENCE_SIZE)
    }

    fun serialize(): ByteArray {
        val byteStream = ByteArrayOutputStream()

        DataOutputStream(byteStream).use { stream ->
            stream.writeByte(VERSION_BYTE)
            stream.writeLong(precisedAmount)
            assetCode.toByteArray(STRING_CHARSET).also {
                stream.writeInt(it.size)
                stream.write(it)
            }
            stream.write(Base32Check.decodeBalanceId(destinationBalanceId))
            stream.write(reference)
        }

        return byteStream.toByteArray()
    }

    companion object {
        private const val REFERENCE_SIZE = 32
        private val STRING_CHARSET = Charsets.UTF_8
        const val VERSION_BYTE = 1

        fun fromSerialized(serialized: ByteArray): RawPosPaymentRequest =
                DataInputStream(ByteArrayInputStream(serialized)).use { stream ->
                    val versionByte = stream.readByte()
                    if (versionByte != VERSION_BYTE.toByte()) {
                        throw IllegalArgumentException("Invalid version byte " +
                                byteArrayOf(versionByte).encodeHexString())
                    }

                    RawPosPaymentRequest(
                            precisedAmount = stream.readLong(),
                            assetCode = stream.readInt().let { assetCodeLength ->
                                require(assetCodeLength <= 64)
                                ByteArray(assetCodeLength).let { assetCodeBytes ->
                                    stream.read(assetCodeBytes)
                                    String(assetCodeBytes, STRING_CHARSET)
                                }
                            },
                            destinationBalanceId = Base32Check.encodeBalanceId(
                                    ByteArray(32).also { stream.read(it) }
                            ),
                            reference = ByteArray(REFERENCE_SIZE).also { stream.read(it) }
                    )
                }
    }
}