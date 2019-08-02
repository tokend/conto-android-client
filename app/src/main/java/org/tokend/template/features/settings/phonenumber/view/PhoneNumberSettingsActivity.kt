package org.tokend.template.features.settings.phonenumber.view

import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.text.Editable
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
import org.tokend.template.util.validator.GlobalPhoneNumberValidator
import org.tokend.template.view.util.LoadingIndicatorManager
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
                        onComplete = {
                            phone_number_edit_text.requestFocus()
                            SoftInputUtil.showSoftInputOnView(phone_number_edit_text)
                        },
                        onSuccess = this::onCurrentNumberLoaded,
                        onError = this::onCurrentNumberLoadingError
                )
                .addTo(compositeDisposable)
    }

    private fun onCurrentNumberLoaded(number: String) {
        val toSet =  number.trimStart('+')
        phone_number_edit_text.setText(toSet)
        phone_number_edit_text.setSelection(toSet.length)
    }

    private fun onCurrentNumberLoadingError(error: Throwable) {
        if (error !is AccountDetailsRepository.NoIdentityAvailableException) {
            errorHandlerFactory.getDefault().handle(error)
        }
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
                        onError = this::onNumberSettingError,
                        onComplete = { toastManager.short(R.string.phone_number_set_successfully) }
                )
                .addTo(compositeDisposable)
    }

    private fun onNumberSettingError(error: Throwable) {
        if (error is SetPhoneNumberUseCase.PhoneNumberAlreadyTakenException) {
            phone_number_edit_text.setErrorAndFocus(R.string.error_phone_number_already_taken)
        } else {
            errorHandlerFactory.getDefault().handle(error)
        }
    }
}
