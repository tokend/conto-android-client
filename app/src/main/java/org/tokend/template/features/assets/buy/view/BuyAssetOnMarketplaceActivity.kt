package org.tokend.template.features.assets.buy.view

import android.app.Activity
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.view.Menu
import android.view.MenuItem
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.fragment_user_flow.*
import kotlinx.android.synthetic.main.toolbar.*
import org.tokend.sdk.api.integrations.marketplace.model.MarketplaceInvoiceData
import org.tokend.sdk.utils.BigDecimalUtil
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.extensions.getBigDecimalExtra
import org.tokend.template.features.amountscreen.model.AmountInputResult
import org.tokend.template.features.assets.buy.logic.BuyAssetOnMarketplaceUseCase
import org.tokend.template.features.assets.buy.marketplace.logic.PerformMarketplaceInnerPaymentUseCase
import org.tokend.template.features.assets.buy.marketplace.model.BuySummaryExtras
import org.tokend.template.features.assets.buy.marketplace.model.MarketplaceOfferRecord
import org.tokend.template.features.assets.buy.marketplace.view.MarketplaceBuySummaryFragment
import org.tokend.template.features.assets.model.AssetRecord
import org.tokend.template.features.balances.storage.BalancesRepository
import org.tokend.template.logic.TxManager
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.util.navigation.Navigator
import org.tokend.template.view.util.LoadingIndicatorManager
import org.tokend.template.view.util.ProgressDialogFactory
import org.tokend.template.view.util.UserFlowFragmentDisplayer
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
    private lateinit var paymentMethod: MarketplaceOfferRecord.PaymentMethod
    private var promoCode: String? = null

    private val fragmentDisplayer =
            UserFlowFragmentDisplayer(this, R.id.fragment_container_layout)

    private val requiredAmount: BigDecimal? by lazy {
        intent.getBigDecimalExtra(AMOUNT_EXTRA).takeIf { it.signum() > 0 }
    }

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
                MarketplaceBuyAmountFragment.getBundle(offer, requiredAmount)
        )

        fragment
                .resultObservable
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribeBy(
                        onNext = this::onAmountEntered,
                        onError = { errorHandlerFactory.getDefault().handle(it) }
                )
                .addTo(compositeDisposable)
        fragmentDisplayer.display(fragment, "amount", null)
    }

    private fun onAmountEntered(amountData: AmountInputResult) {
        this.amount = amountData.amount
        this.paymentMethod = offer.paymentMethods.first { it.code == amountData.asset.code }

        toSummaryScreen()
    }

    private fun toSummaryScreen() {
        val fragment = MarketplaceBuySummaryFragment.newInstance(
                MarketplaceBuySummaryFragment.getBundle(
                        offer,
                        paymentMethod.id,
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
        fragmentDisplayer.display(fragment, "summary", true)
    }

    private fun onSummaryConfirmed(extras: BuySummaryExtras) {
        this.promoCode = extras.promoCode

        submitBid()
    }

    private fun submitBid() {
        var disposable: Disposable? = null

        val progress = ProgressDialogFactory.getDialog(
                this,
                cancelListener = { disposable?.dispose() }
        )

        disposable = BuyAssetOnMarketplaceUseCase(
                amount = amount,
                paymentMethodId = paymentMethod.id,
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
                Navigator.from(this)
                        .openWebInvoice(invoice.url)
                        .addTo(activityRequestsBag)
                        .doOnSuccess { finishWithSuccessMessage() }
            is MarketplaceInvoiceData.Crypto ->
                openCryptoInvoiceAndFinish(invoice)
            is MarketplaceInvoiceData.Internal ->
                submitInternalPaymentAndFinish(invoice)
            else ->
                errorHandlerFactory.getDefault().handle(
                        NotImplementedError("There is no handler for $invoice")
                )
        }
        SoftInputUtil.hideSoftInput(this)
    }

    private fun openCryptoInvoiceAndFinish(invoice: MarketplaceInvoiceData.Crypto) {
        val asset = paymentMethod.asset
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

    private fun submitInternalPaymentAndFinish(invoice: MarketplaceInvoiceData.Internal) {
        val progress = ProgressDialogFactory.getDialog(this)

        PerformMarketplaceInnerPaymentUseCase(
                invoice,
                offer.asset.code,
                accountProvider,
                repositoryProvider,
                TxManager(apiProvider)
        )
                .perform()
                .compose(ObservableTransformers.defaultSchedulersCompletable())
                .doOnSubscribe { progress.show() }
                .doOnTerminate { progress.dismiss() }
                .subscribeBy(
                        onComplete = this::finishWithSuccessMessage,
                        onError = { errorHandlerFactory.getDefault().handle(it) }
                )
                .addTo(compositeDisposable)
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
        if (!fragmentDisplayer.tryPopBackStack()) {
            finish()
        }
    }

    private fun finishWithSuccessMessage() {
        toastManager.long(R.string.asset_will_be_received_in_a_moment)
        setResult(Activity.RESULT_OK)
        finish()
    }

    companion object {
        private const val OFFER_EXTRA = "offer"
        private const val AMOUNT_EXTRA = "amount"

        fun getBundle(offer: MarketplaceOfferRecord,
                      amount: BigDecimal? = null) = Bundle().apply {
            putSerializable(OFFER_EXTRA, offer)
            putString(AMOUNT_EXTRA, amount?.let(BigDecimalUtil::toPlainString))
        }
    }
}