package org.tokend.template.features.dashboard.balances.view

import android.content.res.Configuration
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.github.clans.fab.FloatingActionButton
import io.reactivex.rxkotlin.addTo
import kotlinx.android.synthetic.main.fragment_balances.*
import kotlinx.android.synthetic.main.include_appbar_elevation.*
import kotlinx.android.synthetic.main.include_error_empty_view.*
import org.tokend.template.BuildConfig
import org.tokend.template.R
import org.tokend.template.data.repository.BalancesRepository
import org.tokend.template.features.dashboard.balances.view.adapter.BalanceItemsAdapter
import org.tokend.template.features.dashboard.balances.view.adapter.BalanceListItem
import org.tokend.template.fragments.BaseFragment
import org.tokend.template.util.Navigator
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.view.util.*
import java.math.BigDecimal


class BalancesFragment : BaseFragment() {
    private val loadingIndicator = LoadingIndicatorManager(
            showLoading = { swipe_refresh.isRefreshing = true },
            hideLoading = { swipe_refresh.isRefreshing = false }
    )

    private val balancesRepository: BalancesRepository
        get() = repositoryProvider.balances()

    private lateinit var adapter: BalanceItemsAdapter
    private lateinit var layoutManager: GridLayoutManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_balances, container, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onInitAllowed() {
        initCompanyLogo()
        initList()
        initSwipeRefresh()
        initToolbarElevation()
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

    private fun initCompanyLogo() {
        val company = companyInfoProvider.getCompany()
                ?: return

        company_logo_image_view.visibility = View.VISIBLE

        val logoSize = resources.getDimensionPixelSize(R.dimen.unlock_logo_size)
        LogoUtil.setLogo(
                company_logo_image_view,
                company.name,
                company.logoUrl,
                logoSize
        )
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
    }

    private fun initSwipeRefresh() {
        swipe_refresh.setColorSchemeColors(ContextCompat.getColor(context!!, R.color.accent))
        swipe_refresh.setOnRefreshListener { update(force = true) }
        SwipeRefreshDependencyUtil.addDependency(swipe_refresh, app_bar)
    }

    private fun initToolbarElevation() {
        ElevationUtil.initScrollElevation(app_bar, appbar_elevation_view)
    }

    private fun initFab() {
        val themedContext = ContextThemeWrapper(requireContext(), R.style.FloatingButtonMenuItem)
        val actions = mutableListOf<FloatingActionButton>()

        // Accept redemption.
        val accountId = walletInfoProvider.getWalletInfo()?.accountId
        val balances = balancesRepository.itemsList
        if (balances.any { it.asset.ownerAccountId == accountId }) {
            actions.add(
                    FloatingActionButton(themedContext, null, R.style.FloatingButtonMenuItem)
                            .apply {
                                labelText = getString(R.string.accept_redemption)
                                setImageDrawable(ContextCompat.getDrawable(
                                        requireContext(),
                                        R.drawable.ic_qr_code_scan_fab)
                                )
                                setOnClickListener {
                                    Navigator.from(this@BalancesFragment)
                                            .openScanRedemption()
                                    menu_fab.close(false)
                                }
                            }
            )
        }

        // Redeem.
        actions.add(
                FloatingActionButton(themedContext, null, R.style.FloatingButtonMenuItem)
                        .apply {
                            labelText = getString(R.string.redeem)
                            setImageDrawable(ContextCompat.getDrawable(
                                    requireContext(),
                                    R.drawable.ic_redeem)
                            )
                            setOnClickListener {
                                Navigator.from(this@BalancesFragment)
                                        .openRedemptionCreation()
                                menu_fab.close(false)
                            }
                        }
        )

        // Send.
        if (BuildConfig.IS_SEND_ALLOWED) {
            actions.add(
                    FloatingActionButton(themedContext, null, R.style.FloatingButtonMenuItem)
                            .apply {
                                labelText = getString(R.string.send_title)
                                setImageDrawable(ContextCompat.getDrawable(
                                        requireContext(),
                                        R.drawable.ic_send_fab)
                                )
                                setOnClickListener {
                                    Navigator.from(this@BalancesFragment)
                                            .openSend()
                                    menu_fab.close(false)
                                }
                            }
            )
        }

        // Receive.
        if (BuildConfig.IS_SEND_ALLOWED) {
            actions.add(
                    FloatingActionButton(themedContext, null, R.style.FloatingButtonMenuItem)
                            .apply {
                                labelText = getString(R.string.receive_title)
                                setImageDrawable(ContextCompat.getDrawable(
                                        requireContext(),
                                        R.drawable.ic_receive_fab)
                                )
                                setOnClickListener {
                                    walletInfoProvider.getWalletInfo()?.also { walletInfo ->
                                        Navigator.from(this@BalancesFragment)
                                                .openAccountQrShare(walletInfo)
                                    }
                                    menu_fab.close(false)
                                }
                            }
            )
        }

        if (actions.isEmpty()) {
            menu_fab.visibility = View.GONE
            menu_fab.isEnabled = false
        } else {
            menu_fab.visibility = View.VISIBLE
            menu_fab.isEnabled = true
            actions.forEach(menu_fab::addMenuButton)
        }

        menu_fab.setClosedOnTouchOutside(true)
    }
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

    // region Display
    private fun displayBalances() {
        val companyId = companyInfoProvider.getCompany()?.id

        val items = balancesRepository
                .itemsList
                .sortedWith(balanceComparator)
                .filter { it.asset.ownerAccountId == companyId }
                .map(::BalanceListItem)

        adapter.setData(items)
    }

    private fun displayTotal() {
        val conversionAssetCode = balancesRepository.conversionAsset
        val companyId = companyInfoProvider.getCompany()?.id

        if (conversionAssetCode == null) {
            total_text_view.visibility = View.GONE
            return
        }

        val total = balancesRepository
                .itemsList
                .filter { it.asset.ownerAccountId == companyId }
                .fold(BigDecimal.ZERO) { sum, balance ->
                    sum.add(balance.convertedAmount ?: BigDecimal.ZERO)
                }

        total_text_view.visibility = View.VISIBLE
        total_text_view.text = amountFormatter.formatAssetAmount(total, conversionAssetCode)
    }
    // endregion

    private fun openWallet(balanceId: String) {
        Navigator.from(this).openBalanceDetails(balanceId)
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
        } else {
            super.onBackPressed()
        }
    }
}