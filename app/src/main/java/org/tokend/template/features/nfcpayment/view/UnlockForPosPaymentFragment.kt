package org.tokend.template.features.nfcpayment.view

import android.view.View
import kotlinx.android.synthetic.main.fragment_unlock_app.*
import org.tokend.template.extensions.withArguments
import org.tokend.template.features.signin.logic.PostSignInManager
import org.tokend.template.features.signin.unlock.view.UnlockAppFragment

class UnlockForPosPaymentFragment : UnlockAppFragment() {
    override fun initButtons() {
        super.initButtons()
        sign_out_button.visibility = View.GONE
    }

    override fun getPostSignInManager(): PostSignInManager? {
        return null
    }

    companion object {
        fun newInstance(): UnlockForPosPaymentFragment =
                UnlockForPosPaymentFragment().withArguments(getBundle(allowSignOut = false))
    }
}