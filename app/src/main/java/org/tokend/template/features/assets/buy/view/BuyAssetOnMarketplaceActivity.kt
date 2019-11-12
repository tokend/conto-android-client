package org.tokend.template.features.assets.buy.view

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentTransaction
import android.support.v4.content.ContextCompat
import android.view.Menu
import android.view.MenuItem
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.fragment_user_flow.*
import kotlinx.android.synthetic.main.toolbar.*
import org.tokend.sdk.api.integrations.marketplace.model.MarketplaceInvoiceData
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.data.model.Asset
import org.tokend.template.data.model.AssetRecord
import org.tokend.template.data.repository.BalancesRepository
import org.tokend.template.features.amountscreen.model.AmountInputResult
import org.tokend.template.features.assets.buy.logic.BuyAssetOnMarketplaceUseCase
import org.tokend.template.features.assets.buy.marketplace.model.BuySummaryExtras
import org.tokend.template.features.assets.buy.marketplace.model.MarketplaceOfferRecord
import org.tokend.template.features.assets.buy.marketplace.view.MarketplaceBuySummaryFragment
import org.tokend.template.util.Navigator
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.view.util.LoadingIndicatorManager
import org.tokend.template.view.util.ProgressDialogFactory
import org.tokend.template.view.util.input.SoftInputUtil
import java.math.BigDecimal

class BuyAssetOnMarketplaceActivity : BaseActivity() {
    private val loadingIndicator = LoadingIndicatorManager(
            showLoading = { swipe_refresh.isRefreshing = true },
            hideLoading = { swipe_refresh.isRefreshing = false }
    )

    private val balancesRepository: BalancesRepository
        get() = repositoryProvider.balances()

