package org.tokend.template.di.providers

import okhttp3.CookieJar
import org.tokend.sdk.keyserver.models.WalletInfo
import org.tokend.sdk.tfa.TfaCallback
import org.tokend.wallet.Account

class ApiProviderFactory {
    fun createApiProvider(urlConfigProvider: UrlConfigProvider,
                          account: Account? = null,
                          walletInfo: WalletInfo? = null,
                          tfaCallback: TfaCallback? = null,
                          cookieJar: CookieJar? = null): ApiProvider {
        return createApiProvider(
                urlConfigProvider,
                AccountProviderFactory().createAccountProvider(account),
                WalletInfoProviderFactory().createWalletInfoProvider(walletInfo),
                tfaCallback,
                cookieJar
        )
    }

    fun createApiProvider(urlConfigProvider: UrlConfigProvider,
                          accountProvider: AccountProvider,
                          walletInfoProvider: WalletInfoProvider,
                          tfaCallback: TfaCallback? = null,
                          cookieJar: CookieJar? = null): ApiProvider {
        return ApiProviderImpl(urlConfigProvider, accountProvider, walletInfoProvider,
                tfaCallback, cookieJar)
    }
}