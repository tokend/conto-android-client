package org.tokend.template.features.nfcpayment.logic

import io.reactivex.Single
import org.tokend.rx.extensions.toSingle
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.di.providers.WalletInfoProvider
import org.tokend.template.features.nfcpayment.model.FulfilledPosPaymentRequest
import org.tokend.template.features.nfcpayment.model.PosPaymentRequest
import java.math.BigDecimal

class PosPaymentRequestFulfiller(
        private val walletInfoProvider: WalletInfoProvider,
        private val apiProvider: ApiProvider
) {
    class InsufficientOrMissingBalanceException(assetCode: String,
                                                val missingAmount: BigDecimal) :
            Exception("Insufficient or missing balance of $assetCode")

    fun fulfill(request: PosPaymentRequest): Single<FulfilledPosPaymentRequest> {
        return getSenderBalance(request)
                .map { balanceId ->
                    FulfilledPosPaymentRequest(balanceId, request)
                }
    }

    private fun getSenderBalance(request: PosPaymentRequest): Single<String> {
        val signedApi = apiProvider.getSignedApi()
                ?: return Single.error(IllegalStateException("No signed API instance found"))
        val accountId = walletInfoProvider.getWalletInfo()?.accountId
                ?: return Single.error(IllegalStateException("No wallet info found"))

        val assetCode = request.asset.code

        return signedApi.v3.accounts
                .getBalances(accountId)
                .toSingle()
                .flatMap { balances ->
                    val balance = balances
                            .find { it.asset.id == assetCode }
                    val available = balance?.state?.available ?: BigDecimal.ZERO

                    if (balance == null || available < request.amount)
                        Single.error(InsufficientOrMissingBalanceException(
                                assetCode,
                                request.amount - available
                        ))
                    else
                        Single.just(balance.id)
                }
    }
}