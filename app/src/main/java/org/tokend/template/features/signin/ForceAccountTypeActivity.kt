package org.tokend.template.features.signin

import android.os.Bundle
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.features.signin.logic.PostSignInManager
import org.tokend.template.features.signin.model.ForcedAccountType
import org.tokend.template.util.Navigator
import org.tokend.template.util.ObservableTransformers

class ForceAccountTypeActivity : BaseActivity() {
    private lateinit var forcedAccountType: ForcedAccountType

    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_loading_data)

        val forcedAccountType = intent.getStringExtra(FORCED_ACCOUNT_TYPE_EXTRA)
                ?.let { ForcedAccountType.valueOf(it) }
        if (forcedAccountType == null) {
            errorHandlerFactory.getDefault().handle(IllegalArgumentException(
                    "No $FORCED_ACCOUNT_TYPE_EXTRA specified"
            ))
            finish()
            return
        }
        this.forcedAccountType = forcedAccountType

        performLoading()
    }

    private fun performLoading() {
        PostSignInManager(repositoryProvider, forcedAccountType)
                .doPostSignIn()
                .compose(ObservableTransformers.defaultSchedulersCompletable())
                .subscribeBy(
                        onError = this::onLoadingError,
                        onComplete = this::onLoadingCompleted
                )
                .addTo(compositeDisposable)
    }

    private fun onLoadingError(error: Throwable) {
        errorHandlerFactory.getDefault().handle(error)
        finish()
    }

    private fun onLoadingCompleted() {
        if (forcedAccountType == ForcedAccountType.CORPORATE) {
            Navigator.from(this).toCorporateMainActivity()
        } else {
            Navigator.from(this).toMainActivity()
        }
    }

    companion object {
        private const val FORCED_ACCOUNT_TYPE_EXTRA = "forced_account_type"

        fun getBundle(forcedAccountType: ForcedAccountType) = Bundle().apply {
            putString(FORCED_ACCOUNT_TYPE_EXTRA, forcedAccountType.name)
        }
    }
}
