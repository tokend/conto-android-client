package org.tokend.template.features.companies.view

import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.addTo
import kotlinx.android.synthetic.main.include_error_empty_view.*
import org.tokend.template.features.companies.model.CompanyRecord
import org.tokend.template.features.companies.storage.ClientCompaniesRepository
import org.tokend.template.data.repository.base.MultipleItemsRepository
import org.tokend.template.features.companies.view.adapter.CompanyListItem
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.util.SearchUtil
import java.util.concurrent.TimeUnit

class AllCompaniesFragment : CompaniesListFragment() {
    override val repository: MultipleItemsRepository<CompanyRecord>
        get() = repositoryProvider.companies()

    private val clientCompaniesRepository: ClientCompaniesRepository
        get() = repositoryProvider.clientCompanies()

    private var companiesDisposable: Disposable? = null
    override fun subscribeToCompanies() {
        val disposable = CompositeDisposable()
        companiesDisposable?.dispose()
        companiesDisposable = disposable

        Observable.combineLatest(
                listOf(
                        repository.itemsSubject,
                        clientCompaniesRepository.itemsSubject
                )
        ) { true }
                .filter {
                    repository.isFresh && clientCompaniesRepository.isFresh
                }
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe {
                    displayCompanies()
                }
                .addTo(disposable)

        repository.loadingSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe {
                    loadingIndicator.setLoading(it, "companies")
                }
                .addTo(disposable)

        clientCompaniesRepository.loadingSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe {
                    loadingIndicator.setLoading(it, "client-companies")
                }
                .addTo(disposable)

        Observable.merge(
                repository.errorsSubject,
                clientCompaniesRepository.errorsSubject
        )
                .debounce(100, TimeUnit.MILLISECONDS)
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
                .addTo(disposable)

        disposable.addTo(compositeDisposable)
    }

    override fun displayCompanies() {
        val clientCompanies = clientCompaniesRepository.itemsList
        val items = repository.itemsList
                .asSequence()
                .map { company ->
                    CompanyListItem(company, clientCompanies.contains(company))
                }
                .sortedWith(Comparator { o1, o2 ->
                    return@Comparator o1.exist.compareTo(o2.exist)
                })
                .filter { item ->
                    filter?.let {
                        SearchUtil.isMatchGeneralCondition(
                                it,
                                item.name,
                                item.industry
                        )
                    } ?: true
                }
                .toList()

        adapter.setData(items)
    }
}