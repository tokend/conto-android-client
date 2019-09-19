package org.tokend.template.features.swap.view

import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.reactivex.rxkotlin.addTo
import io.reactivex.subjects.BehaviorSubject
import kotlinx.android.synthetic.main.fragment_swaps.*
import kotlinx.android.synthetic.main.include_appbar_elevation.*
import kotlinx.android.synthetic.main.include_error_empty_view.*
import kotlinx.android.synthetic.main.toolbar.*
import org.tokend.template.R
import org.tokend.template.features.swap.model.SwapRecord
import org.tokend.template.features.swap.repository.SwapsRepository
import org.tokend.template.features.swap.view.adapter.SwapListItem
import org.tokend.template.features.swap.view.adapter.SwapsAdapter
import org.tokend.template.fragments.BaseFragment
import org.tokend.template.fragments.ToolbarProvider
import org.tokend.template.util.Navigator
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.view.util.ElevationUtil
import org.tokend.template.view.util.LoadingIndicatorManager

class SwapsFragment : BaseFragment(), ToolbarProvider {
    override val toolbarSubject = BehaviorSubject.create<Toolbar>()

    private val loadingIndicator = LoadingIndicatorManager(
            showLoading = { swipe_refresh.isRefreshing = true },
            hideLoading = { swipe_refresh.isRefreshing = false }
    )

    private val swapsRepository: SwapsRepository
        get() = repositoryProvider.swaps()

    private lateinit var adapter: SwapsAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_swaps, container, false)
    }

    override fun onInitAllowed() {
        initToolbar()
        initList()
        initFab()
        initSwipeRefresh()

        subscribeToSwaps()

        update()
    }

    private fun initToolbar() {
        toolbar.title = getString(R.string.swaps_screen_title)
        toolbarSubject.onNext(toolbar)
        ElevationUtil.initScrollElevation(swaps_recycler_view, appbar_elevation_view)
    }

    private val hideFabScrollListener =
            object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy > 2) {
                        add_fab.hideMenuButton(true)
                    } else if (dy < -2 && add_fab.isEnabled) {
                        add_fab.showMenuButton(true)
                    }
                }
            }

    private fun initList() {
        adapter = SwapsAdapter(amountFormatter)
        swaps_recycler_view.layoutManager = LinearLayoutManager(requireContext())
        swaps_recycler_view.adapter = adapter
        swaps_recycler_view.addOnScrollListener(hideFabScrollListener)

        adapter.onItemClick { _, item ->
            item.source?.also(this::openSwapDetails)
        }

        error_empty_view.apply {
            setEmptyDrawable(R.drawable.ic_trade)
            observeAdapter(adapter, R.string.no_swaps)
            setEmptyViewDenial { swapsRepository.isNeverUpdated }
        }
    }

    private fun initFab() {
        add_fab.setOnMenuButtonClickListener {
            Navigator.from(this).openSwapCreation()
        }
    }

    private fun initSwipeRefresh() {
        swipe_refresh.setColorSchemeColors(ContextCompat.getColor(requireContext(), R.color.accent))
        swipe_refresh.setOnRefreshListener { update(force = true) }
    }

    private fun subscribeToSwaps() {
        swapsRepository
                .itemsSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { displaySwaps() }
                .addTo(compositeDisposable)

        swapsRepository
                .loadingSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { loadingIndicator.setLoading(it) }
                .addTo(compositeDisposable)

        swapsRepository
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
    }

    private fun update(force: Boolean = false) {
        if (!force) {
            swapsRepository.updateIfNotFresh()
        } else {
            swapsRepository.update()
        }
    }

    private fun displaySwaps() {
//        val items = listOf(
//                SwapListItem(
//                        BigDecimal("100"), SimpleAsset("", 6, "Gas station bonuses", null),
//                        BigDecimal("25"), SimpleAsset("", 6, "Pet shop points", null),
//                        "alice@mail.com",
//                        SwapState.CREATED,
//                        false,
//                        null
//                ),
//                SwapListItem(
//                        BigDecimal("10"), SimpleAsset("", 6, "Silpo bonus points", null),
//                        BigDecimal("10"), SimpleAsset("", 6, "ATB bonuses", null),
//                        "alice@mail.com",
//                        SwapState.CAN_BE_RECEIVED_BY_DEST,
//                        true,
//                        null
//                )
//        )
        val items = swapsRepository.itemsList.map(::SwapListItem)

        adapter.setData(items)
    }

    private fun openSwapDetails(swap: SwapRecord) {

    }

    companion object {
        val ID = "swaps".hashCode().toLong()
    }
}