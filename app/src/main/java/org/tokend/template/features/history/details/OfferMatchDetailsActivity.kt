package org.tokend.template.features.history.details

import androidx.core.content.ContextCompat
import org.tokend.template.R
import org.tokend.template.features.assets.model.Asset
import org.tokend.template.features.assets.model.SimpleAsset
import org.tokend.template.features.history.model.BalanceChange
import org.tokend.template.features.history.model.details.BalanceChangeCause
import org.tokend.template.view.details.DetailsItem

open class OfferMatchDetailsActivity : BalanceChangeDetailsActivity() {
    private lateinit var baseAsset: Asset
    private lateinit var quoteAsset: Asset

    override fun displayDetails(item: BalanceChange) {
        super.displayDetails(item)

        val details = item.cause as? BalanceChangeCause.MatchedOffer

        if (details == null) {
            finishWithError(IllegalStateException("Invalid item cause type"))
            return
        }

        baseAsset = SimpleAsset(details.baseAssetCode).orMoreDetailed()
        quoteAsset = SimpleAsset(details.quoteAssetCode).orMoreDetailed()

        displayChargedOrFunded(item, details)
        displayPrice(details)
    }

    protected open fun displayPrice(cause: BalanceChangeCause.MatchedOffer) {
        val formattedPrice = amountFormatter
                .formatAssetAmount(cause.price, quoteAsset)

        val priceString = getString(R.string.template_price_one_equals,
                baseAsset.name ?: baseAsset.code, formattedPrice)

        adapter.addData(
                DetailsItem(
                        text = priceString,
                        hint = getString(R.string.price),
                        icon = ContextCompat.getDrawable(this, R.drawable.ic_price)
                )
        )
    }

    protected open fun displayChargedOrFunded(item: BalanceChange,
                                              cause: BalanceChangeCause.MatchedOffer) {
        val (total, fee, assetCode) =
                if (item.isReceived == true)
                    cause.charged.let {
                        Triple(it.amount + it.fee.total, it.fee, it.assetCode)
                    }
                else
                    cause.funded.let {
                        Triple(it.amount - it.fee.total, it.fee, it.assetCode)
                    }

        val asset = if (baseAsset.code == assetCode) baseAsset else quoteAsset

        adapter.addData(
                DetailsItem(
                        text = amountFormatter.formatAssetAmount(
                                total, asset,
                                withAssetName = true
                        ),
                        hint =
                        if (item.isReceived == true)
                            getString(R.string.charged)
                        else
                            getString(R.string.received),
                        icon = ContextCompat.getDrawable(this, R.drawable.ic_coins)
                )
        )

        if (fee.total.signum() > 0) {
            adapter.addData(
                    DetailsItem(
                            text = amountFormatter.formatAssetAmount(
                                    fee.total, asset,
                                    withAssetName = true
                            ),
                            hint = getString(R.string.tx_fee)
                    )
            )
        }
    }
}