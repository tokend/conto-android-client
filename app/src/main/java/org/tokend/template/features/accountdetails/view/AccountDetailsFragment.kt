package org.tokend.template.features.accountdetails.view

import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import kotlinx.android.synthetic.main.activity_share_qr.*
import org.tokend.template.App
import org.tokend.template.R
import org.tokend.template.extensions.withArguments
import org.tokend.template.features.qr.ShareQrFragment
import org.tokend.template.util.Navigator
import org.tokend.template.view.dialog.SignOutDialogFactory

class AccountDetailsFragment : ShareQrFragment() {
    private val useAccountId: Boolean
        get() = arguments?.getBoolean(USE_ACCOUNT_ID_EXTRA) ?: false

    override val data: String
        get() = walletInfoProvider.getWalletInfo()
                ?.run { if (useAccountId) accountId else email }
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
        layoutInflater.inflate(R.layout.preference_layout,
                scrollable_root_layout, false).apply {
            findViewById<ImageView>(android.R.id.icon).setImageDrawable(
                    ContextCompat.getDrawable(requireContext(), R.drawable.ic_settings))
            findViewById<TextView>(android.R.id.title).setText(R.string.settings_title)
            findViewById<TextView>(android.R.id.summary).visibility = View.GONE
            setPadding(0, 0, 0, 0)

            setOnClickListener {
                Navigator.from(this@AccountDetailsFragment).openSettings()
            }

            scrollable_root_layout.addView(this)
        }

        layoutInflater.inflate(R.layout.preference_layout,
                scrollable_root_layout, false).apply {
            findViewById<ImageView>(android.R.id.icon).setImageDrawable(
                    ContextCompat.getDrawable(requireContext(), R.drawable.ic_sign_out))
            findViewById<TextView>(android.R.id.title).setText(R.string.sign_out)
            findViewById<TextView>(android.R.id.summary).visibility = View.GONE
            setPadding(0, 0, 0, 0)

            setOnClickListener {
                SignOutDialogFactory.getDialog(requireContext()) {
                    (activity?.application as? App)?.signOut(activity)
                }.show()
            }

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