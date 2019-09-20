package org.tokend.template.activities

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.AlertDialog
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.view.View
import com.github.tbouron.shakedetector.library.ShakeDetector
import com.mikepenz.materialdrawer.AccountHeader
import com.mikepenz.materialdrawer.AccountHeaderBuilder
import com.mikepenz.materialdrawer.Drawer
import com.mikepenz.materialdrawer.DrawerBuilder
import com.mikepenz.materialdrawer.model.DividerDrawerItem
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem
import com.mikepenz.materialdrawer.model.ProfileDrawerItem
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem
import com.mikepenz.materialdrawer.util.DrawerImageLoader
import io.reactivex.Completable
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.navigation_drawer_header.view.*
import org.jetbrains.anko.browse
import org.tokend.template.BuildConfig
import org.tokend.template.R
import org.tokend.template.features.assets.ExploreAssetsFragment
import org.tokend.template.features.assets.buy.marketplace.view.MarketplaceFragment
import org.tokend.template.features.companies.view.CompaniesFragment
import org.tokend.template.features.dashboard.balances.view.BalancesFragment
import org.tokend.template.features.dashboard.view.DashboardFragment
import org.tokend.template.features.deposit.DepositFragment
import org.tokend.template.features.invest.view.SalesFragment
import org.tokend.template.features.kyc.model.KycForm
import org.tokend.template.features.kyc.model.KycState
import org.tokend.template.features.kyc.storage.KycStateRepository
import org.tokend.template.features.movements.view.AssetMovementsFragment
import org.tokend.template.features.polls.view.PollsFragment
import org.tokend.template.features.send.model.PaymentRequest
import org.tokend.template.features.settings.SettingsFragment
import org.tokend.template.features.signin.model.ForcedAccountType
import org.tokend.template.features.trade.orderbook.view.OrderBookFragment
import org.tokend.template.features.trade.pairs.view.TradeAssetPairsFragment
import org.tokend.template.features.withdraw.WithdrawFragment
import org.tokend.template.features.withdraw.model.WithdrawalRequest
import org.tokend.template.fragments.FragmentFactory
import org.tokend.template.fragments.ToolbarProvider
import org.tokend.template.logic.wallet.WalletEventsListener
import org.tokend.template.util.Navigator
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.util.ProfileUtil
import org.tokend.template.view.util.PicassoDrawerImageLoader
import org.tokend.template.view.util.input.SoftInputUtil
import java.util.concurrent.TimeUnit

open class MainActivity : BaseActivity(), WalletEventsListener {
    companion object {
        val CONTRIBUTE_ITEM_ID = "contribute".hashCode().toLong()
        private const val SHAKES_TO_PAY_COUNT = 4
        private const val REPO_URL = "https://github.com/tokend/conto-android-client"
    }

    protected open val defaultFragmentId = BalancesFragment.ID

    private var navigationDrawer: Drawer? = null
    private var landscapeNavigationDrawer: Drawer? = null
    private var onBackPressedListener: OnBackPressedListener? = null
    protected val factory = FragmentFactory()
    private val tablet by lazy {
        resources.getBoolean(R.bool.isTablet)
    }
    private val orientation: Int
        get() = resources.configuration.orientation
    private var accountHeader: AccountHeader? = null
    private var landscapeAccountHeader: AccountHeader? = null
    private val profileUpdatesSubject = PublishSubject.create<Boolean>()

    private var toolbar: Toolbar? = null

