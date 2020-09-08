package org.tokend.template.features.signin.unlock.view

import android.app.Application
import android.os.Bundle
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.reactivex.Completable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.subjects.CompletableSubject
import kotlinx.android.synthetic.main.fragment_unlock_app.*
import kotlinx.android.synthetic.main.include_error_empty_view.*
import kotlinx.android.synthetic.main.layout_error_empty_view.view.*
import org.jetbrains.anko.onClick
import org.tokend.crypto.ecdsa.erase
import org.tokend.sdk.api.wallets.model.InvalidCredentialsException
import org.tokend.template.App
import org.tokend.template.R
import org.tokend.template.extensions.*
import org.tokend.template.features.signin.logic.PostSignInManager
import org.tokend.template.features.signin.logic.SignInUseCase
import org.tokend.template.features.signin.unlock.model.UnlockMethod
import org.tokend.template.fragments.BaseFragment
import org.tokend.template.logic.fingerprint.FingerprintAuthManager
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.util.ProfileUtil
import org.tokend.template.util.navigation.Navigator
import org.tokend.template.view.FingerprintIndicatorManager
import org.tokend.template.view.dialog.SignOutDialogFactory
import org.tokend.template.view.util.LoadingIndicatorManager
import org.tokend.template.view.util.input.SimpleTextWatcher
import org.tokend.template.view.util.input.SoftInputUtil

open class UnlockAppFragment : BaseFragment() {
    private val resultSubject = CompletableSubject.create()
    val resultCompletable: Completable = resultSubject

    private lateinit var fingerprintAuthManager: FingerprintAuthManager
    private lateinit var fingerprintIndicatorManager: FingerprintIndicatorManager

    private val loadingIndicator = LoadingIndicatorManager(
            showLoading = { showLoading() },
            hideLoading = { /* ;-) */ }
    )

    private var isLoading: Boolean
        get() = loadingIndicator.isLoading
        set(value) {
            loadingIndicator.setLoading(value)
        }

    private var unlockMethod: UnlockMethod = UnlockMethod.PASSWORD
        set(value) {
            field = value
            updateUnlockView()
        }

    private var canUnlockWithPassword = false
        set(value) {
            field = value
            unlock_button.isEnabled = value
        }

    private lateinit var email: String
    private var lastEnteredPassword: CharArray? = null

    private val application: Application
        get() = requireActivity().application

