package org.tokend.template.features.assets.sell.view

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.fragment_marketplace_sell_card_number.*
import org.tokend.template.R
import org.tokend.template.data.repository.base.ObjectPersistence
import org.tokend.template.extensions.onEditorAction
import org.tokend.template.features.assets.sell.model.MarketplaceSellInfoHolder
import org.tokend.template.features.assets.sell.storage.CreditCardNumberPersistence
import org.tokend.template.fragments.BaseFragment
import org.tokend.template.util.validator.CreditCardNumberValidator
import org.tokend.template.view.util.input.SoftInputUtil

class MarketplaceSellCardNumberFragment : BaseFragment() {
    private lateinit var sellInfoHolder: MarketplaceSellInfoHolder

    private val resultSubject = PublishSubject.create<String>()
    val resultObservable: Observable<String> = resultSubject

    private var canConfirm: Boolean = false
        set(value) {
            field = value
            confirm_button.isEnabled = value
        }

    private val cardNumberPersistence: ObjectPersistence<String> by lazy {
        CreditCardNumberPersistence(requireActivity().getPreferences(Context.MODE_PRIVATE))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_marketplace_sell_card_number, container, false)
    }

    override fun onInitAllowed() {
        sellInfoHolder = requireActivity() as? MarketplaceSellInfoHolder
                ?: throw IllegalArgumentException("Activity must hold sell info")

        initLabels()
        initFields()
        initButtons()

        updateConfirmationAvailability()
    }

    private fun initLabels() {
        val infoString = amountFormatter.formatAssetAmount(
                sellInfoHolder.amount,
                sellInfoHolder.balance.asset,
                withAssetName = true
        ) +
                "\n" +
                getString(R.string.template_price,
                        amountFormatter.formatAssetAmount(
                                sellInfoHolder.price,
                                sellInfoHolder.priceAsset,
                                withAssetCode = true
                        )
                )

        top_info_text_view.text = infoString
    }

    private fun initFields() {
        card_number_edit_text.addTextChangedListener(object : TextWatcher {
            private var lock = false

            override fun afterTextChanged(s: Editable) {
                if (lock) {
                    return
                }

                lock = true

                for (i in 4 until s.length step 5) {
                    if (s[i] != ' ') {
                        s.insert(i, " ")
                    }
                }

                lock = false

                onCardNumberChanged()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        card_number_edit_text.onEditorAction {
            tryToPostResult()
        }

        cardNumberPersistence.loadItem()?.also {
            card_number_edit_text.setText(it)
        }

        card_number_edit_text.requestFocus()
        SoftInputUtil.showSoftInputOnView(card_number_edit_text)
    }

    private fun initButtons() {
        confirm_button.setOnClickListener {
            tryToPostResult()
        }

        clear_button.setOnClickListener {
            card_number_edit_text.text = null
        }
    }

    private fun onCardNumberChanged() {
        updateClearButtonVisibility()
        updateConfirmationAvailability()
    }

    private fun updateClearButtonVisibility() {
        clear_button.visibility =
                if (card_number_edit_text.text.isNullOrEmpty())
                    View.GONE
                else
                    View.VISIBLE
    }

    private fun updateConfirmationAvailability() {
        canConfirm = readCardNumber()
                .also { Log.i("Oleg", it) }
                .let(CreditCardNumberValidator::isValid)
    }

    private val cleanUpRegex = "(\\n|\\r|\\s)".toRegex()
    private fun readCardNumber(): String {
        return card_number_edit_text.text
                ?.toString()
                ?.trim()
                ?.replace(cleanUpRegex, "")
                ?: ""
    }

    private fun tryToPostResult() {
        if (canConfirm) {
            postResult()
        }
    }

    private fun postResult() {
        val number = readCardNumber()

        if (save_card_number_checkbox.isChecked) {
            cardNumberPersistence.saveItem(number)
        }

        resultSubject.onNext(number)
    }

    companion object {
        fun newInstance() = MarketplaceSellCardNumberFragment()
    }
}