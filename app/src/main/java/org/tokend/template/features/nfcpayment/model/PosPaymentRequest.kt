package org.tokend.template.features.nfcpayment.model

import org.tokend.template.data.model.Asset
import java.math.BigDecimal

class PosPaymentRequest(
        val amount: BigDecimal,
        val asset: Asset,
        val reference: ByteArray,
        val destinationBalanceId: String
) {
    constructor(amount: BigDecimal,
                asset: Asset,
                rawRequest: RawPosPaymentRequest) : this(
            amount = amount,
            asset = asset,
            reference = rawRequest.reference,
            destinationBalanceId = rawRequest.destinationBalanceId
    )
}