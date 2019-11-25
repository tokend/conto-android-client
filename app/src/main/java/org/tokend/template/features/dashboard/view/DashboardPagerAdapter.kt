package org.tokend.template.features.dashboard.view

import android.content.Context
import android.support.v4.app.FragmentManager
import org.tokend.template.R
import org.tokend.template.features.assets.buy.marketplace.view.MarketplaceFragment
import org.tokend.template.features.dashboard.balances.view.BalancesFragment
import org.tokend.template.view.BaseFragmentPagerAdapter

class DashboardPagerAdapter(context: Context,
                            fragmentManager: FragmentManager
) : BaseFragmentPagerAdapter(fragmentManager) {

    override val pages = listOf(
            Page(
                    BalancesFragment.newInstance(BalancesFragment.getBundle(
                            allowToolbar = false
                    )),
                    context.getString(R.string.balances_screen_title),
                    BALANCES_PAGE
            ),
            Page(
                    MarketplaceFragment.newInstance(MarketplaceFragment.getBundle(
                            allowToolbar = false,
                            companyId = null
                    )),
                    context.getString(R.string.shop_title),
                    SHOP_PAGE
            )
    )

    companion object {
        const val BALANCES_PAGE = 1L
        const val SHOP_PAGE = 2L
    }
}