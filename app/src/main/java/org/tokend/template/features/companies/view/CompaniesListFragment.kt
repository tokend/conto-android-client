package org.tokend.template.features.companies.view

import android.content.res.Configuration
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.SimpleItemAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.addTo
import kotlinx.android.synthetic.main.fragment_companies_list.*
import kotlinx.android.synthetic.main.include_error_empty_view.*
import org.tokend.template.R
import org.tokend.template.features.companies.model.CompanyRecord
import org.tokend.template.data.repository.base.MultipleItemsRepository
import org.tokend.template.features.companies.view.adapter.CompanyItemsAdapter
import org.tokend.template.features.companies.view.adapter.CompanyListItem
import org.tokend.template.fragments.BaseFragment
import org.tokend.template.util.Navigator
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.util.SearchUtil
import org.tokend.template.view.util.ColumnCalculator
import org.tokend.template.view.util.LoadingIndicatorManager
import org.tokend.template.view.util.ScrollOnTopItemUpdateAdapterObserver

abstract class CompaniesListFragment : BaseFragment() {
    protected open lateinit var adapter: CompanyItemsAdapter
    protected open lateinit var layoutManager: GridLayoutManager

    protected open val loadingIndicator = LoadingIndicatorManager(
            showLoading = { swipe_refresh.isRefreshing = true },
            hideLoading = { swipe_refresh.isRefreshing = false }
    )

    protected var filter: String? = null
        set(value) {
            if (value != field) {
                field = value
                onFilterChanged()
            }
        }

    protected abstract val repository: MultipleItemsRepository<CompanyRecord>

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_companies_list, container, false)
    }

    override fun onInitAllowed() {
        initList()
        initSwipeRefresh()

        subscribeToCompanies()

        update()
    }

    private fun initList() {
        layoutManager = GridLayoutManager(requireContext(), 1)
        adapter = CompanyItemsAdapter()
        updateListColumnsCount()

        companies_recycler_view.layoutManager = layoutManager
        companies_recycler_view.adapter = adapter
        (companies_recycler_view.itemAnimator as? SimpleItemAnimator)
                ?.supportsChangeAnimations = false

        error_empty_view.setEmptyDrawable(R.drawable.ic_company)
        error_empty_view.observeAdapter(
                adapter,
                R.string.error_no_companies
        )
        error_empty_view.setEmptyViewDenial { repository.isNeverUpdated }

        adapter.registerAdapterDataObserver(
                ScrollOnTopItemUpdateAdapterObserver(companies_recycler_view)
        )

        adapter.onItemClick { _, item ->
            item.source?.also(this::openCompanyDetails)
        }
    }

    private fun initSwipeRefresh() {
        swipe_refresh.setColorSchemeColors(ContextCompat.getColor(requireContext(), R.color.accent))
        swipe_refresh.setOnRefreshListener { update(true) }
    }

    protected open fun subscribeToCompanies() {
        repository
                .loadingSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { loadingIndicator.setLoading(it) }
                .addTo(compositeDisposable)

        repository
                .errorsSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { error ->
                    if (!adapter.hasData) {
                        error_empty_view.showError(error, errorHandlerFactory.getDefault()) {
                            update(true)
                        }
                    } else {
                        errorHandlerFactory.getDefault().handle(error)
                    }
                }
                .addTo(compositeDisposable)

        repository
                .itemsSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { onCompaniesLoaded() }
                .addTo(compositeDisposable)
    }

    protected open fun update(force: Boolean = false) {
        if (!force) {
            repository.updateIfNotFresh()
        } else {
            repository.update()
        }
    }

    protected open fun onCompaniesLoaded() {
        displayCompanies()
    }

    protected open fun onFilterChanged() {
        displayCompanies()
    }

    protected open fun displayCompanies() {
        val items = repository
                .itemsList
                .map { CompanyListItem(it) }
                .filter { item ->
                    filter?.let {
                        SearchUtil.isMatchGeneralCondition(
                                it,
                                item.name,
                                item.industry
                        )
                    } ?: true
                }

        adapter.setData(items)
    }

    private fun openCompanyDetails(company: CompanyRecord) {
        Navigator.from(this).openCompanyDetails(company)
    }

    private fun updateListColumnsCount() {
        layoutManager.spanCount = ColumnCalculator.getColumnCount(requireActivity())
        adapter.drawDividers = layoutManager.spanCount == 1
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        updateListColumnsCount()
    }

    protected open var filterChangesDisposable: Disposable? = null
    protected open var filterObservable: Observable<String>? = null
    open fun observeFilterChanges(filterChanges: Observable<String>) {
        if (filterObservable == filterChanges) {
            return
        }

        filterChangesDisposable?.dispose()
        filterObservable = filterChanges
        filterChangesDisposable =
                filterChanges
                        .subscribe { filter = it.takeIf(String::isNotEmpty) }
                        .addTo(compositeDisposable)
    }
}