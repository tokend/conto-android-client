package org.tokend.template.features.nfcpayment.logic

import io.reactivex.Completable
import org.tokend.wallet.Transaction

interface TransactionBroadcaster {
    fun broadcastTransaction(transaction: Transaction): Completable
}