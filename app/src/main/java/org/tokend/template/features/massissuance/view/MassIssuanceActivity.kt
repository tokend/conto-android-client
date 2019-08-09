package org.tokend.template.features.massissuance.view

import android.app.Activity
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.text.Editable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.activity_mass_issuance.*
import kotlinx.android.synthetic.main.activity_mass_issuance.swipe_refresh
import kotlinx.android.synthetic.main.include_appbar_elevation.*
import kotlinx.android.synthetic.main.toolbar.*
import org.tokend.sdk.utils.extentions.encodeBase64String
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
import org.tokend.template.view.util.LoadingIndicatorManager
import org.tokend.template.view.util.ProgressDialogFactory
import org.tokend.template.view.util.input.AmountEditTextWrapper
import org.tokend.template.view.util.input.SimpleTextWatcher
import java.math.BigDecimal
import java.security.SecureRandom

class MassIssuanceActivity : BaseActivity() {
    private lateinit var amountWrapper: AmountEditTextWrapper

    private val loadingIndicator = LoadingIndicatorManager(
            showLoading = { swipe_refresh.isRefreshing = true },
            hideLoading = { swipe_refresh.isRefreshing = false }
    )

    private val referenceSeed: String = SecureRandom.getSeed(16).encodeBase64String()

    private var issuanceAsset: Asset? = null
        set(value) {
            field = value
            onIssuanceAssetChanged()
        }

    private val assetCode: String?
        get() = intent.getStringExtra(EXTRA_ASSET)

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
        initSwipeRefresh()
        initFields()
        initButtons()
        initAssetSelection()

        subscribeToBalances()

        update()
    }

    // region Init
    private fun initToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setTitle(R.string.issuance_title)
        ElevationUtil.initScrollElevation(scroll_view, appbar_elevation_view)
    }

    private fun initSwipeRefresh() {
        swipe_refresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.accent))
        swipe_refresh.setOnRefreshListener { update(true) }
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

        issuanceAsset = ownedAssets.find { it.code == assetCode } ?: ownedAssets.first()
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

        balancesRepository
                .loadingSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe {
                    loadingIndicator.setLoading(it, "balances")
                }
                .addTo(compositeDisposable)

        balancesRepository.errorsSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe {
                    errorHandlerFactory.getDefault().handle(it)
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
                referenceSeed,
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
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun update(force: Boolean = false) {
        if (force) {
            balancesRepository.update()
        } else {
            balancesRepository.updateIfNotFresh()
        }
    }

    companion object {
        const val EXTRA_EMAILS = "extra_emails"
        const val EXTRA_ASSET = "extra_asset"

        fun getBundle(emails: String?, assetCode: String?) = Bundle().apply {
            putString(EXTRA_EMAILS, emails)
            putString(EXTRA_ASSET, assetCode)
        }
    }
}
