package org.tokend.template.features.signin.unlock.view

import android.os.Bundle
import io.reactivex.rxkotlin.subscribeBy
import org.tokend.template.App
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.activities.OnBackPressedListener
import org.tokend.template.features.recovery.view.KycRecoveryStatusDialogFactory
import org.tokend.template.util.Navigator
import org.tokend.template.util.ObservableTransformers

class UnlockAppActivity : BaseActivity() {
    override val allowUnauthorized: Boolean = true

    private var onBackPressedListener: OnBackPressedListener? = null

    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_single_fragment)

        val fragment = UnlockAppFragment.newInstance()
        onBackPressedListener = fragment

        fragment
                .resultCompletable
                .compose(ObservableTransformers.defaultSchedulersCompletable())
                .subscribeBy(
                        onComplete = this::onUnlockComplete,
                        onError = {
                            errorHandlerFactory.getDefault().handle(it)
                            finish()
                        }
                )

        supportFragmentManager
                .beginTransaction()
                .add(R.id.wallet_fragment_container, fragment)
                .disallowAddToBackStack()
                .commit()
    }

    private fun onUnlockComplete() {
        // KYC recovery check.
        val account = repositoryProvider.account().item
        if (account != null && account.isKycRecoveryActive) {
            KycRecoveryStatusDialogFactory(this, R.style.AlertDialogStyle)
                    .getStatusDialog(account, urlConfigProvider.getConfig().client) {
                        setOnDismissListener {
                            (application as? App)?.signOut(this@UnlockAppActivity)
                        }
                    }
                    .show()
        } else {
            Navigator.from(this)
                    .performPostSignInRouting(repositoryProvider.kycState().item)
        }
    }

    override fun onBackPressed() {
        if (onBackPressedListener?.onBackPressed() != false) {
            super.onBackPressed()
        }
    }
}
