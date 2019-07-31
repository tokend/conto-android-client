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
import org.tokend.template.App
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.data.model.CompanyRecord
import org.tokend.template.data.repository.ClientCompaniesRepository
import org.tokend.template.features.companies.view.adapter.CompanyItemsAdapter
import org.tokend.template.features.companies.view.adapter.CompanyListItem
import org.tokend.template.util.Navigator
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.util.SearchUtil
import org.tokend.template.view.ErrorEmptyView
import org.tokend.template.view.util.*

class CompaniesActivity : BaseActivity() {

    private val loadingIndicator = LoadingIndicatorManager(
            showLoading = { swipe_refresh.isRefreshing = true },
            hideLoading = { swipe_refresh.isRefreshing = false }
    )

    private val clientCompaniesRepository: ClientCompaniesRepository
        get() = repositoryProvider.clientCompanies()

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

        menu?.findItem(R.id.add)?.setOnMenuItemClickListener {
            addCompany()
            true
        }

        menu?.findItem(R.id.sign_out)?.setOnMenuItemClickListener {
            signOutWithConfirmation()
            true
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
        error_empty_view.observeAdapter(
                companiesAdapter,
                R.string.error_no_companies,
                ErrorEmptyView.ButtonAction(getString(R.string.add), this::addCompany)
        )
        error_empty_view.setEmptyViewDenial { clientCompaniesRepository.isNeverUpdated }

        companiesAdapter.registerAdapterDataObserver(
                ScrollOnTopItemUpdateAdapterObserver(companies_recycler_view)
        )
        companiesAdapter.onItemClick { _, item ->
            item.source?.also(this::onCompanySelected)
        }
    }

    private fun subscribeToCompanies() {
        clientCompaniesRepository
                .loadingSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { loadingIndicator.setLoading(it) }
                .addTo(compositeDisposable)

        clientCompaniesRepository
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

        clientCompaniesRepository
                .itemsSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { onCompaniesLoaded() }
                .addTo(compositeDisposable)
    }

    private fun onFilterChanged() {
        displayCompanies()
    }

    private fun onCompaniesLoaded() {
        if (clientCompaniesRepository.itemsList.size == 1) {
            val company = clientCompaniesRepository.itemsList.first()
            onCompanySelected(company)
            return
        }

        clientCompaniesRepository
                .itemsList
                .find { it.id == session.lastCompanyId }
                ?.let { company ->
                    onCompanySelected(company)
                    return
                }

        displayCompanies()
    }

    private fun displayCompanies() {
        val items = clientCompaniesRepository
                .itemsList
                .map { CompanyListItem(it) }
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
            clientCompaniesRepository.updateIfNotFresh()
        } else {
            clientCompaniesRepository.update()
        }
    }

    private fun onCompanySelected(company: CompanyRecord) {
        session.setCompany(company)
        Navigator.from(this).toCompanyLoading()
    }

    private fun addCompany() {
        Navigator.from(this).openExploreCompanies()
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        updateListColumnsCount()
    }

    private fun updateListColumnsCount() {
        layoutManager.spanCount = ColumnCalculator.getColumnCount(this)
        companiesAdapter.drawDividers = layoutManager.spanCount == 1
    }

    private fun signOutWithConfirmation() {
        SignOutDialogFactory.getTunedDialog(this) {
            (application as? App)?.signOut(this)
        }.show()
    }

    override fun onBackPressed() {
        if (searchItem?.isActionViewExpanded == true) {
            searchItem?.collapseActionView()
        } else {
            super.onBackPressed()
        }
    }

    companion object {
        const val ID = 1141L
    }
}