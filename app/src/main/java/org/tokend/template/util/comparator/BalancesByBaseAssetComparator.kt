package org.tokend.template.util.comparator

import org.tokend.template.features.balances.model.BalanceRecord
import org.tokend.wallet.xdr.AssetPolicy

/**
 * Compares [BalanceRecord]s by checking [AssetPolicy.BASE_ASSET] policy
 * of it's asset.
 * Order is ascending (balances of non-base assets will be first)
 */
class BalancesByBaseAssetComparator(
        private val fallbackComparator: Comparator<BalanceRecord>?
) : Comparator<BalanceRecord> {
    override fun compare(o1: BalanceRecord, o2: BalanceRecord): Int {
        val result = o1.asset.isBase.compareTo(o2.asset.isBase)
        return if (result == 0 && fallbackComparator != null)
            fallbackComparator.compare(o1, o2)
        else
            result
    }
}