package org.tokend.template.features.invites.view

import android.os.Bundle
import android.text.Editable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.activity_invite_new_users.*
import kotlinx.android.synthetic.main.layout_progress.*
import kotlinx.android.synthetic.main.toolbar.*
import org.jetbrains.anko.onClick
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.extensions.hasError
import org.tokend.template.features.invites.logic.InviteNewUsersUseCase
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.util.validator.EmailValidator
import org.tokend.template.view.util.LoadingIndicatorManager
import org.tokend.template.view.util.input.SimpleTextWatcher

class InviteNewUsersActivity : BaseActivity() {

    private val loadingIndicator = LoadingIndicatorManager(
            showLoading = { progress.show() },
            hideLoading = { progress.hide() }
    )

    private var invitees: List<String>? = null

    private var canInvite = false
        set(value) {
            field = value
            invite_button.isEnabled = value
        }

    private var isLoading: Boolean = false
        set(value) {
            field = value
            loadingIndicator.setLoading(value, "main")
            updateInviteAvailability()
        }

    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_invite_new_users)

        initViews()
        updateInviteAvailability()
    }

    private fun initViews() {
        initToolbar()
        initInputField()
        initInviteButton()
    }

    private fun initToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.invite_new_users_title)
    }

    private fun initInputField() {
        emails_edit_text.addTextChangedListener(object : SimpleTextWatcher() {
            override fun afterTextChanged(s: Editable?) {
                emails_edit_text.error = null
                updateInviteAvailability()
            }
        })
    }

    private fun initInviteButton() {
        invite_button.onClick {
            tryToInvite()
        }
    }

    private fun updateInviteAvailability() {
        canInvite = !isLoading
                && !emails_edit_text.hasError()
                && !emails_edit_text.text.isNullOrBlank()
    }

    private fun tryToInvite() {
        invitees = emails_edit_text.text
                ?.split(',')
                ?.filter { it.isNotBlank() }
                ?.map { it.trim() }

        checkEmails()

        if (!canInvite) {
            return
        }

        invite()
    }

    private fun checkEmails() {
        invitees?.find {
            !EmailValidator.isValid(it)
        }?.let {
            emails_edit_text.error = getString(R.string.error_missed_comma_or_invalid_email)
            updateInviteAvailability()
        }
    }

    private fun invite() {
        invitees?.let {
            InviteNewUsersUseCase(
                    it,
                    walletInfoProvider,
                    apiProvider,
                    repositoryProvider
            )
                    .perform()
                    .compose(ObservableTransformers.defaultSchedulersCompletable())
                    .doOnSubscribe {
                        isLoading = true
                    }
                    .doOnTerminate {
                        isLoading = false
                    }
                    .subscribeBy(
                            onComplete = this::onInviteComplete,
                            onError = { error ->
                                errorHandlerFactory.getDefault().handle(error)
                            }
                    )
                    .addTo(compositeDisposable)
        }
    }

    private fun onInviteComplete() {
        toastManager.short(getString(R.string.message_invite_success))
    }

}
