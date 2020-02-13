package org.tokend.template.features.redeem.logic

import android.nfc.cardemulation.HostApduService
import android.os.Build
import android.os.Bundle
import android.support.annotation.RequiresApi
import org.tokend.sdk.utils.extentions.decodeHex
import org.tokend.template.features.redeem.logic.NfcRedemptionService.Companion.broadcast
import org.tokend.template.features.redeem.logic.NfcRedemptionService.Companion.cancelBroadcast

/**
 * Broadcasts redemption request over NFC.
 *
 * If there is a redemption it broadcasts 0x01 followed by serialized request, 0x00 otherwise
 *
 * @see broadcast
 * @see cancelBroadcast
 */
@RequiresApi(Build.VERSION_CODES.KITKAT)
class NfcRedemptionService : HostApduService() {
    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        return if (isAidSelected(commandApdu)) {
            getResponse()
        } else {
            byteArrayOf()
        }
    }

    private fun isAidSelected(commandApdu: ByteArray): Boolean {
        return commandApdu.size > SELECT_AID_HEADER.size &&
                SELECT_AID_HEADER.contentEquals(commandApdu.sliceArray(SELECT_AID_HEADER.indices))
    }

    private fun getResponse(): ByteArray {
        val request = serializedRedemptionRequest
        return if (request != null)
            byteArrayOf(1) + request
        else
            byteArrayOf(0)
    }

    override fun onDeactivated(reason: Int) {}

    companion object {
        private val SELECT_AID_HEADER = "00A40400".decodeHex()

        private var serializedRedemptionRequest: ByteArray? = null

        @RequiresApi(Build.VERSION_CODES.KITKAT)
        fun broadcast(serializedRedemptionRequest: ByteArray) {
            this.serializedRedemptionRequest = serializedRedemptionRequest
        }

        @RequiresApi(Build.VERSION_CODES.KITKAT)
        fun cancelBroadcast() {
            this.serializedRedemptionRequest = null
        }
    }
}