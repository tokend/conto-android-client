package org.tokend.template.features.dashboard.view

import android.content.Context
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import org.tokend.template.R
import org.tokend.template.fragments.FragmentFactory

class DashboardPagerAdapter(context: Context,
                            fragmentManager: FragmentManager
) : FragmentPagerAdapter(fragmentManager) {
    private val fragmentFactory = FragmentFactory()

    private val pages = arrayListOf(
            Triple(
                    fragmentFactory.getBalancesFragment(),
                    context.getString(R.string.balances_screen_title),
                    1L
            ),
            Triple(
                    fragmentFactory.getAllAtomicSwapAsksFragment(),
                    context.getString(R.string.shop_title),
                    2L
            )
    )

    override fun getItem(position: Int): Fragment? {
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
}