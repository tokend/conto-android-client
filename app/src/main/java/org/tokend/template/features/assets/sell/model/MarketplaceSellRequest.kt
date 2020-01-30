package org.tokend.template.features.assets.sell.model

import org.tokend.template.data.model.Asset
import org.tokend.template.data.model.history.SimpleFeeRecord
import org.tokend.template.features.send.model.PaymentFee
import org.tokend.template.features.send.model.PaymentRecipient
import org.tokend.template.features.send.model.PaymentRequest
import java.io.Serializable
import java.math.BigDecimal

class MarketplaceSellRequest(
        val amount: BigDecimal,
        val asset: Asset,
        val price: BigDecimal,
        val priceAsset: Asset,
        val paymentMethods: Collection<MarketplaceSellPaymentMethod>,
        val sourceAccountId: String,
        val sourceBalanceId: String,
        val marketplaceAccountId: String
) : Serializable {

    fun getPaymentToMarketplace(): PaymentRequest {
        return PaymentRequest(
                amount = amount,
                asset = asset,
                fee = PaymentFee(SimpleFeeRecord.ZERO, SimpleFeeRecord.ZERO, false),
                paymentSubject = null,
                actualPaymentSubject = null,
                recipient = PaymentRecipient(marketplaceAccountId),
                senderAccountId = sourceAccountId,
                senderBalanceId = sourceBalanceId
        )
    }
}