package org.tokend.template.features.assets.sell.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.fragment_marketplace_sell_amount.amount_view
import kotlinx.android.synthetic.main.fragment_marketplace_sell_amount.continue_button
import kotlinx.android.synthetic.main.fragment_marketplace_sell_price.*
import org.tokend.template.R
import org.tokend.template.data.model.Asset
import org.tokend.template.data.model.SimpleAsset
import org.tokend.template.extensions.onEditorAction
import org.tokend.template.features.amountscreen.model.AmountInputResult
import org.tokend.template.features.assets.sell.model.MarketplaceSellInfoHolder
import org.tokend.template.fragments.BaseFragment
import org.tokend.template.view.util.input.SoftInputUtil
import java.math.MathContext
import kotlin.math.roundToInt

class MarketplaceSellPriceFragment : BaseFragment() {
    private lateinit var sellInfoHolder: MarketplaceSellInfoHolder

    private val asset: Asset by lazy<Asset> {
        repositoryProvider.balances()
                .itemsList
                .find { it.assetCode == PRICE_ASSET_CODE }
                ?.asset
                ?: SimpleAsset(PRICE_ASSET_CODE)
    }

    private var canContinue: Boolean = false
        set(value) {
            field = value
            continue_button.isEnabled = value
        }

    private val resultSubject = PublishSubject.create<AmountInputResult>()
    val resultObservable: Observable<AmountInputResult> = resultSubject

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_marketplace_sell_price, container, false)
    }

    override fun onInitAllowed() {
        sellInfoHolder = requireActivity() as? MarketplaceSellInfoHolder
                ?: throw IllegalArgumentException("Activity must hold sell info")

        initLabels()
        initFields()
        initButtons()

        onAmountChanged()
    }

    private fun initLabels() {
        top_info_text_view.text = amountFormatter.formatAssetAmount(
                sellInfoHolder.amount,
                sellInfoHolder.balance.asset,
                withAssetName = true
        )

        prices_hint_text_view.text = getString(
                R.string.template_price_hint,
                asset.code
        )
    }

    private fun initFields() {
        amount_view.amountWrapper.apply {
            maxPlacesAfterComa = asset.trailingDigits
            onAmountChanged { _, _ ->
                onAmountChanged()
            }
            if (sellInfoHolder.price.signum() > 0) {
                setAmount(sellInfoHolder.price)
            }
        }

        amount_view.editText.onEditorAction {
            tryToPostResult()
        }

        root_layout.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            val width = right - left
            val oldWidth = oldRight - oldLeft
            if (width != oldWidth) {
                // Max amount input field width is 45% of the container width.
                amount_view.editText.maxWidth = (width * 0.45).roundToInt()
            }
        }

        amount_view.editText.requestFocus()
        SoftInputUtil.showSoftInputOnView(amount_view.editText)
    }

    private fun initButtons() {
        continue_button.setOnClickListener {
            tryToPostResult()
        }
    }

    private fun onAmountChanged() {
        updateContinueAvailability()
        displayTotal()
    }

    private fun updateContinueAvailability() {
        val amount = amount_view.amountWrapper.scaledAmount
        canContinue = amount.signum() > 0
    }

    private fun displayTotal() {
        total_text_view.text = getString(
                R.string.template_amount_total,
                amountFormatter.formatAssetAmount(
                        amount_view.amountWrapper.scaledAmount
                                .multiply(sellInfoHolder.amount, MathContext.DECIMAL64),
                        asset,
                        withAssetCode = false,
                        withAssetName = false
                )
        )
    }

    private fun tryToPostResult() {
        if (canContinue) {
            postResult()
        }
    }

    private fun postResult() {
        resultSubject.onNext(AmountInputResult(
                amount = amount_view.amountWrapper.scaledAmount,
                asset = asset
        ))
    }

    companion object {
        // 😈.
        private const val PRICE_ASSET_CODE = "UAH"

        fun newInstance() = MarketplaceSellPriceFragment()
    }
}