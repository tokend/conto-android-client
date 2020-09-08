package org.tokend.template.features.accountdetails.view

import android.os.Bundle
import androidx.core.content.ContextCompat
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import kotlinx.android.synthetic.main.activity_share_qr.*
import org.tokend.template.App
import org.tokend.template.R
import org.tokend.template.extensions.withArguments
import org.tokend.template.features.qr.ShareQrFragment
import org.tokend.template.util.navigation.Navigator
import org.tokend.template.view.dialog.SignOutDialogFactory

class AccountDetailsFragment : ShareQrFragment() {
    private val useAccountId: Boolean
        get() = arguments?.getBoolean(USE_ACCOUNT_ID_EXTRA) ?: false

    override val data: String
        get() = walletInfoProvider.getWalletInfo()
                ?.run { if (useAccountId || session.isLocalAccountUsed) accountId else email }
                ?: throw IllegalStateException("No wallet info found")

    override val title: String
        get() = getString(R.string.account_title)

    override val shareDialogText: String
        get() = getString(R.string.share_account)

    override fun onInitAllowed() {
        super.onInitAllowed()

        initExtraActions()
    }

    private fun initExtraActions() {
        addPreferenceItem(R.drawable.ic_settings, R.string.settings_title) {
            Navigator.from(this@AccountDetailsFragment).openSettings()
        }

        addPreferenceItem(R.drawable.ic_sign_out, R.string.sign_out) {
            SignOutDialogFactory.getDialog(requireContext()) {
                (activity?.application as? App)?.signOut(activity)
            }.show()
        }
    }

    private fun addPreferenceItem(@DrawableRes iconRes: Int,
                                  @StringRes titleRes: Int,
                                  action: () -> Unit) {
        layoutInflater.inflate(R.layout.preference_layout,
                scrollable_root_layout, false).apply {
            findViewById<ImageView>(android.R.id.icon).setImageDrawable(
                    ContextCompat.getDrawable(requireContext(), iconRes))
            findViewById<TextView>(android.R.id.title).setText(titleRes)
            findViewById<TextView>(android.R.id.summary).visibility = View.GONE
            setPadding(0, 0, 0, 0)

            setOnClickListener { action() }

            scrollable_root_layout.addView(this)
        }
    }

    companion object {
        val ID = "account_details".hashCode().toLong()
        private const val USE_ACCOUNT_ID_EXTRA = "use_account_id"

        fun getBundle(useAccountId: Boolean) = Bundle().apply {
            putBoolean(USE_ACCOUNT_ID_EXTRA, useAccountId)
        }

        fun newInstance(bundle: Bundle): AccountDetailsFragment =
                AccountDetailsFragment().withArguments(bundle)
    }
}