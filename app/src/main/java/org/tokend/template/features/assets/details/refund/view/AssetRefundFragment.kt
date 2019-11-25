package org.tokend.template.features.assets.details.refund.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.fragment_asset_refund.*
import kotlinx.android.synthetic.main.include_error_empty_view.*
import org.tokend.template.R
import org.tokend.template.extensions.withArguments
import org.tokend.template.features.assets.details.refund.logic.AssetRefundOfferLoader
import org.tokend.template.features.trade.orderbook.model.OrderBookEntryRecord
import org.tokend.template.fragments.BaseFragment
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.view.util.UserFlowFragmentDisplayer

class AssetRefundFragment : BaseFragment() {
    private val fragmentDisplayer =
            UserFlowFragmentDisplayer(this, R.id.fragment_container_layout)

    private val refundOfferLoader: AssetRefundOfferLoader by lazy {
        AssetRefundOfferLoader(repositoryProvider)
    }

    private lateinit var assetCode: String

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
    }

    private fun hideLoading() {
        loading_layout.visibility = View.GONE
    }

    private fun onRefundOfferLoaded(offer: OrderBookEntryRecord) {

    }

    private fun onRefundOfferLoadingError(error: Throwable) {
        if (error is AssetRefundOfferLoader.RefundNotAvailableException) {
            error_empty_view.showEmpty(R.string.asset_refund_is_not_available)
        } else {
            error_empty_view.showError(error, errorHandlerFactory.getDefault()) {
                obtainRefundAvailability()
            }
        }
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