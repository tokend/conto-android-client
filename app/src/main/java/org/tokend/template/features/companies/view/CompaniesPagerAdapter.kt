package org.tokend.template.features.companies.view

import android.content.Context
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import org.tokend.template.R

class CompaniesPagerAdapter(context: Context,
                            fragmentManager: FragmentManager
) : FragmentPagerAdapter(fragmentManager) {
    private val pages = arrayListOf(
            Triple(
                    ClientCompaniesFragment(),
                    context.getString(R.string.my_companies_tab),
                    MY_COMPANIES_PAGE
            ),
            Triple(
                    AllCompaniesFragment(),
                    context.getString(R.string.all_companies_tab),
                    ALL_COMPANIES_PAGE
            )
    )

    override fun getItem(position: Int): CompaniesListFragment? {
        return pages.getOrNull(position)?.first
    }

    override fun getPageTitle(position: Int): CharSequence {
        return pages.getOrNull(position)?.second ?: ""
    }

    override fun getItemId(position: Int): Long {
        return pages[position].third
    }

    override fun getCount(): Int = pages.size

    fun getIndexOf(id: Long): Int {
        return pages.indexOfFirst { it.third == id }
    }

    companion object {
        const val MY_COMPANIES_PAGE = 1L
        const val ALL_COMPANIES_PAGE = 2L
    }
}