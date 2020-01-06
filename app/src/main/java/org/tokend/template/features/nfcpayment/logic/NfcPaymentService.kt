package org.tokend.template.features.nfcpayment.logic

import android.nfc.cardemulation.HostApduService
import android.os.Build
import android.os.Bundle
import android.support.annotation.RequiresApi
import android.util.Log

@RequiresApi(Build.VERSION_CODES.KITKAT)
class NfcPaymentService: HostApduService() {
    override fun onDeactivated(reason: Int) {

    }

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        Log.i("Oleg", "I'm alive! App is $application")
        return byteArrayOf(0x42, 0x42, 0x42)
    }
}