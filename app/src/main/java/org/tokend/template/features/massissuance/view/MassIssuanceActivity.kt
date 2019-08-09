package org.tokend.template.features.massissuance.view

import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.text.Editable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.activity_mass_issuance.*
import kotlinx.android.synthetic.main.include_appbar_elevation.*
import kotlinx.android.synthetic.main.toolbar.*
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.data.model.Asset
import org.tokend.template.data.model.BalanceRecord
import org.tokend.template.data.repository.BalancesRepository
import org.tokend.template.extensions.hasError
import org.tokend.template.features.massissuance.logic.PerformMassIssuanceUseCase
import org.tokend.template.logic.transactions.TxManager
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.util.validator.EmailValidator
import org.tokend.template.view.balancepicker.BalancePickerBottomDialog
import org.tokend.template.view.util.ElevationUtil
import org.tokend.template.view.util.ProgressDialogFactory
import org.tokend.template.view.util.input.AmountEditTextWrapper
import org.tokend.template.view.util.input.SimpleTextWatcher
import java.math.BigDecimal

class MassIssuanceActivity : BaseActivity() {
    private lateinit var amountWrapper: AmountEditTextWrapper

    private var issuanceAsset: Asset? = null
        set(value) {
            field = value
            onIssuanceAssetChanged()
        }

    private val balancesRepository: BalancesRepository
        get() = repositoryProvider.balances()

    private val balance: BalanceRecord?
        get() = balancesRepository.itemsList.find { it.assetCode == issuanceAsset?.code }

    private var emails: List<String> = emptyList()

    private var canIssue: Boolean = false
        set(value) {
            field = value
            issue_button.isEnabled = value
        }

    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_mass_issuance)

        initToolbar()
        initFields()
        initButtons()
        initAssetSelection()

        subscribeToBalances()
    }

    // region Init
    private fun initToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setTitle(R.string.issuance_title)
        ElevationUtil.initScrollElevation(scroll_view, appbar_elevation_view)
    }

    private fun initFields() {
        amountWrapper = AmountEditTextWrapper(amount_edit_text)
        amountWrapper.onAmountChanged { _, _ ->
            updateIssuanceAvailability()
        }

        amount_edit_text.isHelperTextAlwaysShown = true
        amount_edit_text.requestFocus()

        emails_edit_text.addTextChangedListener(object : SimpleTextWatcher() {
            override fun afterTextChanged(s: Editable?) {
                emails_edit_text.error = null
                updateIssuanceAvailability()
            }
        })

        initEmails()
    }

    private fun initButtons() {
        issue_button.setOnClickListener {
            tryToIssue()
        }
    }

    private fun initAssetSelection() {
        val accountId = walletInfoProvider.getWalletInfo()?.accountId

        val ownedAssets = balancesRepository
                .itemsList
                .sortedWith(balanceComparator)
                .map(BalanceRecord::asset)
                .filter { it.ownerAccountId == accountId }

        if (ownedAssets.isEmpty()) {
            errorHandlerFactory.getDefault().handle(IllegalStateException(
                    "No owned assets found"
            ))
            finish()
            return
        }

        val picker = object : BalancePickerBottomDialog(
                this,
                amountFormatter,
                balanceComparator,
                balancesRepository,
                ownedAssets
        ) {
            override fun getAvailableAmount(assetCode: String, balance: BalanceRecord?): BigDecimal? {
                return getAvailableIssuanceAmount(balance)
            }
        }

        asset_edit_text.setOnClickListener {
            picker.show { result ->
                issuanceAsset = result.asset
            }
        }

        val dropDownArrow = ContextCompat.getDrawable(this, R.drawable.ic_arrow_drop_down)
        asset_edit_text.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null,
                dropDownArrow, null)

        issuanceAsset = ownedAssets.first()
    }

    private fun initEmails() {
        val emails = intent.getStringExtra(EXTRA_EMAILS) ?: return
        emails_edit_text.setText(emails)
        amount_edit_text.requestFocus()
    }
    // endregion

    private fun subscribeToBalances() {
        balancesRepository
                .itemsSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe {
                    updateIssuanceAvailability()
                }
                .addTo(compositeDisposable)
    }

    private fun onIssuanceAssetChanged() {
        asset_edit_text.setText(issuanceAsset?.name ?: issuanceAsset?.code)
        updateIssuanceAvailability()
    }

    private fun updateAmountHelperAndError() {
        val available = getAvailableIssuanceAmount(balance)

        if (amountWrapper.scaledAmount > available) {
            amount_edit_text.error = getString(R.string.error_insufficient_balance)
        } else {
            amount_edit_text.error = null
            val asset = issuanceAsset ?: return
            amount_edit_text.setHelperText(
                    getString(R.string.template_available,
                            amountFormatter.formatAssetAmount(
                                    available,
                                    asset,
                                    withAssetCode = false
                            )
                    )
            )
        }
    }

    private fun getAvailableIssuanceAmount(balance: BalanceRecord?): BigDecimal {
        return balance?.available ?: BigDecimal.ZERO
    }

    private fun updateIssuanceAvailability() {
        updateAmountHelperAndError()

        canIssue = amountWrapper.scaledAmount.signum() > 0
                && !amount_edit_text.hasError()
                && !emails_edit_text.hasError()
                && !emails_edit_text.text.isNullOrBlank()
    }

    private fun readAndCheckEmails() {
        emails = (emails_edit_text.text ?: "")
                .split(',')
                .filter(String::isNotBlank)
                .map(String::trim)

        if (emails.any { !EmailValidator.isValid(it) }) {
            emails_edit_text.error = getString(R.string.error_missed_comma_or_invalid_email)
            updateIssuanceAvailability()
        }
    }

    private fun tryToIssue() {
        readAndCheckEmails()

        if (canIssue) {
            issue()
        }
    }

    private fun issue() {
        val assetCode = issuanceAsset?.code
                ?: return
        val amount = amountWrapper.scaledAmount

        val progress = ProgressDialogFactory.getDialog(this)

        PerformMassIssuanceUseCase(
                emails,
                assetCode,
                amount,
                walletInfoProvider,
                repositoryProvider,
                accountProvider,
                TxManager(apiProvider)
        )
                .perform()
                .compose(ObservableTransformers.defaultSchedulersCompletable())
                .doOnSubscribe {
                    progress.show()
                }
                .doOnEvent {
                    progress.dismiss()
                }
                .subscribeBy(
                        onComplete = this::onSuccessfulIssuance,
                        onError = { errorHandlerFactory.getDefault().handle(it) }
                )
                .addTo(compositeDisposable)
    }

    private fun onSuccessfulIssuance() {
        toastManager.short(R.string.successfully_issued)
        finish()
    }

    companion object {
        const val EXTRA_EMAILS = "extra_emails"

        fun getBundle(emails: String?) = Bundle().apply {
            putString(EXTRA_EMAILS, emails)
        }
    }
}
