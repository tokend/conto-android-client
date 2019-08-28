package org.tokend.template.features.dashboard.balances.view

import android.view.View
import kotlinx.android.synthetic.main.fragment_balances.*
import org.tokend.template.BuildConfig
import org.tokend.template.R
import org.tokend.template.features.dashboard.balances.view.adapter.BalanceListItem
import org.tokend.template.util.Navigator
import org.tokend.template.view.util.fab.FloatingActionMenuAction

class CompanyBalancesFragment : BalancesFragment() {

    override fun getFabActions(): Collection<FloatingActionMenuAction> {
        val accountId = walletInfoProvider.getWalletInfo()?.accountId
        val balances = balancesRepository.itemsList
        val navigator = Navigator.from(this)

        val actions = mutableListOf<FloatingActionMenuAction>()

        // Accept redemption.
        actions.add(FloatingActionMenuAction(
                requireContext(),
                R.string.accept_redemption,
                R.drawable.ic_qr_code_scan_fab,
                {
                    navigator.openScanRedemption()
                },
                isEnabled = balances.any { it.asset.ownerAccountId == accountId }
        ))

        // Send.
        if (BuildConfig.IS_SEND_ALLOWED) {
            actions.add(FloatingActionMenuAction(
                    requireContext(),
                    R.string.send_title,
                    R.drawable.ic_send_fab,
                    {
                        navigator.openSend()
                    },
                    isEnabled = balances.any { it.asset.isTransferable }
            ))
        }

        // Issuance.
        actions.add(FloatingActionMenuAction(
                requireContext(),
                R.string.issuance_title,
                R.drawable.ic_issuance_white,
                {
                    navigator.openMassIssuance()
                },
                isEnabled = balances.any { it.asset.ownerAccountId == accountId }
        ))

        return actions
    }

    override fun displayBalances() {
        val companyId = walletInfoProvider.getWalletInfo()?.accountId

        val items = balancesRepository
                .itemsList
                .sortedWith(balanceComparator)
                .filter { it.asset.ownerAccountId == companyId }
                .map {
                    BalanceListItem(it, ownerName = null)
                }

        adapter.setData(items)
    }

    override fun displayTotal() {
        total_text_view.visibility = View.GONE
    }

    companion object {
        val ID = "company_balances".hashCode().toLong()

        fun newInstance(): CompanyBalancesFragment {
            val fragment = CompanyBalancesFragment()
            fragment.arguments = BalancesFragment.getBundle(true)
            return fragment
        }
    }
}