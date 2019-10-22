package org.tokend.template.features.assets.buy.marketplace.view

import android.os.Bundle
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.rengwuxian.materialedittext.MaterialEditText
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.fragment_marketplace_buy_summary.*
import kotlinx.android.synthetic.main.include_appbar_elevation.*
import kotlinx.android.synthetic.main.layout_progress.*
import kotlinx.android.synthetic.main.layout_promo_code_input.view.*
import org.tokend.sdk.api.integrations.marketplace.model.MarketplaceOfferPrice
import org.tokend.sdk.utils.BigDecimalUtil
import org.tokend.sdk.utils.extentions.isBadRequest
import org.tokend.template.R
import org.tokend.template.data.model.SimpleAsset
import org.tokend.template.extensions.setErrorAndFocus
import org.tokend.template.extensions.withArguments
import org.tokend.template.features.assets.buy.marketplace.logic.MarketplaceOfferPriceLoader
import org.tokend.template.features.assets.buy.marketplace.model.BuySummaryExtras
import org.tokend.template.features.assets.buy.marketplace.model.MarketplaceOfferRecord
import org.tokend.template.fragments.BaseFragment
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.view.InfoCard
import org.tokend.template.view.util.AnimationUtil
import org.tokend.template.view.util.CircleLogoUtil
import org.tokend.template.view.util.ElevationUtil
import org.tokend.template.view.util.LoadingIndicatorManager
import org.tokend.template.view.util.input.SimpleTextWatcher
import retrofit2.HttpException
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

class MarketplaceBuySummaryFragment : BaseFragment() {
    private val resultSubject = PublishSubject.create<BuySummaryExtras>()
    val resultObservable: Observable<BuySummaryExtras> = resultSubject

    private val loadingIndicator = LoadingIndicatorManager(
            showLoading = { progress.show() },
            hideLoading = { progress.hide() }
    )

    private lateinit var offer: MarketplaceOfferRecord
    private lateinit var paymentMethod: MarketplaceOfferRecord.PaymentMethod
    private lateinit var amount: BigDecimal

    private lateinit var priceLoader: MarketplaceOfferPriceLoader

    private lateinit var promoCodeEditText: MaterialEditText
    private lateinit var promoCodeOkView: View
    private var promoCode: String? = null
        set(value) {
            val isTheSame = field == value
            field = value
            if (!isTheSame) {
                onPromoCodeUpdated()
            }
        }

