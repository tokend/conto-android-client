package org.tokend.template.util.comparator

import org.tokend.template.features.balances.model.BalanceRecord

/**
 * Compares [BalanceRecord]s by asset code
 */
class BalancesByAssetCodeComparator(
        private val assetCodeComparator: Comparator<String>
): Comparator<BalanceRecord> {
    override fun compare(o1: BalanceRecord?, o2: BalanceRecord?): Int {
        return assetCodeComparator.compare(o1?.assetCode, o2?.assetCode)
    }
}