package org.tokend.template.features.withdraw.amount.view

import android.os.Bundle
import org.tokend.template.features.amountscreen.view.AmountInputFragment
import org.tokend.template.view.balancepicker.BalancePickerBottomDialog

class WithdrawAmountFragment : AmountInputFragment() {
    override fun getBalancePicker(): BalancePickerBottomDialog {
        val companyId = companyInfoProvider.getCompany()?.id

        return BalancePickerBottomDialog(
                requireContext(),
                amountFormatter,
                balanceComparator,
                balancesRepository
        ) { balance ->
            balance.asset.isWithdrawable
                    && balance.asset.ownerAccountId == companyId
        }
    }

    override fun getTitleText(): String? {
        return null
    }

    companion object {

        fun newInstance(requiredAsset: String? = null): WithdrawAmountFragment {
            val fragment = WithdrawAmountFragment()
            fragment.arguments = Bundle().apply {
                putString(ASSET_EXTRA, requiredAsset)
            }
            return fragment
        }
    }
}