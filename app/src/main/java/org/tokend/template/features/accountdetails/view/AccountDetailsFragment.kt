package org.tokend.template.features.accountdetails.view

import android.os.Bundle
import android.support.constraint.ConstraintLayout
import android.support.design.widget.BottomSheetBehavior
import android.support.design.widget.CoordinatorLayout
import android.support.v4.content.ContextCompat
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Space
import android.widget.TextView
import io.reactivex.rxkotlin.addTo
import kotlinx.android.synthetic.main.activity_share_qr.*
import kotlinx.android.synthetic.main.include_rounded_elevated_bottom_sheet_header.*
import kotlinx.android.synthetic.main.layout_balance_changes_bottom_sheet.*
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

    private lateinit var bottomSheet: BottomSheetBehaviorWithNestedScrollChild<*>

    override fun onInitAllowed() {
        super.onInitAllowed()

        initExtraActions()
        initBottomSheet()
    }

    private fun initBottomSheet() {
        val coordinatorLayout = CoordinatorLayout(requireContext())
        coordinatorLayout.layoutParams = ConstraintLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
        ).apply {
            topToBottom = R.id.appbar
            bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
        }

        layoutInflater.inflate(R.layout.layout_balance_changes_bottom_sheet,
                coordinatorLayout, true)

        root_layout.addView(coordinatorLayout)

        bottomSheet = BottomSheetBehavior.from(balance_changes_bottom_sheet_layout)
                as BottomSheetBehaviorWithNestedScrollChild<*>

        // Fade header.
        bottomSheet.setBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(p0: View, p1: Int) {}

            override fun onSlide(p0: View, offset: Float) {
                bottom_sheet_header_fade.alpha = offset
            }
        })

        val fragment = BottomSheetAssetMovementsFragment()

        fragment
                .recyclerViewObservable
                .subscribe(bottomSheet::setNestedScrollChild)
                .addTo(compositeDisposable)

        childFragmentManager
                .beginTransaction()
                .replace(R.id.fragment_container_layout, fragment)
                .commit()

        scrollable_root_layout.addView(
                Space(requireContext()).apply {
                    layoutParams = ViewGroup.LayoutParams(
                            0, bottomSheet.peekHeight
                    )
                }
        )
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

    override fun onBackPressed(): Boolean {
        return if (bottomSheet.state == BottomSheetBehavior.STATE_EXPANDED) {
            bottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED
            false
        } else {
            true
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