package org.tokend.template.features.nfcpayment.model

sealed class PosToClientCommand {
    class SelectAid(aid: ByteArray) : PosToClientCommand() {
        override val data = HEADER + aid.size.toByte() + aid

        companion object {
            private val HEADER = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00)

            fun isIt(commandBytes: ByteArray) = commandBytes.size > HEADER.size + 1
                    && commandBytes.sliceArray(HEADER.indices).contentEquals(HEADER)

            fun fromBytes(commandBytes: ByteArray) = SelectAid(
                    commandBytes.sliceArray(HEADER.size + 1 until commandBytes.size)
            )
        }
    }

    class SendPaymentRequest(val request: RawPosPaymentRequest) : PosToClientCommand() {
        override val data = HEADER + request.serialize()

        companion object {
            private val HEADER = byteArrayOf(0x01)

            fun isIt(commandBytes: ByteArray) = commandBytes.size > HEADER.size
                    && commandBytes.sliceArray(HEADER.indices).contentEquals(HEADER)

            fun fromBytes(commandBytes: ByteArray) = SendPaymentRequest(
                    RawPosPaymentRequest.fromSerialized(
                            commandBytes.sliceArray(HEADER.size until commandBytes.size))
            )
        }
    }

    object Ok : PosToClientCommand() {
        private val HEADER = byteArrayOf(0x20)

        fun isIt(commandBytes: ByteArray) = commandBytes.contentEquals(HEADER)

        override val data = HEADER
    }

    object Error : PosToClientCommand() {
        private val HEADER = byteArrayOf(0x40)

        fun isIt(commandBytes: ByteArray) = commandBytes.contentEquals(HEADER)

        override val data = HEADER
    }

    abstract val data: ByteArray

    companion object {
        fun fromBytes(commandBytes: ByteArray): PosToClientCommand {
            return when {
                SelectAid.isIt(commandBytes) -> SelectAid.fromBytes(commandBytes)
                SendPaymentRequest.isIt(commandBytes) -> SendPaymentRequest.fromBytes(commandBytes)
                Ok.isIt(commandBytes) -> Ok
                Error.isIt(commandBytes) -> Error
                else -> throw IllegalArgumentException("Unknown command")
            }
        }
    }
}