package org.tokend.template.view

import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter

/**
 * Adapter for named pages with IDs
 */
abstract class BaseFragmentPagerAdapter(
        fragmentManager: FragmentManager
): FragmentPagerAdapter(fragmentManager) {
    data class Page(
            val content: Fragment,
            val title: String,
            val id: Long
    )

    abstract val pages: List<Page>

    override fun getItem(position: Int): Fragment? {
        return pages.getOrNull(position)?.content
    }

    override fun getPageTitle(position: Int): CharSequence {
        return pages.getOrNull(position)?.title ?: ""
    }

    override fun getItemId(position: Int): Long {
        return pages[position].id
    }

    override fun getCount(): Int = pages.size

    open fun getIndexOf(id: Long): Int {
        return pages.indexOfFirst { it.id == id }
    }
}