package org.tokend.template.features.assets.details.refund.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.fragment_asset_refund.*
import kotlinx.android.synthetic.main.include_error_empty_view.*
import org.tokend.template.R
import org.tokend.template.extensions.withArguments
import org.tokend.template.features.amountscreen.model.AmountInputResult
import org.tokend.template.features.assets.details.refund.logic.AssetRefundOfferLoader
import org.tokend.template.features.fees.logic.FeeManager
import org.tokend.template.features.offers.logic.CreateOfferRequestUseCase
import org.tokend.template.features.offers.model.OfferRequest
import org.tokend.template.features.trade.orderbook.model.OrderBookEntryRecord
import org.tokend.template.fragments.BaseFragment
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.util.navigation.Navigator
import org.tokend.template.view.util.ProgressDialogFactory
import org.tokend.template.view.util.UserFlowFragmentDisplayer
import java.math.BigDecimal

class AssetRefundFragment : BaseFragment() {
    private val fragmentDisplayer =
            UserFlowFragmentDisplayer(this, R.id.fragment_container_layout)

    private val refundOfferLoader: AssetRefundOfferLoader by lazy {
        AssetRefundOfferLoader(repositoryProvider)
    }

    private lateinit var assetCode: String
    private lateinit var offer: OrderBookEntryRecord
    private lateinit var amount: BigDecimal

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_asset_refund, container, false)
    }

    override fun onInitAllowed() {
        val assetCode = arguments?.getString(ASSET_CODE_EXTRA)
                ?: throw IllegalArgumentException("Missing $ASSET_CODE_EXTRA")
        this.assetCode = assetCode

        obtainRefundAvailability()
    }

    private fun obtainRefundAvailability() {
        refundOfferLoader
                .load(
                        assetToRefund = assetCode,
                        refundAssetCode = REFUND_ASSET_CODE
                )
                .compose(ObservableTransformers.defaultSchedulersSingle())
                .doOnSubscribe { showLoading() }
                .doOnEvent { _, _ -> hideLoading() }
                .subscribeBy(
                        onSuccess = this::onRefundOfferLoaded,
                        onError = this::onRefundOfferLoadingError
                )
                .addTo(compositeDisposable)
    }

    private fun showLoading() {
        error_empty_view.hide()
        loading_layout.visibility = View.VISIBLE
        fragment_container_layout.visibility = View.GONE
    }

    private fun hideLoading() {
        loading_layout.visibility = View.GONE
        fragment_container_layout.visibility = View.VISIBLE
    }

    private fun onRefundOfferLoaded(offer: OrderBookEntryRecord) {
        this.offer = offer
        toAmountInput()
    }


    private fun onRefundOfferLoadingError(error: Throwable) {
        fragment_container_layout.removeAllViews()
        if (error is AssetRefundOfferLoader.RefundNotAvailableException) {
            error_empty_view.showEmpty(R.string.asset_refund_is_not_available)
        } else {
            error_empty_view.showError(error, errorHandlerFactory.getDefault()) {
                obtainRefundAvailability()
            }
        }
    }

    private fun toAmountInput() {
        val fragment = AssetRefundAmountFragment.newInstance(
                AssetRefundAmountFragment.getBundle(offer)
        )

        fragment.resultObservable
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { onAmountEntered((it as AmountInputResult).amount)  }
                .addTo(compositeDisposable)

        fragmentDisplayer.display(fragment, "amount", true)
    }

    private fun onAmountEntered(amount: BigDecimal) {
        this.amount = amount
        createAndConfirmOffer()
    }

    private fun createAndConfirmOffer() {
        var disposable: Disposable? = null

        val progress = ProgressDialogFactory.getDialog(requireContext(), R.string.loading_data) {
            disposable?.dispose()
        }

        disposable = CreateOfferRequestUseCase(
                baseAmount = amount,
                baseAsset = offer.baseAsset,
                price = offer.price,
                quoteAsset = offer.quoteAsset,
                orderBookId = 0L,
                isBuy = false,
                offerToCancel = null,
                walletInfoProvider = walletInfoProvider,
                feeManager = FeeManager(apiProvider)
        )
                .perform()
                .compose(ObservableTransformers.defaultSchedulersSingle())
                .doOnSubscribe { progress.show() }
                .doOnEvent { _, _ -> progress.dismiss() }
                .subscribeBy(
                        onSuccess = this::onOfferRequestCreated,
                        onError = { errorHandlerFactory.getDefault().handle(it) }
                )
    }

    private fun onOfferRequestCreated(offerRequest: OfferRequest) {
        openConfirmation(offerRequest)
    }

    private fun openConfirmation(offerRequest: OfferRequest) {
        Navigator.from(this)
                .openAssetRefundConfirmation(offerRequest)
                .addTo(activityRequestsBag)
                .doOnSuccess { onOfferConfirmed() }
    }

    private fun onOfferConfirmed() {
        fragmentDisplayer.remove("amount")
        obtainRefundAvailability()
    }

    companion object {
        // 😈.
        private const val REFUND_ASSET_CODE = "UAH"

        private const val ASSET_CODE_EXTRA = "asset"

        fun getBundle(assetCode: String) = Bundle().apply {
            putString(ASSET_CODE_EXTRA, assetCode)
        }

        fun newInstance(bundle: Bundle): AssetRefundFragment =
                AssetRefundFragment().withArguments(bundle)
    }
}