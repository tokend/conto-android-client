package org.tokend.template.features.invites.logic

import io.reactivex.Completable
import org.tokend.rx.extensions.toCompletable
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.di.providers.WalletInfoProvider

class InviteNewUsersUseCase(
        private val emails: List<String>,
        private val walletInfoProvider: WalletInfoProvider,
        private val apiProvider: ApiProvider
) {

    fun perform(): Completable {
        val accountId = walletInfoProvider.getWalletInfo()?.accountId
                ?: return Completable.error(IllegalStateException("No wallet info found"))

        val signedApi = apiProvider.getSignedApi()
                ?: return Completable.error(IllegalStateException("No signed API instance found"))

        return signedApi
                .integrations
                .dns
                .inviteClients(
                        accountId,
                        emails
                ).toCompletable()
    }
}