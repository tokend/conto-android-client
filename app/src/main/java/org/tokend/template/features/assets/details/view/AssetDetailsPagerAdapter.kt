package org.tokend.template.features.assets.details.view

import android.content.Context
import androidx.fragment.app.FragmentManager
import org.tokend.template.R
import org.tokend.template.features.assets.details.refund.view.AssetRefundFragment
import org.tokend.template.features.assets.details.secondarymarket.view.AssetSecondaryMarketFragment
import org.tokend.template.features.assets.model.AssetRecord
import org.tokend.template.view.BaseFragmentPagerAdapter

class AssetDetailsPagerAdapter(asset: AssetRecord,
                               withRefund: Boolean,
                               withSecondaryMarket: Boolean,
                               context: Context,
                               fragmentManager: FragmentManager
) : BaseFragmentPagerAdapter(fragmentManager) {

    override val pages = mutableListOf(
            Page(
                    AssetDetailsFragment.newInstance(AssetDetailsFragment.getBundle(
                            asset = asset,
                            balanceCreation = true
                    )),
                    context.getString(R.string.asset_overview),
                    OVERVIEW_PAGE
            )
    ).apply {
        if (withRefund) {
            add(
                    Page(
                            AssetRefundFragment.newInstance(AssetRefundFragment.getBundle(
                                    assetCode = asset.code
                            )),
                            context.getString(R.string.asset_refund),
                            REFUND_PAGE
                    )
            )
        }

        if (withSecondaryMarket) {
            add(
                    Page(
                            AssetSecondaryMarketFragment.newInstance(AssetSecondaryMarketFragment.getBundle(
                                    asset = asset
                            )),
                            context.getString(R.string.asset_secondary_market),
                            SECONDARY_MARKET_PAGE
                    )
            )
        }
    }

    companion object {
        /**
         * Page that contains view for transition.
         */
        const val DETAILS_PAGE_POSITION = 0

        const val OVERVIEW_PAGE = 1L
        const val REFUND_PAGE = 2L
        const val SECONDARY_MARKET_PAGE = 3L
    }
}