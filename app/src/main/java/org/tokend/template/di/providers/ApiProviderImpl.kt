package org.tokend.template.di.providers

import okhttp3.CookieJar
import org.tokend.sdk.api.TokenDApi
import org.tokend.sdk.keyserver.KeyServer
import org.tokend.sdk.signing.AccountRequestSigner
import org.tokend.sdk.tfa.TfaCallback
import org.tokend.sdk.utils.CookieJarProvider
import org.tokend.sdk.utils.HashCodes
import org.tokend.template.BuildConfig

class ApiProviderImpl(
        private val urlConfigProvider: UrlConfigProvider,
        private val accountProvider: AccountProvider,
        private val tfaCallback: TfaCallback?,
        cookieJar: CookieJar?) : ApiProvider {
    private var cookieJarProvider = cookieJar?.let {
        object : CookieJarProvider {
            override fun getCookieJar(): CookieJar {
                return it
            }
        }
    }

    private val withLogs: Boolean
        get() = BuildConfig.WITH_LOGS

    private var apiByHash: Pair<Int, TokenDApi>? = null
    private var signedApiByHash: Pair<Int, TokenDApi>? = null

    private fun getUrl(index: Int): String {
        return urlConfigProvider.getConfig(index).api
    }

    override fun getApi(index: Int): TokenDApi = synchronized(this) {
        val url = getUrl(index)
        val hash = url.hashCode()

        val api = apiByHash
                ?.takeIf { (currentHash, _) ->
                    currentHash == hash
                }
                ?.second
                ?: TokenDApi(
                        url,
                        null,
                        tfaCallback,
                        cookieJarProvider,
                        withLogs = withLogs
                )

        apiByHash = Pair(hash, api)

        return api
    }

    override fun getKeyServer(index: Int): KeyServer {
        return KeyServer(getApi(index).wallets)
    }

    override fun getSignedApi(index: Int): TokenDApi? = synchronized(this) {
        val account = accountProvider.getAccount() ?: return null
        val url = getUrl(index)
        val hash = HashCodes.ofMany(account.accountId, url)

        val signedApi =
                signedApiByHash
                        ?.takeIf { (currentHash, _) ->
                            currentHash == hash
                        }
                        ?.second
                        ?: TokenDApi(
                                url,
                                AccountRequestSigner(account),
                                tfaCallback,
                                cookieJarProvider,
                                withLogs = withLogs
                        )

        signedApiByHash = Pair(hash, signedApi)

        return signedApi
    }

    override fun getSignedKeyServer(index: Int): KeyServer? {
        return getSignedApi()?.let { KeyServer(it.wallets) }
    }
}