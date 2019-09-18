package org.tokend.template.di.providers

import org.tokend.sdk.api.TokenDApi
import org.tokend.sdk.keyserver.KeyServer

interface ApiProvider {
    fun getApi(index: Int = 0): TokenDApi
    fun getSignedApi(index: Int = 0): TokenDApi?
    fun getKeyServer(index: Int = 0): KeyServer
    fun getSignedKeyServer(index: Int = 0): KeyServer?
}