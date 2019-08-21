package org.tokend.template.features.dashboard.shop.view

import android.content.res.Configuration
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.widget.GridLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.reactivex.rxkotlin.addTo
import kotlinx.android.synthetic.main.fragment_all_atomic_swap_asks.*
import kotlinx.android.synthetic.main.include_error_empty_view.*
import org.tokend.template.R
import org.tokend.template.data.model.AtomicSwapAskRecord
import org.tokend.template.features.dashboard.shop.repository.AllAtomicSwapAsksRepository
import org.tokend.template.features.dashboard.shop.view.adapter.SinglePriceAtomicSwapAskListItem
import org.tokend.template.features.dashboard.shop.view.adapter.SinglePriceAtomicSwapAsksAdapter
import org.tokend.template.fragments.BaseFragment
import org.tokend.template.util.Navigator
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.view.util.ColumnCalculator
import org.tokend.template.view.util.LoadingIndicatorManager

class AllAtomicSwapAsksFragment: BaseFragment() {
    private val loadingIndicator = LoadingIndicatorManager(
            showLoading = { swipe_refresh.isRefreshing = true },
            hideLoading = { swipe_refresh.isRefreshing = false }
    )

    private val asksRepository: AllAtomicSwapAsksRepository
        get() = repositoryProvider.allAtomicSwapAsks()

    private lateinit var adapter: SinglePriceAtomicSwapAsksAdapter
    private lateinit var layoutManager: GridLayoutManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_all_atomic_swap_asks, container, false)
    }

    override fun onInitAllowed() {
        initList()
        initSwipeRefresh()

        subscribeToAsks()

        update()
    }

    private fun initList() {
        layoutManager = GridLayoutManager(requireContext(), 1)
        adapter = SinglePriceAtomicSwapAsksAdapter(amountFormatter)
        adapter.onItemClick { _, item ->
            item.source?.also(this::openBuy)
        }
        updateListColumnsCount()

        error_empty_view.apply {
            setEmptyDrawable(R.drawable.ic_shop_cart)
            observeAdapter(adapter, R.string.no_offers)
            setEmptyViewDenial { asksRepository.isNeverUpdated }
        }

        asks_recycler_view.adapter = adapter
        asks_recycler_view.layoutManager = layoutManager

        asks_recycler_view.listenBottomReach({ adapter.getDataItemCount() }) {
            asksRepository.loadMore() || asksRepository.noMoreItems
        }
    }

    private fun initSwipeRefresh() {
        swipe_refresh.setColorSchemeColors(ContextCompat.getColor(requireContext(), R.color.accent))
        swipe_refresh.setOnRefreshListener {
            update(force = true)
        }
    }

    private fun subscribeToAsks() {
        asksRepository
                .itemsSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { displayAsks() }
                .addTo(compositeDisposable)

        asksRepository.loadingSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { loading ->
                    if (loading) {
                        if (asksRepository.isOnFirstPage) {
                            loadingIndicator.show("asks")
                        } else {
                            adapter.showLoadingFooter()
                        }
                    } else {
                        loadingIndicator.hide("asks")
                        adapter.hideLoadingFooter()
                    }
                }
                .addTo(compositeDisposable)

        asksRepository.errorsSubject
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
    }

    private fun displayAsks() {
        val items = asksRepository
                .itemsList
                .map(::SinglePriceAtomicSwapAskListItem)
        adapter.setData(items)
    }

    private fun update(force: Boolean = false) {
        if (!force) {
            asksRepository.updateIfNotFresh()
        } else {
            asksRepository.update()
        }
    }

    private fun openBuy(ask: AtomicSwapAskRecord) {
        Navigator.from(this).openAtomicSwapBuy(
                ask.asset.code,
                ask.id
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        updateListColumnsCount()
    }

    private fun updateListColumnsCount() {
        layoutManager.spanCount = ColumnCalculator.getColumnCount(requireActivity())
    }
}