    private lateinit var offer: MarketplaceOfferRecord
    private var amount: BigDecimal = BigDecimal.ZERO
    private var asset: Asset? = null
    private var promoCode: String? = null

    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.fragment_user_flow)

        val ask = intent.getSerializableExtra(OFFER_EXTRA) as? MarketplaceOfferRecord
        if (ask == null) {
            finishWithMissingArgError(OFFER_EXTRA)
            return
        }
        this.offer = ask

        initToolbar()
        initSwipeRefresh()
        subscribeToBalances()
        toAmountScreen()
    }

    // region Init
    private fun initToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.buy)
    }

    private fun initSwipeRefresh() {
        swipe_refresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.accent))
        swipe_refresh.setOnRefreshListener { balancesRepository.update() }
    }
    // endregion

    private fun toAmountScreen() {
        val fragment = MarketplaceBuyAmountFragment.newInstance(
                MarketplaceBuyAmountFragment.getBundle(offer)
        )

        fragment
                .resultObservable
                .compose(ObservableTransformers.defaultSchedulers())
                .map { it as AmountInputResult }
                .subscribeBy(
                        onNext = this::onAmountEntered,
                        onError = { errorHandlerFactory.getDefault().handle(it) }
                )
                .addTo(compositeDisposable)
        displayFragment(fragment, "amount", null)
    }

    private fun onAmountEntered(amountData: AmountInputResult) {
        this.amount = amountData.amount
        this.asset = amountData.asset

        toSummaryScreen()
    }

    private fun toSummaryScreen() {
        val fragment = MarketplaceBuySummaryFragment.newInstance(
                MarketplaceBuySummaryFragment.getBundle(
                        offer,
                        offer.paymentMethods.first { it.asset.code == asset?.code }.id,
                        amount
                )
        )

        fragment
                .resultObservable
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribeBy(
                        onNext = this::onSummaryConfirmed,
                        onError = { errorHandlerFactory.getDefault().handle(it) }
                )
                .addTo(compositeDisposable)
        displayFragment(fragment, "summary", true)
    }

    private fun onSummaryConfirmed(extras: BuySummaryExtras) {
        this.promoCode = extras.promoCode

        submitBid()
    }

    private fun submitBid() {
        val assetCode = asset?.code
                ?: return

        var disposable: Disposable? = null

        val progress = ProgressDialogFactory.getDialog(
                this,
                cancelListener = { disposable?.dispose() }
        )

        disposable = BuyAssetOnMarketplaceUseCase(
                amount = amount,
                quoteAssetCode = assetCode,
                offer = offer,
                promoCode = promoCode,
                repositoryProvider = repositoryProvider,
                walletInfoProvider = walletInfoProvider,
                apiProvider = apiProvider
        )
                .perform()
                .compose(ObservableTransformers.defaultSchedulersSingle())
                .doOnSubscribe { progress.show() }
                .doOnEvent { _, _ -> progress.dismiss() }
                .subscribeBy(
                        onSuccess = this::onBidSubmitted,
                        onError = { errorHandlerFactory.getDefault().handle(it) }
                )
                .addTo(compositeDisposable)
    }

    private fun onBidSubmitted(invoice: MarketplaceInvoiceData) {
        when (invoice) {
            is MarketplaceInvoiceData.Redirect ->
                Navigator.from(this).openWebInvoice(invoice.url, WEB_INVOICE_REQUEST)
            is MarketplaceInvoiceData.Crypto ->
                openCryptoInvoiceAndFinish(invoice)
            else ->
                errorHandlerFactory.getDefault().handle(
                        NotImplementedError("There is no handler for $invoice")
                )
        }
        SoftInputUtil.hideSoftInput(this)
    }

    private fun openCryptoInvoiceAndFinish(invoice: MarketplaceInvoiceData.Crypto) {
        val asset = this.asset ?: return
        val sendAmountString = amountFormatter.formatAssetAmount(invoice.amount, asset)
        val receiveAmountString = amountFormatter.formatAssetAmount(amount, offer.asset)

        Navigator.from(this).openQrShare(
                title = this.title.toString(),
                shareLabel = getString(R.string.share_address_label),
                data = invoice.address,
                shareText = getString(
                        R.string.template_atomic_swap_invoice_share_text,
                        sendAmountString,
                        receiveAmountString,
                        invoice.address
                ),
                topText = getString(R.string.template_send_to_address, sendAmountString)
        )
        finish()
    }

    private fun displayFragment(
            fragment: Fragment,
            tag: String,
            forward: Boolean?
    ) {
        supportFragmentManager.beginTransaction()
                .setTransition(
                        when (forward) {
                            true -> FragmentTransaction.TRANSIT_FRAGMENT_OPEN
                            false -> FragmentTransaction.TRANSIT_FRAGMENT_CLOSE
                            null -> FragmentTransaction.TRANSIT_NONE
                        }
                )
                .replace(R.id.fragment_container_layout, fragment)
                .addToBackStack(tag)
                .commit()
    }

    private fun subscribeToBalances() {
        balancesRepository.loadingSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { loadingIndicator.setLoading(it, "balances") }
                .addTo(compositeDisposable)

        balancesRepository.errorsSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe {
                    errorHandlerFactory.getDefault().handle(it)
                }
                .addTo(compositeDisposable)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.balance_details, menu)

        menu?.findItem(R.id.asset_details)?.setOnMenuItemClickListener {
            (offer.asset as? AssetRecord)?.also { asset ->
                Navigator.from(this).openAssetDetails(asset)
            }
            true
        }

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.asset_details -> openAssetDetails()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun openAssetDetails() {
        (offer.asset as? AssetRecord)?.also { asset ->
            Navigator.from(this).openAssetDetails(asset)
        }
    }

    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount <= 1) {
            finish()
        } else {
            supportFragmentManager.popBackStackImmediate()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == WEB_INVOICE_REQUEST && resultCode == Activity.RESULT_OK) {
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    companion object {
        private const val OFFER_EXTRA = "offer"
        private val WEB_INVOICE_REQUEST = "web_invoice".hashCode() and 0xffff

        fun getBundle(offer: MarketplaceOfferRecord) = Bundle().apply {
            putSerializable(OFFER_EXTRA, offer)
        }
    }
}