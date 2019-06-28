package org.tokend.template.logic

import org.tokend.template.data.model.CompanyRecord
import org.tokend.template.di.providers.AccountProvider
import org.tokend.template.di.providers.CompanyInfoProvider
import org.tokend.template.di.providers.WalletInfoProvider
import org.tokend.template.features.signin.logic.SignInMethod
import org.tokend.template.logic.persistance.SessionInfoStorage

/**
 * Holds session data
 */
class Session(
        walletInfoProvider: WalletInfoProvider,
        accountProvider: AccountProvider,
        private val sessionInfoStorage: SessionInfoStorage? = null
) : WalletInfoProvider by walletInfoProvider,
        AccountProvider by accountProvider,
        CompanyInfoProvider {

    /**
     * @returns true if session is expired and so sign out is required
     */
    var isExpired = false

    /**
     * @returns [SignInMethod] used to start this session
     */
    var signInMethod: SignInMethod? = null
        set(value) {
            field = value
            if (value != null) {
                sessionInfoStorage?.saveLastSignInMethod(value)
            }
        }

    /**
     * @returns last used [SignInMethod]
     */
    val lastSignInMethod: SignInMethod?
        get() = sessionInfoStorage?.loadLastSignInMethod()

    /**
     * @returns true if session was started with TokenD Authenticator auth
     */
    val isAuthenticatorUsed
        get() = signInMethod == SignInMethod.AUTHENTICATOR


    private var company: CompanyRecord? = null

    override fun setCompany(company: CompanyRecord?) {
        this.company = company
        sessionInfoStorage?.saveLastCompanyId(company?.id)
    }

    override fun getCompany(): CompanyRecord? = company

    /**
     * @returns last saved companyId
     */
    val lastCompanyId: String?
        get() = sessionInfoStorage?.loadLastCompanyId()

    /**
     * Resets the session to the initial state, clears data
     */
    fun reset() {
        isExpired = false
        signInMethod = null

        setWalletInfo(null)
        setAccount(null)
        setCompany(null)
    }
}