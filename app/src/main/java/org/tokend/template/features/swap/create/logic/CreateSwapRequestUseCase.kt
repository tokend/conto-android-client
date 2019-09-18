package org.tokend.template.features.swap.create.logic

import io.reactivex.Single
import io.reactivex.rxkotlin.toMaybe
import io.reactivex.rxkotlin.toSingle
import io.reactivex.schedulers.Schedulers
import org.tokend.template.data.model.Asset
import org.tokend.template.data.repository.BalancesRepository
import org.tokend.template.di.providers.WalletInfoProvider
import org.tokend.template.features.send.model.PaymentRecipient
import org.tokend.template.features.swap.create.model.SwapRequest
import java.math.BigDecimal
import java.security.SecureRandom

class CreateSwapRequestUseCase(
        private val baseAmount: BigDecimal,
        private val baseAsset: Asset,
        private val quoteAmount: BigDecimal,
        private val quoteAsset: Asset,
        private val counterparty: PaymentRecipient,
        private val balancesRepository: BalancesRepository,
        private val walletInfoProvider: WalletInfoProvider
) {
    private lateinit var sourceAccountId: String
    private lateinit var baseBalanceId: String
    private lateinit var secret: ByteArray

    fun perform(): Single<SwapRequest> {
        return getAccountId()
                .doOnSuccess { accountId ->
                    this.sourceAccountId = accountId
                }
                .flatMap {
                    getBaseBalanceId()
                }
                .doOnSuccess { baseBalanceId ->
                    this.baseBalanceId = baseBalanceId
                }
                .flatMap {
                    getSecret()
                }
                .doOnSuccess { secret ->
                    this.secret = secret
                }
                .flatMap { getRequest() }
    }

    private fun getAccountId(): Single<String> {
        return walletInfoProvider
                .getWalletInfo()
                ?.accountId
                .toMaybe()
                .switchIfEmpty(Single.error(IllegalStateException("Missing account ID")))
    }

    private fun getBaseBalanceId(): Single<String> {
        return {
            balancesRepository.itemsList
                    .find { it.assetCode == baseAsset.code }
                    ?.id
                    ?: throw IllegalStateException("No balance ID found for $baseAsset")
        }.toSingle()
    }

    private fun getSecret(): Single<ByteArray> {
        return {
            SecureRandom.getSeed(SECRET_SIZE_BYTES)
        }.toSingle().subscribeOn(Schedulers.computation())
    }

    private fun getRequest(): Single<SwapRequest> {
        return {
            SwapRequest(
                    sourceAccountId = sourceAccountId,
                    baseBalanceId = baseBalanceId,
                    baseAsset = baseAsset,
                    baseAmount = baseAmount,
                    quoteAsset = quoteAsset,
                    quoteAmount = quoteAmount,
                    destAccountId = counterparty.accountId,
                    destEmail = counterparty.nickname!!,
                    secret = secret
            )
        }.toSingle()
    }

    companion object {
        private const val SECRET_SIZE_BYTES = 32
    }
}