    private lateinit var toPayAmount: BigDecimal
    private lateinit var toPayAssetCode: String
    private var discountPercents: BigDecimal = BigDecimal.ZERO
    private var isPromoCodeAccepted: Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_marketplace_buy_summary, container, false)
    }

    override fun onInitAllowed() {
        val offer = arguments?.getSerializable(OFFER_EXTRA) as? MarketplaceOfferRecord
        requireNotNull(offer) { "Missing $offer" }
        this.offer = offer

        val paymentMethodId = arguments?.getString(PAYMENT_METHOD_ID_EXTRA)
        paymentMethod = offer.paymentMethods.find { it.id == paymentMethodId }
                ?: throw IllegalArgumentException("Missing $PAYMENT_METHOD_ID_EXTRA")

        amount = arguments?.getString(AMOUNT_EXTRA).let { BigDecimalUtil.valueOf(it) }
        require(amount.signum() != 0) { "Missing $AMOUNT_EXTRA" }

        priceLoader = MarketplaceOfferPriceLoader(apiProvider, offer.id, paymentMethod.id)

        initElevation()
        initButtons()

        displayItemToBuy()
        displayPromoCodeCard()

        calculateInitialPayAmount()
    }

    private fun initElevation() {
        ElevationUtil.initScrollElevation(scroll_view, appbar_elevation_view)
    }

    private fun initButtons() {
        buy_btn.setOnClickListener {
            publishResult()
        }
    }

    private fun displayItemToBuy() {
        CircleLogoUtil.setAssetLogo(asset_logo_image_view, offer.asset)
        buy_amount_text_view.text = amountFormatter.formatAssetAmount(
                amount,
                offer.asset,
                withAssetCode = false
        )
        asset_name_text_view.text = offer.asset.name ?: offer.asset.code
    }

    private fun displayPromoCodeCard() {
        val promoCodeInputLayout = layoutInflater.inflate(R.layout.layout_promo_code_input,
                null, false)

        promoCodeOkView = promoCodeInputLayout.promo_code_ok_image_view

        promoCodeEditText = promoCodeInputLayout.promo_code_edit_text
        promoCodeEditText.addTextChangedListener(object : SimpleTextWatcher() {
            override fun afterTextChanged(s: Editable?) {
                promoCodeEditText.error = null
                promoCode = s?.toString()?.trim()?.takeIf(String::isNotBlank)
            }
        })

        InfoCard(cards_layout)
                .setHeading(R.string.promo_code, null)
                .addView(promoCodeInputLayout)
    }

    private fun calculateInitialPayAmount() {
        toPayAmount = BigDecimalUtil.scaleAmount(
                amount * offer.price,
                offer.priceAsset.trailingDigits
        )
        toPayAssetCode = offer.priceAsset.code
        discountPercents = BigDecimal.ZERO
        onPayAmountUpdated()
    }

    private fun onPromoCodeUpdated() {
        isPromoCodeAccepted = false
        schedulePayAmountLoading()
        updatePromoCodeIndicator()
    }

    private var amountLoadingDisposable: Disposable? = null
    private fun schedulePayAmountLoading() {
        amountLoadingDisposable?.dispose()

        val promoCode = this.promoCode
        val delaySingle = Single.just(true).delay(AMOUNT_LOADING_DELAY, TimeUnit.MILLISECONDS)

        amountLoadingDisposable = delaySingle
                .flatMap { priceLoader.load(amount, promoCode) }
                .compose(ObservableTransformers.defaultSchedulersSingle())
                .retry(3) { !(it is HttpException && it.isBadRequest()) }
                .doOnSubscribe {
                    loadingIndicator.show()
                }
                .doOnEvent { _, _ ->
                    loadingIndicator.hide()
                }
                .subscribeBy(
                        onSuccess = { onPayAmountLoaded(it, promoCode) },
                        onError = this::onPayAmountLoadingError
                )
                .addTo(compositeDisposable)
    }

    private fun onPayAmountLoaded(data: MarketplaceOfferPrice,
                                  promoCode: String?) {
        this.isPromoCodeAccepted = promoCode != null
        this.toPayAssetCode = data.assetCode
        this.discountPercents = data.discount * BigDecimal(100)
        this.toPayAmount = data.totalPrice
        onPayAmountUpdated()
    }

    private fun onPayAmountLoadingError(error: Throwable) {
        when {
            error is HttpException && error.isBadRequest() -> {
                promoCodeEditText.setErrorAndFocus(R.string.error_invalid_promo_code)
                calculateInitialPayAmount()
            }
            else -> errorHandlerFactory.getDefault().handle(error)
        }
    }

    private fun onPayAmountUpdated() {
        displayPayAmount()
        updatePromoCodeIndicator()
    }

    private fun displayPayAmount() {
        amount_text_view.text = amountFormatter.formatAssetAmount(
                toPayAmount,
                SimpleAsset(toPayAssetCode),
                withAssetCode = true
        )

        val discount = this.discountPercents
        if (discount.signum() > 0) {
            discount_text_view.text = getString(
                    R.string.template_discount_percents,
                    amountFormatter.formatAmount(
                            discountPercents,
                            maxDecimalDigits = 2
                    )
            )
        } else {
            discount_text_view.text = ""
        }
    }

    private fun updatePromoCodeIndicator() {
        if (isPromoCodeAccepted && promoCodeOkView.visibility != View.VISIBLE) {
            AnimationUtil.fadeInView(promoCodeOkView)
        } else {
            promoCodeOkView.clearAnimation()
            promoCodeOkView.visibility = View.INVISIBLE
        }
    }

    private fun publishResult() {
        resultSubject.onNext(
                BuySummaryExtras(promoCode)
        )
    }

    companion object {
        private const val OFFER_EXTRA = "offer"
        private const val PAYMENT_METHOD_ID_EXTRA = "payment_method"
        private const val AMOUNT_EXTRA = "amount"
        private const val AMOUNT_LOADING_DELAY = 700L

        fun getBundle(offer: MarketplaceOfferRecord,
                      paymentMethodId: String,
                      amount: BigDecimal) = Bundle().apply {
            putSerializable(OFFER_EXTRA, offer)
            putString(PAYMENT_METHOD_ID_EXTRA, paymentMethodId)
            putString(AMOUNT_EXTRA, BigDecimalUtil.toPlainString(amount))
        }

        fun newInstance(bundle: Bundle): MarketplaceBuySummaryFragment =
                MarketplaceBuySummaryFragment().withArguments(bundle)
    }
}