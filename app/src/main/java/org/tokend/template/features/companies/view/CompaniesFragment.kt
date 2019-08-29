package org.tokend.template.features.companies.view

import android.annotation.SuppressLint
import android.os.Bundle
import android.support.design.widget.TabLayout
import android.support.v4.view.ViewPager
import android.support.v7.widget.Toolbar
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import io.reactivex.subjects.BehaviorSubject
import kotlinx.android.synthetic.main.appbar_with_tabs.*
import kotlinx.android.synthetic.main.fragment_pager.*
import kotlinx.android.synthetic.main.toolbar.*
import org.tokend.template.R
import org.tokend.template.data.repository.ClientCompaniesRepository
import org.tokend.template.fragments.BaseFragment
import org.tokend.template.fragments.ToolbarProvider
import org.tokend.template.view.util.MenuSearchViewManager

class CompaniesFragment : BaseFragment(), ToolbarProvider {
    override val toolbarSubject: BehaviorSubject<Toolbar> = BehaviorSubject.create()

    private val clientCompaniesRepository: ClientCompaniesRepository
        get() = repositoryProvider.clientCompanies()

    private lateinit var adapter: CompaniesPagerAdapter

    private var searchMenuItem: MenuItem? = null
    private var filterChangesSubject = BehaviorSubject.create<String>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_pager, container, false)
    }

    override fun onInitAllowed() {
        initToolbar()
        initPager()

        switchToAllIfNeeded()
    }

    private fun initToolbar() {
        toolbar.title = getString(R.string.companies_title)
        toolbarSubject.onNext(toolbar)
        initMenu()
    }

    private fun initMenu() {
        toolbar.inflateMenu(R.menu.explore)
        val menu = toolbar.menu

        val searchItem = menu.findItem(R.id.search) ?: return
        this.searchMenuItem = searchItem

        try {
            val searchManager = MenuSearchViewManager(searchItem, toolbar, compositeDisposable)

            searchManager.queryHint = getString(R.string.search)
            searchManager
                    .queryChanges
                    .subscribe(filterChangesSubject)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("RestrictedApi")
    private fun initPager() {
        adapter = CompaniesPagerAdapter(requireContext(), childFragmentManager)
        pager.adapter = adapter
        pager.offscreenPageLimit = adapter.count
        appbar_tabs.setupWithViewPager(pager)
        appbar_tabs.tabGravity = TabLayout.GRAVITY_FILL
        appbar_tabs.tabMode = TabLayout.MODE_FIXED

        // Filter.
        val onPageSelected = { pagePosition: Int ->
            adapter
                    .getItem(pagePosition)
                    ?.observeFilterChanges(filterChangesSubject)
        }

        pager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageSelected(position: Int) {
                onPageSelected(position)
            }

            override fun onPageScrollStateChanged(state: Int) {}

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
        })

        onPageSelected(0)
    }

    private fun switchToAllIfNeeded() {
        if (clientCompaniesRepository.itemsList.isEmpty()) {
            pager.currentItem = adapter.getIndexOf(CompaniesPagerAdapter.ALL_COMPANIES_PAGE)
        }
    }

    override fun onBackPressed(): Boolean {
        return if (searchMenuItem?.isActionViewExpanded == true) {
            searchMenuItem?.collapseActionView()
            false
        } else {
            super.onBackPressed()
        }
    }

    companion object {
        val ID = "companies".hashCode().toLong()
    }
}