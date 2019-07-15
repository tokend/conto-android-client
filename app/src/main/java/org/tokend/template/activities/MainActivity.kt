package org.tokend.template.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.view.View
import com.mikepenz.fastadapter.IIdentifyable
import com.mikepenz.materialdrawer.AccountHeader
import com.mikepenz.materialdrawer.AccountHeaderBuilder
import com.mikepenz.materialdrawer.Drawer
import com.mikepenz.materialdrawer.DrawerBuilder
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem
import com.mikepenz.materialdrawer.model.ProfileDrawerItem
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem
import com.mikepenz.materialdrawer.util.DrawerImageLoader
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.activity_main.*
import org.tokend.template.BuildConfig
import org.tokend.template.R
import org.tokend.template.data.model.CompanyRecord
import org.tokend.template.data.repository.CompaniesRepository
import org.tokend.template.features.assets.ExploreAssetsFragment
import org.tokend.template.features.dashboard.view.DashboardFragment
import org.tokend.template.features.deposit.DepositFragment
import org.tokend.template.features.invest.view.SalesFragment
import org.tokend.template.features.kyc.storage.KycStateRepository
import org.tokend.template.features.polls.view.PollsFragment
import org.tokend.template.features.send.model.PaymentRequest
import org.tokend.template.features.settings.SettingsFragment
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
import org.tokend.template.view.util.ProgressDialogFactory
import org.tokend.template.view.util.input.SoftInputUtil
import java.util.concurrent.TimeUnit

open class MainActivity : BaseActivity(), WalletEventsListener {
    companion object {
        private val CONTACT_ITEM_ID = "contact".hashCode().toLong()
    }
    protected open val defaultFragmentId = DashboardFragment.ID

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

    private val companiesRepository: CompaniesRepository
        get() = repositoryProvider.companies()

