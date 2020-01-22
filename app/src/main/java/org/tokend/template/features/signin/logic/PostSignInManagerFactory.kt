package org.tokend.template.features.signin.logic

import org.tokend.template.di.providers.AccountProvider
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.di.providers.RepositoryProvider
import org.tokend.template.di.providers.WalletInfoProvider
import org.tokend.template.features.signin.model.ForcedAccountType
import org.tokend.template.util.ConnectionStateUtil

class PostSignInManagerFactory(
        private val apiProvider: ApiProvider,
        private val accountProvider: AccountProvider,
        private val walletInfoProvider: WalletInfoProvider,
        private val repositoryProvider: RepositoryProvider,
        private val connectionStateUtil: ConnectionStateUtil
) {
    fun get(forcedAccountType: ForcedAccountType? = null): PostSignInManager {
        return PostSignInManager(apiProvider, accountProvider,
                walletInfoProvider, repositoryProvider, forcedAccountType,
                connectionStateUtil::isOnline)
    }
}