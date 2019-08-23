package org.tokend.template.data.repository

import io.reactivex.Maybe
import io.reactivex.Single
import io.reactivex.rxkotlin.toMaybe
import org.tokend.rx.extensions.toSingle
import org.tokend.sdk.api.identity.params.IdentitiesPageParams
import org.tokend.template.data.model.IdentityRecord
import org.tokend.template.di.providers.ApiProvider
import retrofit2.HttpException
import java.net.HttpURLConnection

class AccountDetailsRepository(
        private val apiProvider: ApiProvider
) {
    class NoIdentityAvailableException : Exception()

    private val identities = mutableSetOf<IdentityRecord>()
    private val notExistingIdentifiers = mutableSetOf<String>()

    /**
     * Loads account ID for given identifier.
     * Result will be cached.
     *
     * @param identifier - email or phone number
     *
     * @see NoIdentityAvailableException
     */
    fun getAccountIdByIdentifier(identifier: String): Single<String> {
        val formattedIdentifier = identifier.toLowerCase()
        val existing = identities.find {
            it.email == formattedIdentifier || it.phoneNumber == formattedIdentifier
        }?.accountId
        if (existing != null) {
            return Single.just(existing)
        }

        return getIdentity(IdentitiesPageParams(identifier = formattedIdentifier))
                .map(IdentityRecord::accountId)
    }

    /**
     * Loads email for given account ID.
     * Result will be cached.
     *
     * @see NoIdentityAvailableException
     */
    fun getEmailByAccountId(accountId: String): Single<String> {
        val existing = identities.find { it.accountId == accountId }?.email
        if (existing != null) {
            return Single.just(existing)
        }

        return getIdentity(IdentitiesPageParams(address = accountId))
                .map(IdentityRecord::email)
    }

    /**
     * Loads phone number for given account ID if it exists.
     * Result will be cached.
     *
     * @see NoIdentityAvailableException
     */
    fun getPhoneByAccountId(accountId: String): Maybe<String> {
        val existingIdentity = identities.find { it.accountId == accountId }

        if (existingIdentity != null) {
            return existingIdentity.phoneNumber.toMaybe()
        }

        return getIdentity(IdentitiesPageParams(address = accountId))
                .flatMapMaybe { it.phoneNumber.toMaybe() }
    }

    fun getEmailsByAccountIds(accountIds: List<String>): Single<Map<String, String>> {
        val toReturn = mutableMapOf<String, String>()
        val toRequest = mutableListOf<String>()

        val identitiesByAccountId = identities.associateBy(IdentityRecord::accountId)

        accountIds
                .forEach { accountId ->
                    val cached = identitiesByAccountId[accountId]
                    if (cached != null) {
                        toReturn[accountId] = cached.email
                    } else if (!notExistingIdentifiers.contains(accountId)) {
                        toRequest.add(accountId)
                    }
                }

        if (toRequest.isEmpty()) {
            return Single.just(toReturn)
        }

        val signedApi = apiProvider.getSignedApi()
                ?: return Single.error(IllegalStateException("No signed API instance found"))

        return signedApi
                .identities
                .getForAccounts(toRequest)
                .toSingle()
                .map {
                    it.map(::IdentityRecord)
                }
                .map { identities ->
                    this.identities.addAll(identities)
                    toReturn.putAll(
                            identities
                                    .associateBy(IdentityRecord::accountId)
                                    .mapValues { it.value.email }
                    )
                    toReturn
                }
    }

    private fun getIdentity(params: IdentitiesPageParams): Single<IdentityRecord> {
        val identifier = params.identifier ?: params.address

        if (identifier != null && notExistingIdentifiers.contains(identifier)) {
            return Single.error(NoIdentityAvailableException())
        }

        return apiProvider
                .getApi()
                .identities
                .get(params)
                .toSingle()
                .map { detailsPage ->
                    detailsPage.items.firstOrNull()
                            ?: throw NoIdentityAvailableException()
                }
                .onErrorResumeNext {
                    if (it is HttpException && it.code() == HttpURLConnection.HTTP_NOT_FOUND)
                        Single.error(NoIdentityAvailableException())
                    else
                        Single.error(it)
                }
                .map(::IdentityRecord)
                .doOnSuccess { identities.add(it) }
                .doOnError {
                    if (it is NoIdentityAvailableException && identifier != null) {
                        notExistingIdentifiers.add(identifier)
                    }
                }
    }

    fun getCachedIdentity(accountId: String): IdentityRecord? {
        return identities.find { it.accountId == accountId }
    }

    fun invalidateCachedIdentity(accountId: String) {
        identities.remove(getCachedIdentity(accountId))
    }
}