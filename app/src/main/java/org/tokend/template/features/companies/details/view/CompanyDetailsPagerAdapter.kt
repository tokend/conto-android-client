package org.tokend.template.features.companies.details.view

import android.content.Context
import android.support.v4.app.FragmentManager
import org.tokend.template.R
import org.tokend.template.fragments.FragmentFactory
import org.tokend.template.view.BaseFragmentPagerAdapter

class CompanyDetailsPagerAdapter(
        companyId: String,
        context: Context,
        fragmentManager: FragmentManager
) : BaseFragmentPagerAdapter(fragmentManager) {
    private val fragmentFactory = FragmentFactory()

    override val pages = listOf(
            Page(
                    fragmentFactory.getBalancesFragment(
                            withToolbar = false,
                            companyId = companyId
                    ),
                    context.getString(R.string.my_balances),
                    BALANCES_PAGE
            ),
            Page(
                    fragmentFactory.getAllAtomicSwapAsksFragment(companyId),
                    context.getString(R.string.shop_title),
                    SHOP_PAGE
            )
    )

    companion object {
        const val BALANCES_PAGE = 1L
        const val SHOP_PAGE = 2L
    }
}