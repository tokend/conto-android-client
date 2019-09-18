package org.tokend.template.features.swap.create.view

import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.reactivex.Observable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.fragment_swap_quote_amount_and_counterparty.*
import kotlinx.android.synthetic.main.layout_progress.*
import org.jetbrains.anko.dip
import org.tokend.template.R
import org.tokend.template.data.model.Asset
import org.tokend.template.data.model.BalanceRecord
import org.tokend.template.data.repository.BalancesRepository
import org.tokend.template.data.repository.assets.AssetsRepository
import org.tokend.template.extensions.onEditorAction
import org.tokend.template.extensions.setErrorAndFocus
import org.tokend.template.extensions.withArguments
import org.tokend.template.features.send.model.PaymentRecipient
import org.tokend.template.features.send.recipient.logic.PaymentRecipientLoader
import org.tokend.template.features.swap.create.model.SwapQuoteAmountAndCounterparty
import org.tokend.template.fragments.BaseFragment
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.view.balancepicker.BalancePickerBottomDialog
import org.tokend.template.view.util.LoadingIndicatorManager
import org.tokend.template.view.util.input.AmountEditTextWrapper
import org.tokend.template.view.util.input.SimpleTextWatcher
import java.math.BigDecimal

class SwapQuoteAmountAndCounterpartyFragment : BaseFragment() {
    private val resultSubject = PublishSubject.create<SwapQuoteAmountAndCounterparty>()
    val resultObservable: Observable<SwapQuoteAmountAndCounterparty> = resultSubject

    private val balancesRepository: BalancesRepository
        get() = repositoryProvider.balances()

    private val assetsRepository: AssetsRepository
        get() = repositoryProvider.assets()

    private val loadingIndicator = LoadingIndicatorManager(
            showLoading = { progress.show() },
            hideLoading = { progress.hide() }
    )

    private val baseAssetCode: String by lazy {
        arguments?.getString(BASE_ASSET_CODE_EXTRA)
                ?: throw IllegalArgumentException("No $BASE_ASSET_CODE_EXTRA specified")
    }

    private var canContinue: Boolean = false
        set(value) {
            field = value
            confirm_button.isEnabled = value
        }

    private lateinit var amountWrapper: AmountEditTextWrapper

    private var asset: Asset? = null
        set(value) {
            field = value
            onAssetChanged()
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_swap_quote_amount_and_counterparty, container, false)
    }

    override fun onInitAllowed() {
        initFields()
        initButtons()
        initAssetSelection()

        canContinue = false
    }

    private fun initFields() {
        amountWrapper = AmountEditTextWrapper(amount_edit_text)
        amountWrapper.onAmountChanged { _, _ ->
            updateContinueAvailability()
        }
        amount_edit_text.requestFocus()

        counterparty_edit_text.setPaddings(0, 0,
                requireContext().dip(42), 0)
        counterparty_edit_text.onEditorAction { tryToContinue() }
        counterparty_edit_text.addTextChangedListener(object : SimpleTextWatcher() {
            override fun afterTextChanged(s: Editable?) {
                counterparty_edit_text.error = null
                updateContinueAvailability()
            }
        })
    }

    private fun initButtons() {
        confirm_button.setOnClickListener {
            tryToContinue()
        }
    }

    private fun initAssetSelection() {
        val assets = assetsRepository.itemsList
                .filter { it.code != baseAssetCode }

        val picker = object : BalancePickerBottomDialog(
                requireContext(),
                amountFormatter,
                balanceComparator,
                balancesRepository,
                assetsRepository.itemsList,
                { balance -> balance.assetCode != baseAssetCode }
        ) {
            override fun getAvailableAmount(assetCode: String,
                                            balance: BalanceRecord?): BigDecimal? = null
        }

        asset_edit_text.setOnClickListener {
            picker.show { result ->
                asset = result.asset
            }
        }

        val dropDownArrow = ContextCompat.getDrawable(requireContext(), R.drawable.ic_arrow_drop_down)
        asset_edit_text.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null,
                dropDownArrow, null)

        asset = assets.first()
    }

    private fun onAssetChanged() {
        asset_edit_text.setText(asset?.name ?: asset?.code)
    }

    private fun updateContinueAvailability() {
        canContinue = amountWrapper.scaledAmount.signum() > 0
                && !counterparty_edit_text.text.isNullOrBlank()
                && !loadingIndicator.isLoading
    }

    private fun tryToContinue() {
        if (canContinue) {
            loadCounterpartyAndFinish()
        }
    }

    private fun loadCounterpartyAndFinish() {
        val counterpartyEmail = counterparty_edit_text.text?.toString()
                ?: return

        PaymentRecipientLoader(repositoryProvider.accountDetails(), apiProvider)
                .load(counterpartyEmail)
                .compose(ObservableTransformers.defaultSchedulersSingle())
                .doOnSubscribe {
                    loadingIndicator.show()
                    updateContinueAvailability()
                }
                .doOnEvent { _, _ ->
                    loadingIndicator.hide()
                    updateContinueAvailability()
                }
                .subscribeBy(
                        onSuccess = this::onCounterpartyLoaded,
                        onError = this::onCounterpartyLoadingError
                )
                .addTo(compositeDisposable)
    }

    private fun onCounterpartyLoaded(counterparty: PaymentRecipient) {
        val asset = this.asset
                ?: return

        resultSubject.onNext(SwapQuoteAmountAndCounterparty(
                amount = amountWrapper.scaledAmount,
                asset = asset,
                counterparty = counterparty
        ))
    }

    private fun onCounterpartyLoadingError(error: Throwable) {
        when (error) {
            is PaymentRecipientLoader.NoRecipientFoundException ->
                counterparty_edit_text.setErrorAndFocus(R.string.error_invalid_counterparty)
            else ->
                errorHandlerFactory.getDefault().handle(error)
        }
    }

    companion object {
        private const val BASE_ASSET_CODE_EXTRA = "base_asset_code"

        fun getBundle(baseAssetCode: String) = Bundle().apply {
            putString(BASE_ASSET_CODE_EXTRA, baseAssetCode)
        }

        fun newInstance(bundle: Bundle): SwapQuoteAmountAndCounterpartyFragment =
                SwapQuoteAmountAndCounterpartyFragment().withArguments(bundle)
    }
}