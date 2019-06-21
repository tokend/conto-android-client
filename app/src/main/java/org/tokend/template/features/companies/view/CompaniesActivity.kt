package org.tokend.template.features.companies.view

import android.content.res.Configuration
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.SimpleItemAnimator
import android.view.MenuItem
import io.reactivex.rxkotlin.addTo
import kotlinx.android.synthetic.main.activity_companies.*
import kotlinx.android.synthetic.main.fragment_polls.swipe_refresh
import kotlinx.android.synthetic.main.include_appbar_elevation.*
import kotlinx.android.synthetic.main.include_error_empty_view.*
import kotlinx.android.synthetic.main.toolbar.*
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.data.repository.CompaniesRepository
import org.tokend.template.features.companies.view.adapter.CompanyItemsAdapter
import org.tokend.template.features.companies.view.adapter.CompanyListItem
import org.tokend.template.util.Navigator
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.util.SearchUtil
import org.tokend.template.view.util.*

class CompaniesActivity : BaseActivity() {

    private val loadingIndicator = LoadingIndicatorManager(
            showLoading = { swipe_refresh.isRefreshing = true },
            hideLoading = { swipe_refresh.isRefreshing = false }
    )

    private val companiesRepository: CompaniesRepository
        get() = repositoryProvider.companies()

    private var searchItem: MenuItem? = null

    private lateinit var companiesAdapter: CompanyItemsAdapter
    private lateinit var layoutManager: GridLayoutManager

    private var filter: String? = null
        set(value) {
            if (value != field) {
                field = value
                onFilterChanged()
            }
        }

    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_companies)
        initToolbar()
        initSwipeRefresh()
        initList()

        subscribeToCompanies()
        update()
    }

    private fun initToolbar() {
        toolbar.title = getString(R.string.companies_title)
        ElevationUtil.initScrollElevation(companies_recycler_view, appbar_elevation_view)
        initMenu()
    }

    private fun initMenu() {
        toolbar.inflateMenu(R.menu.companies)

        val menu = toolbar.menu

        try {
            val searchItem = menu?.findItem(R.id.search)!!

            val searchManager = MenuSearchViewManager(searchItem, toolbar, compositeDisposable)

            searchManager.queryHint = getString(R.string.search)
            searchManager
                    .queryChanges
                    .compose(ObservableTransformers.defaultSchedulers())
                    .subscribe { newValue ->
                        filter = newValue.takeIf { it.isNotEmpty() }
                    }
                    .addTo(compositeDisposable)

            this.searchItem = searchItem
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun initSwipeRefresh() {
        swipe_refresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.accent))
        swipe_refresh.setOnRefreshListener { update(true) }
    }

    private fun initList() {
        layoutManager = GridLayoutManager(this, 1)
        companiesAdapter = CompanyItemsAdapter()
        updateListColumnsCount()

        companies_recycler_view.layoutManager = layoutManager
        companies_recycler_view.adapter = companiesAdapter
        (companies_recycler_view.itemAnimator as? SimpleItemAnimator)
                ?.supportsChangeAnimations = false

        error_empty_view.setEmptyDrawable(R.drawable.ic_briefcase)
        error_empty_view.observeAdapter(companiesAdapter, R.string.error_no_companies)
        error_empty_view.setEmptyViewDenial { companiesRepository.isNeverUpdated }

        companiesAdapter.registerAdapterDataObserver(
                ScrollOnTopItemUpdateAdapterObserver(companies_recycler_view)
        )
        companiesAdapter.onItemClick { _, _ ->
            Navigator.from(this).openMainActivity()
        }
    }

    private fun subscribeToCompanies() {
        companiesRepository
                .loadingSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { loadingIndicator.setLoading(it) }
                .addTo(compositeDisposable)

        companiesRepository
                .errorsSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { error ->
                    if (!companiesAdapter.hasData) {
                        error_empty_view.showError(error, errorHandlerFactory.getDefault()) {
                            update(true)
                        }
                    } else {
                        errorHandlerFactory.getDefault().handle(error)
                    }
                }
                .addTo(compositeDisposable)

        companiesRepository
                .itemsSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { displayCompanies() }
                .addTo(compositeDisposable)
    }

    private fun onFilterChanged() {
        displayCompanies()
    }

    private fun displayCompanies() {
        val items = companiesRepository
                .itemsList
                .map(::CompanyListItem)
                .let { items ->
                    filter?.let {
                        items.filter { item ->
                            SearchUtil.isMatchGeneralCondition(
                                    it,
                                    item.name
                            )
                        }
                    } ?: items
                }

        companiesAdapter.setData(items)
    }

    private fun update(force: Boolean = false) {
        if (!force) {
            companiesRepository.updateIfNotFresh()
        } else {
            companiesRepository.update()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        updateListColumnsCount()
    }

    private fun updateListColumnsCount() {
        layoutManager.spanCount = ColumnCalculator.getColumnCount(this)
        companiesAdapter.drawDividers = layoutManager.spanCount == 1
    }

    override fun onBackPressed() {
        if (searchItem?.isActionViewExpanded == true) {
            searchItem?.collapseActionView()
        } else moveTaskToBack(true)
    }

    companion object {
        const val ID = 1141L
    }
}