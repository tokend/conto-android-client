package org.tokend.template.features.swap.create.view

import org.tokend.template.data.model.BalanceRecord
import org.tokend.template.features.amountscreen.view.AmountInputFragment
import org.tokend.template.view.balancepicker.BalancePickerBottomDialog

class SwapAmountFragment : AmountInputFragment() {
    override fun getTitleText(): String? = null

    override fun getBalancePicker(): BalancePickerBottomDialog {
        return BalancePickerBottomDialog(
                requireContext(),
                amountFormatter,
                balanceComparator,
                balancesRepository,
                balancesFilter = BalanceRecord::hasAvailableAmount
        )
    }
}