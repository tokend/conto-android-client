package org.tokend.template.features.dashboard.balances.view

import android.support.v4.content.ContextCompat
import android.view.ContextThemeWrapper
import android.view.View
import com.github.clans.fab.FloatingActionButton
import kotlinx.android.synthetic.main.fragment_balances.*
import org.tokend.template.BuildConfig
import org.tokend.template.R
import org.tokend.template.features.dashboard.balances.view.adapter.BalanceListItem
import org.tokend.template.util.Navigator

class CompanyBalancesFragment : BalancesFragment() {

    override fun getFabActions(): Collection<FloatingActionButton> {
        val themedContext = ContextThemeWrapper(requireContext(), R.style.FloatingButtonMenuItem)
        val actions = mutableListOf<FloatingActionButton>()

        // Accept redemption.
        val accountId = walletInfoProvider.getWalletInfo()?.accountId
        val balances = balancesRepository.itemsList
        if (balances.any { it.asset.ownerAccountId == accountId }) {
            actions.add(
                    FloatingActionButton(themedContext, null, R.style.FloatingButtonMenuItem)
                            .apply {
                                labelText = getString(R.string.accept_redemption)
                                setImageDrawable(ContextCompat.getDrawable(
                                        requireContext(),
                                        R.drawable.ic_qr_code_scan_fab)
                                )
                                setOnClickListener {
                                    Navigator.from(this@CompanyBalancesFragment)
                                            .openScanRedemption()
                                    menu_fab.close(false)
                                }
                            }
            )
        }

        // Send.
        if (BuildConfig.IS_SEND_ALLOWED) {
            actions.add(
                    FloatingActionButton(themedContext, null, R.style.FloatingButtonMenuItem)
                            .apply {
                                labelText = getString(R.string.send_title)
                                setImageDrawable(ContextCompat.getDrawable(
                                        requireContext(),
                                        R.drawable.ic_send_fab)
                                )
                                setOnClickListener {
                                    Navigator.from(this@CompanyBalancesFragment)
                                            .openSend()
                                    menu_fab.close(false)
                                }
                            }
            )
        }

        // Issuance
        if (balances.any { it.asset.ownerAccountId == accountId }) {
            actions.add(
                    FloatingActionButton(themedContext, null, R.style.FloatingButtonMenuItem)
                            .apply {
                                labelText = getString(R.string.issuance_title)
                                setImageDrawable(ContextCompat.getDrawable(
                                        requireContext(),
                                        R.drawable.ic_issuance_white)
                                )
                                setOnClickListener {
                                    Navigator.from(this@CompanyBalancesFragment)
                                            .openMassIssuance()
                                    menu_fab.close(false)
                                }
                            }
            )
        }

        return actions
    }

    override fun displayBalances() {
        val companyId = walletInfoProvider.getWalletInfo()?.accountId

        val items = balancesRepository
                .itemsList
                .sortedWith(balanceComparator)
                .filter { it.asset.ownerAccountId == companyId }
                .map(::BalanceListItem)

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