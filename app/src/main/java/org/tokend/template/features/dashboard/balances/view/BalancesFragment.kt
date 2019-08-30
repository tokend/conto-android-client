package org.tokend.template.features.dashboard.balances.view

import android.content.res.Configuration
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import io.reactivex.rxkotlin.addTo
import io.reactivex.subjects.BehaviorSubject
import kotlinx.android.synthetic.main.appbar.*
import kotlinx.android.synthetic.main.fragment_balances.*
import kotlinx.android.synthetic.main.include_appbar_elevation.*
import kotlinx.android.synthetic.main.include_error_empty_view.*
import kotlinx.android.synthetic.main.toolbar.*
import org.tokend.template.R
import org.tokend.template.data.repository.BalancesRepository
import org.tokend.template.extensions.withArguments
import org.tokend.template.features.dashboard.balances.view.adapter.BalanceItemsAdapter
import org.tokend.template.features.dashboard.balances.view.adapter.BalanceListItem
import org.tokend.template.fragments.BaseFragment
import org.tokend.template.fragments.ToolbarProvider
import org.tokend.template.util.Navigator
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.util.SearchUtil
import org.tokend.template.view.util.*
import org.tokend.template.view.util.fab.FloatingActionMenuAction
import org.tokend.template.view.util.fab.addActions
import java.math.BigDecimal

open class BalancesFragment : BaseFragment(), ToolbarProvider {
    override val toolbarSubject = BehaviorSubject.create<Toolbar>()

    private val loadingIndicator = LoadingIndicatorManager(
            showLoading = { swipe_refresh.isRefreshing = true },
            hideLoading = { swipe_refresh.isRefreshing = false }
    )

    protected val balancesRepository: BalancesRepository
        get() = repositoryProvider.balances()

    protected lateinit var adapter: BalanceItemsAdapter
    private lateinit var layoutManager: GridLayoutManager
    protected var filter: String? = null
        set(value) {
            if (value != field) {
                field = value
                onFilterChanged()
            }
        }

    private val allowToolbar: Boolean by lazy {
        arguments?.getBoolean(ALLOW_TOOLBAR_EXTRA, false) ?: false
    }

    protected open var companyId: String? = null

    private var searchMenuItem: MenuItem? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_balances, container, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onInitAllowed() {
        companyId = arguments?.getString(COMPANY_ID_EXTRA)

        initList()
        initSwipeRefresh()
        initToolbar()
        initFab()

        subscribeToBalances()

        update()
    }

