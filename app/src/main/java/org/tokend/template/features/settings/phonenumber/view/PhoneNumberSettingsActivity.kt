package org.tokend.template.features.settings.phonenumber.view

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.Auth
import com.google.android.gms.auth.api.credentials.Credential
import com.google.android.gms.auth.api.credentials.HintRequest
import com.google.android.gms.common.api.GoogleApiClient
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.activity_phone_number_settings.*
import kotlinx.android.synthetic.main.layout_progress.*
import kotlinx.android.synthetic.main.toolbar.*
import org.jetbrains.anko.enabled
import org.jetbrains.anko.sp
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.data.repository.AccountDetailsRepository
import org.tokend.template.extensions.hasError
import org.tokend.template.extensions.onEditorAction
import org.tokend.template.extensions.setErrorAndFocus
import org.tokend.template.features.settings.phonenumber.logic.SetPhoneNumberUseCase
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.util.errorhandler.CompositeErrorHandler
import org.tokend.template.util.errorhandler.ErrorHandler
import org.tokend.template.util.validator.GlobalPhoneNumberValidator
import org.tokend.template.view.util.LoadingIndicatorManager
import org.tokend.template.view.util.input.EditTextErrorHandler
import org.tokend.template.view.util.input.SimpleTextWatcher
import org.tokend.template.view.util.input.SoftInputUtil


class PhoneNumberSettingsActivity : BaseActivity() {
    private val currentNumberLoadingIndicator = LoadingIndicatorManager(
            showLoading = { swipe_refresh.isRefreshing = true },
            hideLoading = { swipe_refresh.isRefreshing = false }
    )

    private val numberSettingLoadingIndicator = LoadingIndicatorManager(
            showLoading = { progress.show() },
            hideLoading = { progress.hide() }
    )

    private val accountDetailsRepository: AccountDetailsRepository
        get() = repositoryProvider.accountDetails()

    private var canSet: Boolean = false
        set(value) {
            field = value
            action_button.enabled = value
        }

    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_phone_number_settings)

        initToolbar()
        initSwipeRefresh()
        initFields()
        initButtons()

        canSet = false

        update()
    }

    private fun initToolbar() {
        setSupportActionBar(toolbar)
        setTitle(R.string.phone_number_settings_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun initSwipeRefresh() {
        swipe_refresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.accent))
        swipe_refresh.setOnRefreshListener { update(force = true) }
    }

    private fun initFields() {
        phone_number_edit_text.setPaddings(sp(16), 0, 0, 0)
        phone_number_edit_text.addTextChangedListener(object : SimpleTextWatcher() {
            override fun afterTextChanged(s: Editable?) {
                phone_number_edit_text.error = null
                updateActionAvailability()
            }
        })
        phone_number_edit_text.onEditorAction(this::tryToSetNumber)
        phone_number_edit_text.isEnabled = false
    }

    private fun initButtons() {
        action_button.setOnClickListener {
            tryToSetNumber()
        }
    }

    private fun update(force: Boolean = false) {
        val accountId = walletInfoProvider.getWalletInfo()?.accountId
                ?: return

        if (force) {
            accountDetailsRepository.invalidateCachedIdentity(accountId)
        }

        accountDetailsRepository
                .getPhoneByAccountId(accountId)
                .compose(ObservableTransformers.defaultSchedulersMaybe())
                .doOnSubscribe {
                    currentNumberLoadingIndicator.show()
                    updateActionAvailability()
                }
                .doOnEvent { _, _ ->
                    currentNumberLoadingIndicator.hide()
                    updateActionAvailability()
                    phone_number_edit_text.isEnabled = true
                }
                .subscribeBy(
                        onComplete = this::onNoCurrentNumber,
                        onSuccess = this::onCurrentNumberLoaded,
                        onError = this::onCurrentNumberLoadingError
                )
                .addTo(compositeDisposable)
    }

    private fun onNoCurrentNumber() {
        phone_number_edit_text.requestFocus()
        SoftInputUtil.showSoftInputOnView(phone_number_edit_text)
        showPhoneAccountPicker()
    }

    private fun onCurrentNumberLoaded(number: String) {
        val toSet = number.trimStart('+')
        phone_number_edit_text.setText(toSet)
        phone_number_edit_text.setSelection(toSet.length)
    }

    private fun onCurrentNumberLoadingError(error: Throwable) {
        if (error !is AccountDetailsRepository.NoIdentityAvailableException) {
            errorHandlerFactory.getDefault().handle(error)
        }
    }

    private fun showPhoneAccountPicker() {
        val hintRequest = HintRequest.Builder()
                .setPhoneNumberIdentifierSupported(true)
                .build()

        val apiClient = GoogleApiClient.Builder(this)
                .addApi(Auth.CREDENTIALS_API)
                .build()

        val intent = Auth.CredentialsApi.getHintPickerIntent(
                apiClient, hintRequest)

        startIntentSenderForResult(intent.intentSender, PHONE_ACCOUNT_REQUEST,
                null, 0, 0, 0)
    }

    private fun updateActionAvailability() {
        canSet = !phone_number_edit_text.text.isNullOrBlank()
                && !phone_number_edit_text.hasError()
                && !numberSettingLoadingIndicator.isLoading
                && !currentNumberLoadingIndicator.isLoading
    }

    private fun readNumber(): String {
        return "+" + (phone_number_edit_text.text?.toString() ?: "")
    }

    private fun checkNumber() {
        val number = readNumber()
        if (!GlobalPhoneNumberValidator.isValid(number)) {
            phone_number_edit_text.setErrorAndFocus(R.string.error_invalid_phone_number)
        } else {
            phone_number_edit_text.error = null
        }
    }

    private fun tryToSetNumber() {
        checkNumber()
        updateActionAvailability()

        if (canSet) {
            setNumber()
        }
    }

    private fun setNumber() {
        val number = readNumber()

        SetPhoneNumberUseCase(
                number,
                walletInfoProvider,
                apiProvider,
                accountDetailsRepository
        )
                .perform()
                .compose(ObservableTransformers.defaultSchedulersCompletable())
                .doOnSubscribe {
                    numberSettingLoadingIndicator.show()
                    updateActionAvailability()
                }
                .doOnTerminate {
                    numberSettingLoadingIndicator.hide()
                    updateActionAvailability()
                }
                .subscribeBy(
                        onError = numberSettingErrorHandler::handleIfPossible,
                        onComplete = this::onNumberSet
                )
                .addTo(compositeDisposable)
    }

    private fun onNumberSet() {
        toastManager.short(R.string.phone_number_set_successfully)
        finish()
    }

    private val numberSettingErrorHandler: ErrorHandler
        get() = CompositeErrorHandler(
                EditTextErrorHandler(phone_number_edit_text) { error ->
                    when (error) {
                        is SetPhoneNumberUseCase.PhoneNumberAlreadyTakenException ->
                            getString(R.string.error_phone_number_already_taken)
                        else ->
                            null
                    }
                },
                errorHandlerFactory.getDefault()
        )
                .doOnSuccessfulHandle { updateActionAvailability() }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PHONE_ACCOUNT_REQUEST && resultCode == Activity.RESULT_OK) {
            data?.getParcelableExtra<Credential>(Credential.EXTRA_KEY)
                    ?.also(this::onPhoneAccountPicked)
        }
    }

    private fun onPhoneAccountPicked(credential: Credential) {
        onCurrentNumberLoaded(credential.id)
    }

    companion object {
        private val PHONE_ACCOUNT_REQUEST = "phone_account".hashCode() and 0xffff
    }
}
