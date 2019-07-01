package org.tokend.template.util.comparator

import org.tokend.template.data.model.Asset
import org.tokend.template.data.model.BalanceRecord

/**
 * Compares [BalanceRecord]s by existence of the available amount.
 * Order is descending (balances with non-zero amounts will be first)
 *
 * @param assetComparator comparator for asset codes, will be used
 * for further ordering
 */
class BalancesByAmountExistenceComparator(
        private val assetCodeComparator: Comparator<Asset>
) : Comparator<BalanceRecord> {
    override fun compare(o1: BalanceRecord, o2: BalanceRecord): Int {
        val result = (o2.available.signum() > 0).compareTo(o1.available.signum() > 0)
        return if (result == 0)
            assetCodeComparator.compare(o1.asset, o2.asset)
        else
            result
    }
}