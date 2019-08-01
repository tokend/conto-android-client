package org.tokend.template.features.companies.view

import android.content.res.Configuration
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.widget.GridLayoutManager
import android.view.MenuItem
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.fragment_explore.*
import kotlinx.android.synthetic.main.include_appbar_elevation.*
import kotlinx.android.synthetic.main.include_error_empty_view.*
import kotlinx.android.synthetic.main.toolbar.*
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.data.repository.ClientCompaniesRepository
import org.tokend.template.data.repository.CompaniesRepository
import org.tokend.template.features.companies.add.logic.AddCompanyUseCase
import org.tokend.template.features.companies.view.adapter.CompanyItemsAdapter
import org.tokend.template.features.companies.view.adapter.CompanyListItem
import org.tokend.template.util.Navigator
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.util.SearchUtil
import org.tokend.template.view.util.*

class ExploreCompaniesActivity : BaseActivity() {

    private val loadingIndicator = LoadingIndicatorManager(
            showLoading = { swipe_refresh.isRefreshing = true },
            hideLoading = { swipe_refresh.isRefreshing = false }
    )

    private val companiesRepository: CompaniesRepository
        get() = repositoryProvider.companies()
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
        setContentView(R.layout.fragment_explore)
        initToolbar()
        initSwipeRefresh()
        initCompaniesList()

        subscribeToCompanies()
        update()
    }

    private fun initToolbar() {
        toolbar.title = getString(R.string.explore_companies_title)
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        toolbar.setNavigationOnClickListener { finish() }
        ElevationUtil.initScrollElevation(recycler_view, appbar_elevation_view)
        initMenu()
    }

    private fun initSwipeRefresh() {
        swipe_refresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.accent))
        swipe_refresh.setOnRefreshListener { update(force = true) }
    }

    private fun initCompaniesList() {
        val columns = ColumnCalculator.getColumnCount(this)

        layoutManager = GridLayoutManager(this, columns)
        companiesAdapter = CompanyItemsAdapter()

        recycler_view.layoutManager = layoutManager
        recycler_view.adapter = companiesAdapter

        error_empty_view.setEmptyDrawable(R.drawable.ic_briefcase)
        error_empty_view.observeAdapter(companiesAdapter, R.string.error_no_companies)
        error_empty_view.setEmptyViewDenial {
            companiesRepository.isNeverUpdated
                    || clientCompaniesRepository.isNeverUpdated
        }

        companiesAdapter.onItemClick { view, item ->
            if (!item.exist) {
                addCompanyWithConfirmation(item)
            }
        }

        companiesAdapter.registerAdapterDataObserver(
                ScrollOnTopItemUpdateAdapterObserver(recycler_view)
        )

        ElevationUtil.initScrollElevation(recycler_view, appbar_elevation_view)
    }

    private fun initMenu() {
        toolbar.inflateMenu(R.menu.explore_companies)
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

        menu?.findItem(R.id.scan)?.setOnMenuItemClickListener {
            Navigator.from(this).openCompanyAdd()
            true
        }
    }

    private var companiesDisposable: CompositeDisposable? = null

    private fun subscribeToCompanies() {
        companiesDisposable?.dispose()

        Observable.combineLatest(
                listOf(
                        companiesRepository.itemsSubject,
                        clientCompaniesRepository.itemsSubject
                )
        ) { true }
                .filter {
                    companiesRepository.isFresh && clientCompaniesRepository.isFresh
                }
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe {
                    displayCompanies()
                }
                .addTo(compositeDisposable)

        companiesRepository.loadingSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe {
                    loadingIndicator.setLoading(it, "companies")
                }
                .addTo(compositeDisposable)

        companiesRepository.errorsSubject
                .observeOn(AndroidSchedulers.mainThread())
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
    }

    private fun onFilterChanged() {
        displayCompanies()
    }

    private fun displayCompanies() {
        val clientCompanies = clientCompaniesRepository.itemsList
        val items = companiesRepository.itemsList
                .asSequence()
                .map { company ->
                    CompanyListItem(company, clientCompanies.contains(company))
                }
                .sortedWith(Comparator { o1, o2 ->
                    return@Comparator o1.exist.compareTo(o2.exist)
                            .takeIf { it != 0 }
                            ?: assetCodeComparator.compare(o1.name, o2.name)
                })
                .toList()
                .let { items ->
                    filter?.let {
                        items.filter { item ->
                            SearchUtil.isMatchGeneralCondition(it, item.name)
                        }
                    } ?: items
                }

        companiesAdapter.setData(items)
    }

    private fun addCompanyWithConfirmation(company: CompanyListItem) {
        AlertDialog.Builder(this, R.style.AlertDialogStyle)
                .setMessage(getString(R.string.template_add_company_confirmation, company.name))
                .setPositiveButton(R.string.yes) { _, _ ->
                    addCompany(company)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
    }

    private fun addCompany(company: CompanyListItem) {
        val companyRecord = companiesRepository.itemsList.find { it.id == company.id } ?: return

        var disposable: Disposable? = null

        val progress = ProgressDialogFactory.getDialog(this) {
            disposable?.dispose()
        }

        disposable = AddCompanyUseCase(
                companyRecord,
                repositoryProvider.clientCompanies()
        )
                .perform()
                .compose(ObservableTransformers.defaultSchedulersCompletable())
                .doOnSubscribe { progress.show() }
                .doOnEvent { progress.dismiss() }
                .subscribeBy(
                        onComplete = { displayCompanies() },
                        onError = { errorHandlerFactory.getDefault().handle(it) }
                )
    }

    private fun update(force: Boolean = false) {
        if (!force) {
            clientCompaniesRepository.updateIfNotFresh()
            companiesRepository.updateIfNotFresh()
        } else {
            clientCompaniesRepository.update()
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
        } else {
            super.onBackPressed()
        }
    }
}
