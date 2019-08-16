package org.tokend.template.features.assets.buy.model

import org.tokend.sdk.api.integrations.fiat.model.FiatInvoiceAttributes

data class FiatInvoice(
        val paymentFormUrl: String
) {
    constructor(source: FiatInvoiceAttributes): this(
            paymentFormUrl = source.paymentUrl
    )
}