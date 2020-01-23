package org.tokend.template.features.companies.details.view

import android.content.Context
import android.support.v4.app.FragmentManager
import org.tokend.template.R
import org.tokend.template.features.companies.model.CompanyRecord
import org.tokend.template.features.assets.buy.marketplace.view.MarketplaceFragment
import org.tokend.template.features.assets.view.ExploreAssetsFragment
import org.tokend.template.features.dashboard.balances.view.BalancesFragment
import org.tokend.template.view.BaseFragmentPagerAdapter

class CompanyDetailsPagerAdapter(
        company: CompanyRecord,
        context: Context,
        fragmentManager: FragmentManager
) : BaseFragmentPagerAdapter(fragmentManager) {
    override val pages = mutableListOf(
            Page(
                    BalancesFragment.newInstance(BalancesFragment.getBundle(
                            allowToolbar = false,
                            companyId = company.id
                    )),
                    context.getString(R.string.my_balances),
                    BALANCES_PAGE
            ),
            Page(
                    MarketplaceFragment.newInstance(MarketplaceFragment.getBundle(
                            allowToolbar = false,
                            companyId = company.id
                    )),
                    context.getString(R.string.shop_title),
                    SHOP_PAGE
            ),
            Page(
                    ExploreAssetsFragment.newInstance(ExploreAssetsFragment.getBundle(
                            allowToolbar = false,
                            ownerAccountId = company.id
                    )),
                    context.getString(R.string.company_all_assets),
                    ASSETS_PAGE
            )
    ).apply {
        if (company.descriptionMd != null) {
            add(Page(
                    CompanyDescriptionFragment.newInstance(
                            CompanyDescriptionFragment.getBundle(company)
                    ),
                    context.getString(R.string.details),
                    DESCRIPTION_PAGE
            ))
        }
    }

    companion object {
        const val BALANCES_PAGE = 1L
        const val SHOP_PAGE = 2L
        const val DESCRIPTION_PAGE = 3L
        const val ASSETS_PAGE = 4L
    }
}