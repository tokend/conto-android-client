package org.tokend.template.features.settings.telegram.view

import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.text.Editable
import android.text.InputFilter
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.activity_telegram_username_setting.*
import kotlinx.android.synthetic.main.layout_progress.*
import kotlinx.android.synthetic.main.toolbar.*
import org.jetbrains.anko.enabled
import org.jetbrains.anko.sp
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.data.repository.AccountDetailsRepository
import org.tokend.template.extensions.hasError
import org.tokend.template.extensions.onEditorAction
import org.tokend.template.features.settings.telegram.logic.SetTelegramUsernameUseCase
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.util.errorhandler.CompositeErrorHandler
import org.tokend.template.util.errorhandler.ErrorHandler
import org.tokend.template.view.util.LoadingIndicatorManager
import org.tokend.template.view.util.input.EditTextErrorHandler
import org.tokend.template.view.util.input.SimpleTextWatcher
import org.tokend.template.view.util.input.SoftInputUtil


class TelegramUsernameSettingsActivity : BaseActivity() {
    private val currentUsernameLoadingIndicator = LoadingIndicatorManager(
            showLoading = { swipe_refresh.isRefreshing = true },
            hideLoading = { swipe_refresh.isRefreshing = false }
    )

    private val settingLoadingIndicator = LoadingIndicatorManager(
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
        setContentView(R.layout.activity_telegram_username_setting)

        initToolbar()
        initSwipeRefresh()
        initFields()
        initButtons()

        canSet = false

        update()
    }

    private fun initToolbar() {
        setSupportActionBar(toolbar)
        setTitle(R.string.telegram_username_settings_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun initSwipeRefresh() {
        swipe_refresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.accent))
        swipe_refresh.setOnRefreshListener { update(force = true) }
    }

    private fun initFields() {
        username_edit_text.setPaddings(sp(16), 0, 0, 0)
        username_edit_text.filters = arrayOf(
                InputFilter { source, _, _, _, _, _->
                    if (source.contains('@'))
                        source.toString().replace("@", "")
                    else
                        null
                }
        )
        username_edit_text.addTextChangedListener(object : SimpleTextWatcher() {
            override fun afterTextChanged(s: Editable?) {
                username_edit_text.error = null
                updateActionAvailability()
            }
        })
        username_edit_text.onEditorAction(this::tryToSetUsername)
        username_edit_text.isEnabled = false
    }

    private fun initButtons() {
        action_button.setOnClickListener {
            tryToSetUsername()
        }
    }

    private fun update(force: Boolean = false) {
        val accountId = walletInfoProvider.getWalletInfo()?.accountId
                ?: return

        if (force) {
            accountDetailsRepository.invalidateCachedIdentity(accountId)
        }

        accountDetailsRepository
                .getTelegramUsernameByAccountId(accountId)
                .compose(ObservableTransformers.defaultSchedulersMaybe())
                .doOnSubscribe {
                    currentUsernameLoadingIndicator.show()
                    updateActionAvailability()
                }
                .doOnEvent { _, _ ->
                    currentUsernameLoadingIndicator.hide()
                    updateActionAvailability()
                    username_edit_text.isEnabled = true
                }
                .subscribeBy(
                        onComplete = this::onNoCurrentUsername,
                        onSuccess = this::onCurrentUsernameLoaded,
                        onError = this::onCurrentUsernameLoadingError
                )
                .addTo(compositeDisposable)
    }

    private fun onNoCurrentUsername() {
        username_edit_text.requestFocus()
        SoftInputUtil.showSoftInputOnView(username_edit_text)
    }

    private fun onCurrentUsernameLoaded(username: String) {
        username_edit_text.setText(username)
        username_edit_text.setSelection(username.length)
    }

    private fun onCurrentUsernameLoadingError(error: Throwable) {
        if (error !is AccountDetailsRepository.NoIdentityAvailableException) {
            errorHandlerFactory.getDefault().handle(error)
        }
    }

    private fun updateActionAvailability() {
        canSet = !username_edit_text.text.isNullOrBlank()
                && !username_edit_text.hasError()
                && !settingLoadingIndicator.isLoading
                && !currentUsernameLoadingIndicator.isLoading
    }

    private fun tryToSetUsername() {
        if (canSet) {
            setNumber()
        }
    }

    private fun setNumber() {
        val username = username_edit_text.text?.toString()?.trim() ?: ""

        SetTelegramUsernameUseCase(
                username,
                walletInfoProvider,
                apiProvider,
                accountDetailsRepository
        )
                .perform()
                .compose(ObservableTransformers.defaultSchedulersCompletable())
                .doOnSubscribe {
                    settingLoadingIndicator.show()
                    updateActionAvailability()
                }
                .doOnTerminate {
                    settingLoadingIndicator.hide()
                    updateActionAvailability()
                }
                .subscribeBy(
                        onError = usernameSettingErrorHandler::handleIfPossible,
                        onComplete = this::onUsernameSet
                )
                .addTo(compositeDisposable)
    }

    private fun onUsernameSet() {
        toastManager.short(R.string.telegram_connected_successfully)
        finish()
    }

    private val usernameSettingErrorHandler: ErrorHandler
        get() = CompositeErrorHandler(
                EditTextErrorHandler(username_edit_text) { error ->
                    when (error) {
                        is SetTelegramUsernameUseCase.UsernameAlreadyTakenException ->
                            getString(R.string.error_telegram_username_already_taken)
                        else ->
                            null
                    }
                },
                errorHandlerFactory.getDefault()
        )
                .doOnSuccessfulHandle { updateActionAvailability() }
}
