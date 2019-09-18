package org.tokend.template.features.assets.buy.marketplace.view.adapter

import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import org.tokend.template.R
import org.tokend.template.view.adapter.base.BaseViewHolder
import org.tokend.template.view.adapter.base.SimpleItemClickListener
import org.tokend.template.view.util.CircleLogoUtil
import org.tokend.template.view.util.formatter.AmountFormatter

class SinglePriceAtomicSwapAskItemViewHolder(
        view: View,
        private val amountFormatter: AmountFormatter
) : BaseViewHolder<MarketplaceOfferListItem>(view) {
    private val logoImageView: ImageView = view.findViewById(R.id.asset_logo_image_view)
    private val assetNameTextView: TextView = view.findViewById(R.id.asset_name_text_view)
    private val priceTextView: TextView = view.findViewById(R.id.price_text_view)
    private val availableTextView: TextView = view.findViewById(R.id.available_text_view)
    private val companyNameTextView: TextView = view.findViewById(R.id.company_name_text_view)
    private val buyButton: Button = view.findViewById(R.id.buy_btn)

    override fun bind(item: MarketplaceOfferListItem) {
        CircleLogoUtil.setAssetLogo(logoImageView, item.asset)
        assetNameTextView.text = item.asset.name ?: item.asset.code
        priceTextView.text = view.context.getString(
                R.string.template_price,
                amountFormatter.formatAssetAmount(
                        item.price,
                        item.priceAsset,
                        withAssetCode = true
                )
        )
        availableTextView.text = view.context.getString(
                R.string.template_amount_available_for_buy,
                amountFormatter.formatAssetAmount(
                        item.available,
                        item.asset,
                        withAssetCode = false,
                        abbreviation = true
                )
        )
        if (item.companyName != null) {
            companyNameTextView.text = item.companyName
            companyNameTextView.visibility = View.VISIBLE
        } else {
            companyNameTextView.visibility = View.GONE
        }
    }

    override fun bind(item: MarketplaceOfferListItem,
                      clickListener: SimpleItemClickListener<MarketplaceOfferListItem>?) {
        super.bind(item, clickListener)
        buyButton.setOnClickListener { clickListener?.invoke(view, item) }
    }
}