    protected val kycStateRepository: KycStateRepository
        get() = repositoryProvider.kycState()

    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_main)
        window.setBackgroundDrawable(null)

        initNavigation()
        initShakeDetection()

        subscribeToKycChanges()

        navigationDrawer?.setSelection(defaultFragmentId)
    }

    // region Init
    @SuppressLint("UseSparseArrays")
    private fun initNavigation() {
        val email = walletInfoProvider.getWalletInfo()?.email
                ?: throw IllegalStateException("No email found")

        val placeholderValue = email.toUpperCase()
        val placeholderSize =
                resources.getDimensionPixelSize(R.dimen.material_drawer_item_profile_icon_width)
        val placeholderBackground =
                ContextCompat.getColor(this, R.color.avatar_placeholder_background)
        val placeholderDrawable =
                ProfileUtil.getAvatarPlaceholder(placeholderValue, this, placeholderSize)
        DrawerImageLoader.init(
                PicassoDrawerImageLoader(this, placeholderDrawable, placeholderBackground)
        )

        val items = getNavigationItems()
                .associateBy(PrimaryDrawerItem::getIdentifier)

        val accountHeader = getHeaderInstance(email)
        val landscapeAccountHeader = getHeaderInstance(email)

        navigationDrawer = initDrawerBuilder(items, accountHeader).build()
        landscapeNavigationDrawer = initDrawerBuilder(items, landscapeAccountHeader).buildView()
        nav_tablet.addView(landscapeNavigationDrawer?.slider, 0)

        this.accountHeader = accountHeader
        this.landscapeAccountHeader = landscapeAccountHeader

        profileUpdatesSubject
                .debounce(50, TimeUnit.MILLISECONDS)
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { updateProfileHeader() }
                .addTo(compositeDisposable)
    }

    protected open fun getNavigationItems() = listOf(
            PrimaryDrawerItem()
                    .withName(R.string.balances_screen_title)
                    .withIdentifier(BalancesFragment.ID)
                    .withIcon(R.drawable.ic_coins),

            PrimaryDrawerItem()
                    .withName(R.string.companies_title)
                    .withIdentifier(CompaniesFragment.ID)
                    .withIcon(R.drawable.ic_company),

            PrimaryDrawerItem()
                    .withName(R.string.deposit_title)
                    .withIdentifier(DepositFragment.ID)
                    .withIcon(R.drawable.ic_deposit),

            PrimaryDrawerItem()
                    .withName(R.string.withdraw_title)
                    .withIdentifier(WithdrawFragment.ID)
                    .withIcon(R.drawable.ic_withdraw),

            PrimaryDrawerItem()
                    .withName(R.string.explore_sales_title)
                    .withIdentifier(SalesFragment.ID)
                    .withIcon(R.drawable.ic_invest),

            PrimaryDrawerItem()
                    .withName(R.string.trade_title)
                    .withIdentifier(TradeAssetPairsFragment.ID)
                    .withIcon(R.drawable.ic_trade),

            PrimaryDrawerItem()
                    .withName(R.string.polls_title)
                    .withIdentifier(PollsFragment.ID)
                    .withIcon(R.drawable.ic_poll),

            PrimaryDrawerItem()
                    .withName(R.string.settings_title)
                    .withIdentifier(SettingsFragment.ID)
                    .withIcon(R.drawable.ic_settings),

            PrimaryDrawerItem()
                    .withName(R.string.github)
                    .withIdentifier(CONTRIBUTE_ITEM_ID)
                    .withIcon(R.drawable.ic_github_circle)
                    .withSelectable(false),

            PrimaryDrawerItem()
                    .withName(R.string.marketplace)
                    .withIdentifier(MarketplaceFragment.ID)
                    .withIcon(R.drawable.ic_shop_cart),

            PrimaryDrawerItem()
                    .withName(R.string.operations_history_short)
                    .withIdentifier(AssetMovementsFragment.ID)
                    .withIcon(R.drawable.ic_history)
    )

    protected open fun getHeaderInstance(email: String): AccountHeader {
        return AccountHeaderBuilder()
                .withActivity(this)
                .withAccountHeader(R.layout.navigation_drawer_header)
                .withHeaderBackground(
                        ColorDrawable(ContextCompat.getColor(this, R.color.white))
                )
                .withTextColor(ContextCompat.getColor(this, R.color.primary_text))
                .withSelectionListEnabledForSingleProfile(false)
                .withProfileImagesVisible(true)
                .withDividerBelowHeader(true)
                .addProfiles(getProfileHeaderItem(email))
                .withThreeSmallProfileImages(true)
                .withOnlyMainProfileImageVisible(true)
                .withCurrentProfileHiddenInList(true)
                .withOnAccountHeaderListener { _, _, _ ->
                    openAccountIdShare()
                    true
                }
                .build()
                .also(this::initAccountTypeSwitch)
    }

    protected open fun initAccountTypeSwitch(accountHeader: AccountHeader) {
        val view = accountHeader.view.findViewById<View>(R.id.account_type_switch_layout)
                ?: return
        if (kycStateRepository.itemFormData is KycForm.Corporate
                && kycStateRepository.isFormApproved) {
            view.visibility = View.VISIBLE
            view.account_type_switch_hint.text = getAccountTypeSwitchHint()
            view.account_type_switch_button.setOnClickListener {
                switchAccountType()
            }
        } else {
            view.visibility = View.GONE
        }
    }

    protected open fun getAccountTypeSwitchHint(): String = getString(R.string.switch_to_company)

    protected open fun switchAccountType() {
        AlertDialog.Builder(this, R.style.AlertDialogStyle)
                .setMessage(R.string.switch_to_company_confirmation)
                .setPositiveButton(R.string.yes) { _, _ ->
                    Navigator.from(this).toForcingAccountType(ForcedAccountType.CORPORATE)
                }
                .setNegativeButton(R.string.no, null)
                .show()
    }

    protected open fun getProfileHeaderItem(email: String): ProfileDrawerItem {
        val kycState = kycStateRepository.item
        val avatarUrl = ProfileUtil.getAvatarUrl(kycState, urlConfigProvider, email)
        val name = ProfileUtil.getDisplayedName(kycState, email)

        return ProfileDrawerItem()
                .withIdentifier(1)
                .withName(name)
                .withEmail(email)
                .apply {
                    avatarUrl?.also { withIcon(it) }
                }
    }

    protected open fun initDrawerBuilder(items: Map<Long, PrimaryDrawerItem>,
                                         profileHeader: AccountHeader): DrawerBuilder {
        return DrawerBuilder()
                .withActivity(this)
                .withAccountHeader(profileHeader)
                .withHeaderDivider(false)
                .withTranslucentStatusBar(false)
                .withSliderBackgroundColorRes(R.color.material_drawer_background)
                .also { addRequiredNavigationItems(it, items) }
                .withOnDrawerItemClickListener { _, _, item ->
                    return@withOnDrawerItemClickListener onNavigationItemSelected(item)
                }
    }

    protected open fun addRequiredNavigationItems(builder: DrawerBuilder,
                                                  items: Map<Long, PrimaryDrawerItem>) {
        builder.apply {
            addDrawerItems(
                    items[BalancesFragment.ID],
                    items[CompaniesFragment.ID],
                    items[MarketplaceFragment.ID],
                    items[AssetMovementsFragment.ID]
            )

            if (BuildConfig.IS_DEPOSIT_ALLOWED) {
                addDrawerItems(items[DepositFragment.ID])
            }

            if (BuildConfig.IS_WITHDRAW_ALLOWED) {
                addDrawerItems(items[WithdrawFragment.ID])
            }

            if (BuildConfig.IS_INVEST_ALLOWED) {
                addDrawerItems(items[SalesFragment.ID])
            }

            if (BuildConfig.IS_TRADE_ALLOWED) {
                addDrawerItems(items[OrderBookFragment.ID])
            }

            if (BuildConfig.ARE_POLLS_ALLOWED) {
                addDrawerItems(items[PollsFragment.ID])
            }

            addDrawerItems(
                    items[SettingsFragment.ID],
                    DividerDrawerItem(),
                    items[CONTRIBUTE_ITEM_ID]
            )
        }
    }

    private fun initShakeDetection() {
        ShakeDetector.create(this, this::onShakingDetected)
        ShakeDetector.updateConfiguration(2F, SHAKES_TO_PAY_COUNT)
    }
    // endregion

    private fun subscribeToKycChanges() {
        kycStateRepository
                .itemSubject
                .map { true }
                .subscribe(profileUpdatesSubject)
    }

    // region Navigation
    private fun onNavigationItemSelected(item: IDrawerItem<Any, RecyclerView.ViewHolder>)
            : Boolean {
        when (item.identifier) {
            CONTRIBUTE_ITEM_ID -> contribute()
            else -> navigateTo(item.identifier)

        }
        return false
    }

    private fun navigateTo(screenIdentifier: Long, fragment: Fragment) {
        navigationDrawer?.setSelection(screenIdentifier, false)
        landscapeNavigationDrawer?.setSelection(screenIdentifier, false)

        onBackPressedListener = fragment as? OnBackPressedListener

        SoftInputUtil.hideSoftInput(this)

        displayFragment(fragment)
    }

    private fun navigateTo(screenIdentifier: Long) {
        getFragment(screenIdentifier)
                ?.also { navigateTo(screenIdentifier, it) }
    }

    protected open fun getFragment(screenIdentifier: Long): Fragment? {
        return when (screenIdentifier) {
            DashboardFragment.ID -> factory.getDashboardFragment()
            WithdrawFragment.ID -> factory.getWithdrawFragment()
            ExploreAssetsFragment.ID -> factory.getExploreFragment()
            SettingsFragment.ID -> factory.getSettingsFragment()
            DepositFragment.ID -> factory.getDepositFragment()
            SalesFragment.ID -> factory.getSalesFragment()
            TradeAssetPairsFragment.ID -> factory.getTradeAssetPairsFragment()
            PollsFragment.ID -> factory.getPollsFragment()
            AssetMovementsFragment.ID -> factory.getAssetMovementsFragment()
            BalancesFragment.ID -> factory.getBalancesFragment(withToolbar = true)
            CompaniesFragment.ID -> factory.getCompaniesFragment()
            MarketplaceFragment.ID -> factory
                    .getMarketplaceFragment(withToolbar = true, companyId = null)
            else -> null
        }
    }
    // endregion

    protected open fun updateProfileHeader() {
        val email = walletInfoProvider.getWalletInfo()?.email
                ?: throw IllegalStateException("No email found")

        val h = getProfileHeaderItem(email)
        accountHeader?.updateProfile(h)
        landscapeAccountHeader?.updateProfile(h)
    }

    protected open fun openAccountIdShare() {
        Navigator.from(this).openAccountQrShare()
    }

    private fun contribute() {
        browse(REPO_URL, true)
    }

    private var fragmentToolbarDisposable: Disposable? = null
    private fun displayFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
                .disallowAddToBackStack()
                .setCustomAnimations(R.anim.stay_visible, R.anim.activity_fade_out)
                .replace(R.id.fragment_container_layout, fragment)
                .commit()

        // Bind navigation drawer to fragment's toolbar.
        fragmentToolbarDisposable?.dispose()
        if (fragment is ToolbarProvider) {
            fragmentToolbarDisposable = fragment.toolbarSubject
                    .subscribe { fragmentToolbar ->
                        toolbar = fragmentToolbar
                        fragmentToolbar.apply {
                            setNavigationContentDescription(
                                    com.mikepenz.materialdrawer.R.string.material_drawer_open
                            )
                            setNavigationOnClickListener {
                                navigationDrawer?.openDrawer()
                            }
                        }
                        updateDrawerVisibility()
                    }
                    .addTo(compositeDisposable)
        }
    }

    private fun updateDrawerVisibility() {
        if (tablet && orientation == Configuration.ORIENTATION_LANDSCAPE) {
            navigationDrawer?.drawerLayout?.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            nav_tablet.visibility = View.VISIBLE
            toolbar?.navigationIcon = null
            side_shadow_view.visibility = View.VISIBLE

        } else {
            navigationDrawer?.drawerLayout?.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
            nav_tablet.visibility = View.GONE
            toolbar?.setNavigationIcon(R.drawable.ic_menu)
            side_shadow_view.visibility = View.GONE
        }
    }

    override fun onBackPressed() {
        if (navigationDrawer?.isDrawerOpen == true) {
            navigationDrawer?.closeDrawer()
        } else {
            if (accountHeader?.isSelectionListShown == true) {
                accountHeader?.toggleSelectionList(this)
            }
            if (landscapeAccountHeader?.isSelectionListShown == true) {
                landscapeAccountHeader?.toggleSelectionList(this)
            }
            if (navigationDrawer?.currentSelection == defaultFragmentId) {
                if (onBackPressedListener?.onBackPressed() != false)
                    moveTaskToBack(true)
            } else {
                if (onBackPressedListener?.onBackPressed() != false)
                    navigateTo(defaultFragmentId)
            }
        }
    }

    private var shakeDetectionPostponed = false
    private fun onShakingDetected() {
        if (shakeDetectionPostponed) {
            return
        }

        Navigator.from(this).openShakeToPay()

        shakeDetectionPostponed = true
        Completable.complete()
                .delay(1, TimeUnit.SECONDS)
                .subscribe { shakeDetectionPostponed = false }
                .addTo(compositeDisposable)
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        updateDrawerVisibility()
    }

    override fun onPaymentRequestConfirmed(paymentRequest: PaymentRequest) {
        navigateTo(defaultFragmentId)
    }

    override fun onWithdrawalRequestConfirmed(withdrawalRequest: WithdrawalRequest) {
        navigateTo(defaultFragmentId)
    }

    override fun onAtomicSwapBuyConfirmed() {
        navigateTo(defaultFragmentId)
    }

    override fun onRedemptionRequestAccepted() {}

    override fun onResume() {
        super.onResume()
        ShakeDetector.start();
    }

    override fun onStop() {
        super.onStop()
        ShakeDetector.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        ShakeDetector.destroy()
    }
}