    private val allowSignOut: Boolean by lazy {
        arguments?.getBoolean(ALLOW_SIGN_OUT_EXTRA, true) ?: true
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_unlock_app, container, false)
    }

    override fun onInitAllowed() {
        val email = credentialsPersistence.getSavedEmail()
        if (email == null) {
            errorHandlerFactory.getDefault().handle(
                    IllegalStateException("No saved email, unlock is not possible")
            )
            (application as? App)?.signOut(requireActivity())
            return
        }
        this.email = email

        fingerprintAuthManager = FingerprintAuthManager(requireContext(), credentialsPersistence)
        fingerprintIndicatorManager = FingerprintIndicatorManager(requireContext(), fingerprint_indicator)

        initViews()
        initUnlockMethod()
    }

    private fun initViews() {
        initButtons()
        initErrorEmptyView()
        user_email_text.text = email
        ProfileUtil.setAvatar(user_logo, email, urlConfigProvider, activeKycPersistence.loadItem())
    }

    protected open fun initButtons() {
        use_password_text_button.onClick {
            password_edit_text?.text?.clear()
            cancelFingerprintAuth()
            unlockMethod = UnlockMethod.PASSWORD
        }

        password_edit_text.addTextChangedListener(object : SimpleTextWatcher() {
            override fun afterTextChanged(p0: Editable?) {
                password_edit_text.error = null
                updatePasswordUnlockAvailability()
            }
        })

        password_edit_text.onEditorAction {
            tryToUnlockWithPassword()
        }

        unlock_button.onClick {
            tryToUnlockWithPassword()
        }

        if (allowSignOut) {
            sign_out_button.onClick {
                if (!loadingIndicator.isLoading) {
                    SignOutDialogFactory.getDialog(requireContext()) {
                        (application as App).signOut(requireActivity())
                    }.show()
                }
            }
        } else {
            sign_out_button.visibility = View.GONE
        }

        recovery_button.onClick {
            Navigator.from(this)
                    .openRecovery(email)
                    .addTo(activityRequestsBag)
                    .doOnSuccess { requireActivity().finish() }
        }
    }

    private fun initErrorEmptyView() {
        error_empty_view.setErrorDrawable(null)
        error_empty_view.layoutParams = error_empty_view.layoutParams.apply {
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        error_empty_view.error_action_button.layoutParams =
                error_empty_view.error_action_button.layoutParams.let {
                    (it as? ViewGroup.MarginLayoutParams)?.apply {
                        topMargin = resources.getDimensionPixelSize(R.dimen.quadra_margin)
                    }
                }
    }

    private fun initUnlockMethod() {
        unlockMethod =
                if (!backgroundLockManager.isBackgroundLockEnabled)
                    UnlockMethod.AUTO
                else if (fingerprintAuthManager.isAuthAvailable)
                    UnlockMethod.FINGERPRINT
                else
                    UnlockMethod.PASSWORD
    }

    private fun updateUnlockView() {
        when (unlockMethod) {
            UnlockMethod.PASSWORD -> showPasswordUnlock()
            UnlockMethod.FINGERPRINT -> showFingerprintUnlock()
            UnlockMethod.AUTO -> showAutoUnlock()
        }
    }

    private fun showPasswordUnlock(clear: Boolean = true) {
        if (clear) {
            password_edit_text.text?.clear()
        }
        password_edit_text.error = null
        password_edit_text.requestFocus()
        SoftInputUtil.showSoftInputOnView(password_edit_text)

        progress.visibility = View.GONE
        password_layout.visibility = View.VISIBLE
        fingerprint_layout.visibility = View.INVISIBLE
        error_empty_view.hide()
        if (allowSignOut) {
            sign_out_button.visibility = View.VISIBLE
        }
    }

    private fun showFingerprintUnlock() {
        requestFingerprintAuthIfAvailable()

        progress.visibility = View.GONE
        password_layout.visibility = View.INVISIBLE
        fingerprint_layout.visibility = View.VISIBLE
        error_empty_view.hide()
        if (allowSignOut) {
            sign_out_button.visibility = View.VISIBLE
        }
    }

    private fun showAutoUnlock() {
        performAutoUnlock()
    }

    private fun showLoading() {
        SoftInputUtil.hideSoftInput(requireActivity())
        progress.visibility = View.VISIBLE
        password_layout.visibility = View.INVISIBLE
        fingerprint_layout.visibility = View.INVISIBLE
        error_empty_view.hide()
        if (allowSignOut) {
            sign_out_button.visibility = View.GONE
        }
    }

    private fun showError(error: Throwable) {
        progress.visibility = View.GONE
        password_layout.visibility = View.INVISIBLE
        fingerprint_layout.visibility = View.INVISIBLE
        error_empty_view.showError(error, errorHandlerFactory.getDefault()) {
            lastEnteredPassword?.also {
                unlock(email, it)
            }
        }
        if (allowSignOut) {
            sign_out_button.visibility = View.VISIBLE
        }
    }

    private fun updatePasswordUnlockAvailability() {
        canUnlockWithPassword = !password_edit_text.text.isNullOrEmpty()
                && !password_edit_text.hasError()
    }

    private fun requestFingerprintAuthIfAvailable() {
        fingerprintAuthManager.requestAuthIfAvailable(
                onAuthStart = {},
                onSuccess = { email, password ->
                    unlock(email, password)
                },
                onError = {
                    toastManager.short(it)
                    fingerprintIndicatorManager.error()
                }
        )
    }

    private fun cancelFingerprintAuth() {
        fingerprintAuthManager.cancelAuth()
    }

    private fun tryToUnlockWithPassword() {
        if (password_edit_text.text.isNullOrEmpty()) {
            password_edit_text.setErrorAndFocus(R.string.error_cannot_be_empty)
        }

        updatePasswordUnlockAvailability()

        if (canUnlockWithPassword) {
            unlock(email, password_edit_text.text.getChars())
        }
    }

    private fun performAutoUnlock() {
        val savedPassword = credentialsPersistence.getSavedPassword()
        if (savedPassword == null) {
            unlockMethod = UnlockMethod.PASSWORD
            return
        }
        unlock(email, savedPassword)
    }

    private fun unlock(email: String, password: CharArray) {
        if (lastEnteredPassword !== password) {
            lastEnteredPassword?.erase()
        }
        lastEnteredPassword = password

        SoftInputUtil.hideSoftInput(requireActivity())

        SignInUseCase(
                email,
                password,
                apiProvider.getKeyServer(),
                session,
                credentialsPersistence,
                walletInfoPersistence,
                getPostSignInManager()?.let { it::doPostSignIn }
        )
                .perform()
                .compose(ObservableTransformers.defaultSchedulersCompletable())
                .doOnSubscribe {
                    isLoading = true
                    SoftInputUtil.hideSoftInput(requireActivity())
                }
                .subscribeBy(
                        onComplete = this::onUnlockComplete,
                        onError = {
                            isLoading = false
                            onUnlockError(it)
                        }
                )
                .addTo(compositeDisposable)
    }

    protected open fun getPostSignInManager(): PostSignInManager? {
        return postSignInManagerFactory.get()
    }

    private fun onUnlockComplete() {
        resultSubject.onComplete()
    }

    private fun onUnlockError(error: Throwable) {
        when {
            error is InvalidCredentialsException -> {
                if (unlockMethod == UnlockMethod.PASSWORD) {
                    showPasswordUnlock(clear = false)
                    password_edit_text.setErrorAndFocus(R.string.error_invalid_password)
                } else {
                    unlockMethod = UnlockMethod.PASSWORD
                    toastManager.long(R.string.error_password_changed)
                }
                updatePasswordUnlockAvailability()
            }

            unlockMethod == UnlockMethod.PASSWORD -> {
                errorHandlerFactory.getDefault().handle(error)
                showPasswordUnlock(clear = false)
            }

            else -> {
                showError(error)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (unlockMethod == UnlockMethod.FINGERPRINT && !isLoading) {
            requestFingerprintAuthIfAvailable()
        }
    }

    override fun onPause() {
        super.onPause()
        cancelFingerprintAuth()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        lastEnteredPassword?.erase()
    }

    override fun onBackPressed(): Boolean {
        return if (!isLoading && unlockMethod == UnlockMethod.PASSWORD
                && fingerprintAuthManager.isAuthAvailable) {
            unlockMethod = UnlockMethod.FINGERPRINT
            false
        } else {
            true
        }
    }

    companion object {
        private const val ALLOW_SIGN_OUT_EXTRA = "allow_sign_out"

        fun getBundle(allowSignOut: Boolean = true) = Bundle().apply {
            putBoolean(ALLOW_SIGN_OUT_EXTRA, allowSignOut)
        }

        fun newInstance(bundle: Bundle): UnlockAppFragment =
                UnlockAppFragment().withArguments(bundle)
    }
}