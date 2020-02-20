package org.tokend.template.features.nfcpayment.logic

import io.reactivex.Single
import org.tokend.template.di.providers.RepositoryProvider
import org.tokend.template.features.assets.model.Asset
import org.tokend.template.features.nfcpayment.model.PosPaymentRequest
import org.tokend.template.features.nfcpayment.model.RawPosPaymentRequest
import org.tokend.template.util.BiFunctionToPair
import org.tokend.wallet.NetworkParams

/**
 * Loads required data to create [PosPaymentRequest] from the raw one.
 */
class PosPaymentRequestLoader(
        val repositoryProvider: RepositoryProvider
) {
    fun load(rawRequest: RawPosPaymentRequest): Single<PosPaymentRequest> {
        return Single.zip(
                getNetworkParams(),
                getAsset(rawRequest),
                BiFunctionToPair<NetworkParams, Asset>()
        )
                .map { (networkParams, asset) ->
                    PosPaymentRequest(
                            asset = asset,
                            networkParams = networkParams,
                            rawRequest = rawRequest
                    )
                }
    }

    private fun getNetworkParams(): Single<NetworkParams> {
        return repositoryProvider.systemInfo()
                .getNetworkParams()
    }

    private fun getAsset(rawRequest: RawPosPaymentRequest): Single<out Asset> {
        return repositoryProvider.assets()
                .getSingle(rawRequest.assetCode)
    }
}