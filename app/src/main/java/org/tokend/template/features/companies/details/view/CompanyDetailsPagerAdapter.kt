package org.tokend.template.features.companies.details.view

import android.content.Context
import android.support.v4.app.FragmentManager
import org.tokend.template.R
import org.tokend.template.data.model.CompanyRecord
import org.tokend.template.fragments.FragmentFactory
import org.tokend.template.view.BaseFragmentPagerAdapter

class CompanyDetailsPagerAdapter(
        company: CompanyRecord,
        context: Context,
        fragmentManager: FragmentManager
) : BaseFragmentPagerAdapter(fragmentManager) {
    private val fragmentFactory = FragmentFactory()

    override val pages = mutableListOf(
            Page(
                    fragmentFactory.getBalancesFragment(
                            withToolbar = false,
                            companyId = company.id
                    ),
                    context.getString(R.string.my_balances),
                    BALANCES_PAGE
            ),
            Page(
                    fragmentFactory.getMarketplaceFragment(false, company.id),
                    context.getString(R.string.shop_title),
                    SHOP_PAGE
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
    }
}