    // region Init
    private val hideFabScrollListener =
            object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy > 2) {
                        menu_fab.hideMenuButton(true)
                    } else if (dy < -2 && menu_fab.isEnabled) {
                        menu_fab.showMenuButton(true)
                    }
                }
            }

    private fun initList() {
        layoutManager = GridLayoutManager(requireContext(), 1)
        adapter = BalanceItemsAdapter(amountFormatter)
        updateListColumnsCount()
        balances_list.adapter = adapter
        balances_list.layoutManager = layoutManager
        balances_list.addOnScrollListener(hideFabScrollListener)
        adapter.registerAdapterDataObserver(ScrollOnTopItemUpdateAdapterObserver(balances_list))
        adapter.onItemClick { _, item ->
            item.source?.id?.also { openWallet(it) }
        }

        error_empty_view.observeAdapter(adapter, R.string.you_have_no_balances)
        error_empty_view.setEmptyViewDenial { balancesRepository.isNeverUpdated }
        error_empty_view.setEmptyDrawable(R.drawable.ic_coins)

        ElevationUtil.initScrollElevation(balances_list, appbar_elevation_view)
    }

    private fun initSwipeRefresh() {
        swipe_refresh.setColorSchemeColors(ContextCompat.getColor(context!!, R.color.accent))
        swipe_refresh.setOnRefreshListener { update(force = true) }
    }

    private fun initToolbar() {
        if (allowToolbar) {
            toolbar.title = getString(R.string.balances_screen_title)
            initMenu()
        } else {
            appbar.visibility = View.GONE
        }

        // Do not forget to add SwipeRefreshDependency when making visible.
        app_bar.visibility = View.GONE

        toolbarSubject.onNext(toolbar)
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
                    .compose(ObservableTransformers.defaultSchedulers())
                    .subscribe { newValue ->
                        filter = newValue.takeIf { it.isNotEmpty() }
                    }
                    .addTo(compositeDisposable)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun initFab() {
        menu_fab.addActions(getFabActions())
        menu_fab.setClosedOnTouchOutside(true)
    }

    protected open fun getFabActions(): Collection<FloatingActionMenuAction> = emptyList()
    // endregion

    private fun update(force: Boolean = false) {
        if (!force) {
            balancesRepository.updateIfNotFresh()
        } else {
            balancesRepository.update()
        }
    }

    private fun subscribeToBalances() {
        balancesRepository
                .itemsSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { onBalancesUpdated() }
                .addTo(compositeDisposable)

        balancesRepository
                .loadingSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { loadingIndicator.setLoading(it) }
                .addTo(compositeDisposable)
    }

    private fun onBalancesUpdated() {
        displayBalances()
        displayTotal()
    }

    private fun onFilterChanged() {
        displayBalances()
    }

    // region Display
    protected open fun displayBalances() {
        val systemAssetLabel = getString(R.string.system_asset)

        val items = balancesRepository
                .itemsList
                .sortedWith(balanceComparator)
                .filter {
                    it.available.signum() > 0 &&
                            (companyId == null || it.asset.ownerAccountId == companyId)
                }
                .map {
                    val ownerName =
                            if (companyId != null)
                                null
                            else
                                it.company?.name ?: systemAssetLabel
                    BalanceListItem(it, ownerName)
                }
                .filter { item ->
                    filter?.let {
                        SearchUtil.isMatchGeneralCondition(it, item.assetName, item.ownerName)
                    } ?: true
                }

        adapter.setData(items)
    }

    protected open fun displayTotal() {
        val conversionAssetCode = balancesRepository.conversionAsset

        if (conversionAssetCode == null) {
            total_text_view.visibility = View.GONE
            return
        }

        val total = balancesRepository
                .itemsList
                .fold(BigDecimal.ZERO) { sum, balance ->
                    sum.add(balance.convertedAmount ?: BigDecimal.ZERO)
                }

        total_text_view.visibility = View.VISIBLE
        total_text_view.text = amountFormatter.formatAssetAmount(total, conversionAssetCode)
    }
    // endregion

    protected open fun openWallet(balanceId: String) {
        Navigator.from(this).openSimpleBalanceDetails(balanceId)
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        updateListColumnsCount()
    }

    private fun updateListColumnsCount() {
        layoutManager.spanCount = ColumnCalculator.getColumnCount(requireActivity())
        adapter.drawDividers = layoutManager.spanCount == 1
    }

    override fun onBackPressed(): Boolean {
        return if (menu_fab.isOpened) {
            menu_fab.close(true)
            false
        } else if (searchMenuItem?.isActionViewExpanded == true) {
            searchMenuItem?.collapseActionView()
            false
        } else {
            super.onBackPressed()
        }
    }

    companion object {
        private const val ALLOW_TOOLBAR_EXTRA = "allow_toolbar"
        private const val COMPANY_ID_EXTRA = "company_id"
        val ID = "balances".hashCode().toLong()

        fun newInstance(bundle: Bundle): BalancesFragment = BalancesFragment().withArguments(bundle)

        fun getBundle(allowToolbar: Boolean,
                      companyId: String?) = Bundle().apply {
            putBoolean(ALLOW_TOOLBAR_EXTRA, allowToolbar)
            putString(COMPANY_ID_EXTRA, companyId)
        }
    }
}