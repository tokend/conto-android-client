package org.tokend.template.features.assets.buy.marketplace.view

import android.content.res.Configuration
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.Toolbar
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import io.reactivex.rxkotlin.addTo
import io.reactivex.subjects.BehaviorSubject
import kotlinx.android.synthetic.main.appbar.*
import kotlinx.android.synthetic.main.fragment_all_atomic_swap_asks.*
import kotlinx.android.synthetic.main.include_appbar_elevation.*
import kotlinx.android.synthetic.main.include_error_empty_view.*
import kotlinx.android.synthetic.main.toolbar.*
import org.tokend.template.R
import org.tokend.template.extensions.withArguments
import org.tokend.template.features.assets.buy.marketplace.model.MarketplaceOfferRecord
import org.tokend.template.features.assets.buy.marketplace.repository.MarketplaceOffersRepository
import org.tokend.template.features.assets.buy.marketplace.view.adapter.MarketplaceOfferListItem
import org.tokend.template.features.assets.buy.marketplace.view.adapter.SinglePriceAtomicSwapAsksAdapter
import org.tokend.template.fragments.BaseFragment
import org.tokend.template.fragments.ToolbarProvider
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.util.SearchUtil
import org.tokend.template.util.navigation.Navigator
import org.tokend.template.view.util.ColumnCalculator
import org.tokend.template.view.util.ElevationUtil
import org.tokend.template.view.util.LoadingIndicatorManager
import org.tokend.template.view.util.MenuSearchViewManager

class MarketplaceFragment : BaseFragment(), ToolbarProvider {
    override val toolbarSubject = BehaviorSubject.create<Toolbar>()

    private val loadingIndicator = LoadingIndicatorManager(
            showLoading = { swipe_refresh.isRefreshing = true },
            hideLoading = { swipe_refresh.isRefreshing = false }
    )

    private var companyId: String? = null

    private val allowToolbar: Boolean by lazy {
        arguments?.getBoolean(ALLOW_TOOLBAR_EXTRA, false) ?: false
    }

    private val asksRepository: MarketplaceOffersRepository
        get() = repositoryProvider.marketplaceOffers(companyId)

    private lateinit var adapter: SinglePriceAtomicSwapAsksAdapter
    private lateinit var layoutManager: GridLayoutManager

    private var searchMenuItem: MenuItem? = null

    private var filter: String? = null
        set(value) {
            if (value != field) {
                field = value
                onFilterChanged()
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_all_atomic_swap_asks, container, false)
    }

    override fun onInitAllowed() {
        companyId = arguments?.getString(COMPANY_ID_EXTRA)

        initToolbar()
        initList()
        initSwipeRefresh()

        subscribeToAsks()

        update()
    }

    // region Init
    private fun initToolbar() {
        if (allowToolbar) {
            toolbar.title = getString(R.string.marketplace)
            initMenu()
            ElevationUtil.initScrollElevation(asks_recycler_view, appbar_elevation_view)
        } else {
            appbar.visibility = View.GONE
            appbar_elevation_view.visibility = View.GONE
        }

        toolbarSubject.onNext(toolbar)
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

    private fun initMenu() {
        toolbar.inflateMenu(R.menu.search)
        val menu = toolbar.menu

        val searchItem = menu.findItem(R.id.search) ?: return
        this.searchMenuItem = searchItem

        try {
            val searchManager = MenuSearchViewManager(searchItem, toolbar, compositeDisposable)

            searchManager.queryHint = getString(R.string.search)
            searchManager
                    .queryChanges
                    .compose(ObservableTransformers.defaultSchedulers())
                    .subscribe { newValue ->
                        filter = newValue.takeIf(String::isNotEmpty)
                    }
                    .addTo(compositeDisposable)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    // endregion

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
                .asSequence()
                .filterNot(MarketplaceOfferRecord::isCanceled)
                .map {
                    MarketplaceOfferListItem(
                            it,
                            withCompany = companyId == null
                    )
                }
                .filter { item ->
                    filter?.let {
                        SearchUtil.isMatchGeneralCondition(it, item.asset.name, item.companyName)
                    } ?: true
                }
                .toList()

        adapter.setData(items)
    }

    private fun onFilterChanged() {
        displayAsks()
    }

    private fun update(force: Boolean = false) {
        if (!force) {
            asksRepository.updateIfNotFresh()
        } else {
            asksRepository.update()
        }
    }

    private fun openBuy(offer: MarketplaceOfferRecord) {
        Navigator.from(this).openMarketplaceBuy(offer)
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        updateListColumnsCount()
    }

    private fun updateListColumnsCount() {
        layoutManager.spanCount = ColumnCalculator.getColumnCount(requireActivity())
    }

    override fun onBackPressed(): Boolean {
        return searchMenuItem?.isActionViewExpanded == false.also {
            searchMenuItem?.collapseActionView()
        }
    }

    companion object {
        val ID = "all_atomic_swaps".hashCode().toLong()
        private const val COMPANY_ID_EXTRA = "company_id"
        private const val ALLOW_TOOLBAR_EXTRA = "allow_toolbar"

        fun getBundle(companyId: String?,
                      allowToolbar: Boolean) = Bundle().apply {
            putString(COMPANY_ID_EXTRA, companyId)
            putBoolean(ALLOW_TOOLBAR_EXTRA, allowToolbar)
        }

        fun newInstance(bundle: Bundle): MarketplaceFragment =
                MarketplaceFragment().withArguments(bundle)
    }
}