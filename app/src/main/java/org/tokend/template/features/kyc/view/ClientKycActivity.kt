package org.tokend.template.features.kyc.view

import android.os.Bundle
import android.text.Editable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.activity_client_kyc.*
import kotlinx.android.synthetic.main.include_appbar_elevation.*
import kotlinx.android.synthetic.main.layout_progress.*
import kotlinx.android.synthetic.main.toolbar.*
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.extensions.hasError
import org.tokend.template.extensions.onEditorAction
import org.tokend.template.features.kyc.logic.SubmitKycRequestUseCase
import org.tokend.template.features.kyc.model.KycForm
import org.tokend.template.logic.TxManager
import org.tokend.template.util.Navigator
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.view.util.ElevationUtil
import org.tokend.template.view.util.LoadingIndicatorManager
import org.tokend.template.view.util.input.SimpleTextWatcher
import org.tokend.template.view.util.input.SoftInputUtil

class ClientKycActivity : BaseActivity() {
    private var canContinue: Boolean = false
        set(value) {
            field = value
            continue_button.isEnabled = value
        }

    private val loadingIndicator = LoadingIndicatorManager(
            showLoading = { progress.show() },
            hideLoading = { progress.hide() }
    )
    private var isLoading: Boolean = false
        set(value) {
            field = value
            loadingIndicator.setLoading(value)
            updateContinueAvailability()
        }

    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_client_kyc)

        initToolbar()
        initFields()
        initButtons()

        canContinue = false
    }

    // region Init
    private fun initToolbar() {
        setSupportActionBar(toolbar)
        setTitle(R.string.complete_account_setup)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        ElevationUtil.initScrollElevation(scroll_view, appbar_elevation_view)
    }

    private fun initFields() {
        first_name_edit_text.addTextChangedListener(object : SimpleTextWatcher() {
            override fun afterTextChanged(s: Editable?) {
                first_name_edit_text.error = null
                updateContinueAvailability()
            }
        })
        first_name_edit_text.requestFocus()

        last_name_edit_text.addTextChangedListener(object : SimpleTextWatcher() {
            override fun afterTextChanged(s: Editable?) {
                last_name_edit_text.error = null
                updateContinueAvailability()
            }
        })
        last_name_edit_text.onEditorAction {
            tryToContinue()
        }
    }

    private fun initButtons() {
        continue_button.setOnClickListener {
            tryToContinue()
        }
    }
    // endregion

    private fun updateContinueAvailability() {
        canContinue = !first_name_edit_text.text.isNullOrBlank()
                && !first_name_edit_text.hasError()
                && !last_name_edit_text.text.isNullOrBlank()
                && !last_name_edit_text.hasError()
                && !isLoading
    }

    private fun tryToContinue() {
        if (canContinue) {
            submitForm()
        }
    }

    private fun submitForm() {
        val form = KycForm.General(
                firstName = first_name_edit_text.text.toString().trim(),
                lastName = last_name_edit_text.text.toString().trim()
        )

        SoftInputUtil.hideSoftInput(this)

        SubmitKycRequestUseCase(
                form,
                apiProvider,
                walletInfoProvider,
                accountProvider,
                repositoryProvider,
                TxManager(apiProvider)
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
                        onComplete = this::onFormSubmitted,
                        onError = { errorHandlerFactory.getDefault().handle(it)}
                )
                .addTo(compositeDisposable)
    }

    private fun onFormSubmitted() {
        Navigator.from(this).toClientMainActivity()
    }
}