    private val companyPlaceholderDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(this, R.drawable.company_logo_placeholder)
    }
    private val companyPlaceholderBitmap: Bitmap? by lazy {
        val drawable = companyPlaceholderDrawable?.mutate()
                ?: return@lazy null
        val size = resources.getDimensionPixelSize(R.dimen.material_drawer_item_profile_icon)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        bitmap
    }

    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_main)
        window.setBackgroundDrawable(null)

        initNavigation()

        subscribeToKycChanges()
        subscribeToCompanies()

        navigationDrawer?.setSelection(defaultFragmentId)
    }

    // region Init
    @SuppressLint("UseSparseArrays")
    private fun initNavigation() {
        val email = walletInfoProvider.getWalletInfo()?.email

        val placeholderValue = (email ?: getString(R.string.app_name)).toUpperCase()
        val placeholderSize =
                resources.getDimensionPixelSize(R.dimen.material_drawer_item_profile_icon_width)
        val placeholderDrawable =
                ProfileUtil.getAvatarPlaceholder(placeholderValue, this, placeholderSize)
        DrawerImageLoader.init(
                PicassoDrawerImageLoader(this, placeholderDrawable, Color.WHITE,
                        companyPlaceholderDrawable)
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
                    .withName(R.string.dashboard_title)
                    .withIdentifier(DashboardFragment.ID)
                    .withIcon(R.drawable.ic_coins),

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
                    .withName(R.string.contact_company)
                    .withIdentifier(CONTACT_ITEM_ID)
                    .withIcon(R.drawable.ic_email_letter)
                    .withSelectable(false)
    )

    protected open fun getHeaderInstance(email: String?): AccountHeader {
        var header: AccountHeader? = null

        return AccountHeaderBuilder()
                .withActivity(this)
                .withHeaderBackground(
                        ColorDrawable(ContextCompat.getColor(this, R.color.white))
                )
                .withTextColor(ContextCompat.getColor(this, R.color.primary_text))
                .withSelectionListEnabledForSingleProfile(false)
                .withProfileImagesVisible(true)
                .withDividerBelowHeader(true)
                .addProfiles(
                        getProfileHeaderItem(email),
                        *getCompaniesProfileItems().toTypedArray()
                )
                .withOnlyMainProfileImageVisible(true)
                .withCurrentProfileHiddenInList(true)
                .withOnAccountHeaderListener { view, _, isCurrent ->
                    if (isCurrent) {
                        openAccountIdShare()
                    } else {
                        (view.tag as? CompanyRecord)?.also(this::switchToAnotherCompany)
                    }
                    false
                }
                .withOnAccountHeaderSelectionViewClickListener { _, _ ->
                    val selectionWasHidden = header?.isSelectionListShown == false
                    if (selectionWasHidden) {
                        companiesRepository.update()
                    }
                    false
                }
                .build()
                .also { header = it }
    }

    protected open fun getProfileHeaderItem(email: String?): ProfileDrawerItem {
        val kycState = kycStateRepository.item
        val avatarUrl = ProfileUtil.getAvatarUrl(kycState, urlConfigProvider, email)

        return ProfileDrawerItem()
                .withIdentifier(1)
                .withName(email)
                .withEmail(
                        session.getCompany()?.name ?: ""
                )
                .apply {
                    avatarUrl?.also { withIcon(it) }
                }
    }

    protected open fun getCompaniesProfileItems(): Collection<ProfileDrawerItem> {
        return companiesRepository
                .itemsList
                .map { company ->
                    ProfileDrawerItem()
                            .withEmail(company.name)
                            .withIdentifier(company.hashCode().toLong())
                            .withSelectable(false)
                            .withTypeface(
                                    if (session.getCompany() == company)
                                        Typeface.DEFAULT_BOLD
                                    else
                                        Typeface.DEFAULT
                            )
                            .withPostOnBindViewListener { _, view ->
                                view.tag = company
                            }
                            .apply {
                                if (company.logoUrl != null) {
                                    withIcon(company.logoUrl)
                                } else {
                                    withIcon(companyPlaceholderBitmap)
                                }
                            }
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
            addDrawerItems(items[DashboardFragment.ID])

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
                    items[CONTACT_ITEM_ID],
                    items[SettingsFragment.ID]
            )
        }
    }
    // endregion

    private fun subscribeToKycChanges() {
        kycStateRepository
                .itemSubject
                .map { true }
                .subscribe(profileUpdatesSubject)
    }

    private fun subscribeToCompanies() {
        companiesRepository
                .itemsSubject
                .map { true }
                .subscribe(profileUpdatesSubject)
    }

    // region Navigation
    private fun onNavigationItemSelected(item: IDrawerItem<Any, RecyclerView.ViewHolder>)
            : Boolean {
        when (item.identifier) {
            CONTACT_ITEM_ID -> contactCompany()
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
            else -> null
        }
    }
    // endregion

    protected open fun updateProfileHeader() {
        val email = walletInfoProvider.getWalletInfo()?.email

        val mainProfile = getProfileHeaderItem(email)
        val companyProfiles = getCompaniesProfileItems()

        accountHeader?.also { updateProfiles(it, mainProfile, companyProfiles) }
        landscapeAccountHeader?.also { updateProfiles(it, mainProfile, companyProfiles) }
    }

    private fun updateProfiles(header: AccountHeader,
                               mainProfile: ProfileDrawerItem,
                               companyProfiles: Collection<ProfileDrawerItem>) {
        header.updateProfile(mainProfile)

        header.profiles
                ?.filter { it.identifier != mainProfile.identifier }
                ?.map(IIdentifyable<Any>::getIdentifier)
                ?.forEach(header::removeProfileByIdentifier)

        header.addProfiles(*companyProfiles.toTypedArray())
    }

    protected fun openAccountIdShare() {
        val walletInfo = walletInfoProvider.getWalletInfo() ?: return
        Navigator.from(this@MainActivity).openAccountQrShare(walletInfo)
    }

    private fun contactCompany() {
        val currentCompanyAccount = session.getCompany()?.id
                ?: return

        var disposable: Disposable? = null

        val progress = ProgressDialogFactory.getDialog(this, R.string.loading_data) {
            disposable?.dispose()
        }

        disposable = repositoryProvider
                .accountDetails()
                .getEmailByAccountId(currentCompanyAccount)
                .compose(ObservableTransformers.defaultSchedulersSingle())
                .doOnSubscribe { progress.show() }
                .doOnEvent { _, _ -> progress.dismiss() }
                .subscribeBy(
                        onSuccess = this::openEmailCreation,
                        onError = { errorHandlerFactory.getDefault().handle(it) }
                )
                .addTo(compositeDisposable)
    }

    private fun openEmailCreation(recipient: String) {
        val emailIntent = Intent(Intent.ACTION_SENDTO,
                Uri.fromParts("mailto", recipient, null))
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
        startActivity(Intent.createChooser(emailIntent,
                getString(
                        R.string.template_contact_email,
                        recipient
                )
        ))
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

    private fun switchToAnotherCompany(company: CompanyRecord) {
        if (session.getCompany() == company) {
            return
        }

        session.setCompany(company)
        Navigator.from(this).toCompanyLoading()
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

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        updateDrawerVisibility()
    }

    override fun onPaymentRequestConfirmed(paymentRequest: PaymentRequest) {
        navigateTo(DashboardFragment.ID)
    }

    override fun onWithdrawalRequestConfirmed(withdrawalRequest: WithdrawalRequest) {
        navigateTo(DashboardFragment.ID)
    }
}