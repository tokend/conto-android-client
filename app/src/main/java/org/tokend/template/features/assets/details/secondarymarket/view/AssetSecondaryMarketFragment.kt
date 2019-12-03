package org.tokend.template.features.assets.details.secondarymarket.view

import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.reactivex.rxkotlin.addTo
import kotlinx.android.synthetic.main.fragment_asset_secondary_market.*
import kotlinx.android.synthetic.main.include_error_empty_view.*
import org.tokend.template.R
import org.tokend.template.data.model.Asset
import org.tokend.template.data.model.AssetRecord
import org.tokend.template.data.model.SimpleAsset
import org.tokend.template.extensions.withArguments
import org.tokend.template.features.offers.model.OfferRecord
import org.tokend.template.features.offers.repository.OffersRepository
import org.tokend.template.fragments.BaseFragment
import org.tokend.template.util.Navigator
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.view.util.LoadingIndicatorManager

class AssetSecondaryMarketFragment : BaseFragment() {
    private val loadingIndicator = LoadingIndicatorManager(
            showLoading = { swipe_refresh.isRefreshing = true },
            hideLoading = { swipe_refresh.isRefreshing = false }
    )

    private var contentIsVisible: Boolean = false
        set(value) {
            field = value
            content_layout.visibility = if (value) View.VISIBLE else View.GONE
        }

    private lateinit var asset: Asset

    private val offersRepository: OffersRepository
        get() = repositoryProvider.offers(
                onlyPrimaryMarket = false,
                baseAsset = asset.code,
                quoteAsset = QUOTE_ASSET_CODE
        )

    private val buyOffer: OfferRecord?
        get() = offersRepository.itemsList.firstOrNull { it.isBuy }

    private val sellOffer: OfferRecord?
        get() = offersRepository.itemsList.firstOrNull { !it.isBuy }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_asset_secondary_market, container, false)
    }

    override fun onInitAllowed() {
        asset = arguments?.getSerializable(ASSET_EXTRA) as? Asset
                ?: throw IllegalArgumentException("No $ASSET_EXTRA specified")

        initSwipeRefresh()
        initButtons()

        subscribeToOffers()

        update()
    }

    private fun initSwipeRefresh() {
        swipe_refresh.setColorSchemeColors(ContextCompat.getColor(requireContext(), R.color.accent))
        swipe_refresh.setOnRefreshListener { update(force = true) }
    }

    private fun initButtons() {
        listOf(create_buy_offer_button, edit_buy_button).forEach {
            it.setOnClickListener { createOffer(isBuy = true) }
        }

        listOf(create_sell_offer_button, edit_sell_button).forEach {
            it.setOnClickListener { createOffer(isBuy = false) }
        }
    }

    private fun update(force: Boolean = false) {
        if (!force) {
            offersRepository.updateIfNotFresh()
        } else {
            offersRepository.update()
        }
    }

    private fun subscribeToOffers() {
        offersRepository
                .loadingSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { loadingIndicator.setLoading(it) }
                .addTo(compositeDisposable)

        offersRepository
                .errorsSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe {
                    if (contentIsVisible) {
                        errorHandlerFactory.getDefault().handle(it)
                    } else {
                        error_empty_view.showError(it, errorHandlerFactory.getDefault()) {
                            update(force = true)
                        }
                    }
                }
                .addTo(compositeDisposable)

        offersRepository
                .itemsSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { onOffersUpdated() }
                .addTo(compositeDisposable)
    }

    private fun onOffersUpdated() {
        if (!offersRepository.isNeverUpdated) {
            displayContent()
        } else {
            contentIsVisible = false
        }
    }

    private fun displayContent() {
        contentIsVisible = true
        error_empty_view.hide()

        updateOfferContent(buyOffer, buy_amount_text_view,
                buy_price_text_view, edit_buy_button, create_buy_offer_button)

        updateOfferContent(sellOffer, sell_amount_text_view,
                sell_price_text_view, edit_sell_button, create_sell_offer_button)
    }

    private fun updateOfferContent(offer: OfferRecord?,
                                   amountTextView: TextView,
                                   priceTextView: TextView,
                                   editButton: View,
                                   createButton: View) {
        if (offer != null) {
            amountTextView.visibility = View.VISIBLE
            priceTextView.visibility = View.VISIBLE
            editButton.visibility = View.VISIBLE
            createButton.visibility = View.GONE

            amountTextView.text = amountFormatter.formatAssetAmount(
                    offer.baseAmount,
                    offer.baseAsset,
                    withAssetCode = false
            )

            priceTextView.text = getString(
                    R.string.template_price_for_item,
                    amountFormatter.formatAssetAmount(
                            offer.price,
                            offer.quoteAsset,
                            withAssetCode = true
                    )
            )
        } else {
            amountTextView.visibility = View.GONE
            priceTextView.visibility = View.GONE
            editButton.visibility = View.GONE
            createButton.visibility = View.VISIBLE
        }
    }

    private fun createOffer(isBuy: Boolean) {
        val prevOffer =
                if (isBuy)
                    buyOffer
                else
                    sellOffer

        val quoteAsset: Asset = repositoryProvider.balances()
                .itemsList
                .find { it.assetCode == QUOTE_ASSET_CODE }
                ?.asset
                ?: repositoryProvider.assets()
                        .itemsList
                        .find { it.code == QUOTE_ASSET_CODE }
                ?: SimpleAsset(QUOTE_ASSET_CODE)

        val navigator = Navigator.from(this)

        if (prevOffer != null) {
            navigator.openUpdateOffer(prevOffer)
        } else {
            navigator.openCreateOffer(
                    baseAsset = asset,
                    quoteAsset = quoteAsset,
                    requiredPrice = null
            )
        }
    }

    companion object {
        // 😈.
        private const val QUOTE_ASSET_CODE = "UAH"

        private const val ASSET_EXTRA = "asset"

        fun newInstance(bundle: Bundle): AssetSecondaryMarketFragment =
                AssetSecondaryMarketFragment().withArguments(bundle)

        fun getBundle(asset: AssetRecord) = Bundle().apply {
            putSerializable(ASSET_EXTRA, asset)
        }
    }
}