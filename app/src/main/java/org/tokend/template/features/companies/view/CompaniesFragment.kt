package org.tokend.template.features.companies.view

import android.content.res.Configuration
import android.os.Bundle
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.Toolbar
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import io.reactivex.subjects.BehaviorSubject
import kotlinx.android.synthetic.main.fragment_polls.*
import kotlinx.android.synthetic.main.include_appbar_elevation.*
import kotlinx.android.synthetic.main.toolbar.*
import org.tokend.template.R
import org.tokend.template.features.companies.view.adapter.CompanyItemsAdapter
import org.tokend.template.fragments.BaseFragment
import org.tokend.template.fragments.ToolbarProvider
import org.tokend.template.view.util.ColumnCalculator
import org.tokend.template.view.util.ElevationUtil

class CompaniesFragment : BaseFragment(), ToolbarProvider {

    override val toolbarSubject: BehaviorSubject<Toolbar> = BehaviorSubject.create()

    private var searchItem: MenuItem? = null

    private lateinit var companiesAdapter: CompanyItemsAdapter
    private lateinit var layoutManager: GridLayoutManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_companies, container, false)
    }

    override fun onInitAllowed() {
        initToolbar()
    }

    private fun initToolbar() {
        toolbar.title = getString(R.string.companies_title)
        ElevationUtil.initScrollElevation(polls_list, appbar_elevation_view)
        toolbarSubject.onNext(toolbar)
    }

    private fun initCompaniesList() {

    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        updateListColumnsCount()
    }

    private fun updateListColumnsCount() {
        layoutManager.spanCount = ColumnCalculator.getColumnCount(requireActivity())
        companiesAdapter.drawDividers = layoutManager.spanCount == 1
    }

    override fun onBackPressed(): Boolean {
        return searchItem?.isActionViewExpanded == false.also {
            searchItem?.collapseActionView()
        }
    }
}