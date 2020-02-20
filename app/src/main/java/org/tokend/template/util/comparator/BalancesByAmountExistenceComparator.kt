package org.tokend.template.util.comparator

import org.tokend.template.features.balances.model.BalanceRecord

/**
 * Compares [BalanceRecord]s by existence of the available amount
 * ([BalanceRecord.hasAvailableAmount]).
 * Order is descending (balances with non-zero amounts will be first)
 */
class BalancesByAmountExistenceComparator(
        private val fallbackComparator: Comparator<BalanceRecord>?
) : Comparator<BalanceRecord> {
    override fun compare(o1: BalanceRecord, o2: BalanceRecord): Int {
        val result = o2.hasAvailableAmount.compareTo(o1.hasAvailableAmount)
        return if (result == 0 && fallbackComparator != null)
            fallbackComparator.compare(o1, o2)
        else
            result
    }
}