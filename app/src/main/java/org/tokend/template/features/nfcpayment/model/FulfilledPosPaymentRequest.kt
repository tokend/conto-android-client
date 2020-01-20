package org.tokend.template.features.nfcpayment.model

class FulfilledPosPaymentRequest(
        val sourceBalanceId: String,
        sourceRequest: PosPaymentRequest
) : PosPaymentRequest(sourceRequest.amount, sourceRequest.asset, sourceRequest.networkParams,
        sourceRequest.reference, sourceRequest.destinationBalanceId)