package org.tokend.template.features.nfcpayment.model

import org.tokend.sdk.utils.extentions.encodeHexString
import org.tokend.template.data.model.Asset
import org.tokend.wallet.NetworkParams
import java.io.Serializable
import java.math.BigDecimal

open class PosPaymentRequest(
        val amount: BigDecimal,
        val asset: Asset,
        val networkParams: NetworkParams,
        val reference: ByteArray,
        val destinationBalanceId: String
): Serializable {
    constructor(asset: Asset,
                networkParams: NetworkParams,
                rawRequest: RawPosPaymentRequest) : this(
            amount = networkParams.amountFromPrecised(rawRequest.precisedAmount),
            asset = asset,
            networkParams = networkParams,
            reference = rawRequest.reference,
            destinationBalanceId = rawRequest.destinationBalanceId
    )

    val referenceString: String
        get() = reference.encodeHexString()
}