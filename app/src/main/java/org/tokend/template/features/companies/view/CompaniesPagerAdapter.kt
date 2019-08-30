package org.tokend.template.features.companies.view

import android.content.Context
import android.support.v4.app.FragmentManager
import org.tokend.template.R
import org.tokend.template.view.BaseFragmentPagerAdapter

class CompaniesPagerAdapter(context: Context,
                            fragmentManager: FragmentManager
) : BaseFragmentPagerAdapter(fragmentManager) {
    override val pages = listOf(
            Page(
                    ClientCompaniesFragment(),
                    context.getString(R.string.my_companies_tab),
                    MY_COMPANIES_PAGE
            ),
            Page(
                    AllCompaniesFragment(),
                    context.getString(R.string.all_companies_tab),
                    ALL_COMPANIES_PAGE
            )
    )

    override fun getItem(position: Int): CompaniesListFragment? {
        return super.getItem(position) as? CompaniesListFragment
    }

    companion object {
        const val MY_COMPANIES_PAGE = 1L
        const val ALL_COMPANIES_PAGE = 2L
    }
}