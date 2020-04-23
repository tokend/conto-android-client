package org.tokend.template.util.navigation

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.app.ActivityOptionsCompat
import android.support.v4.app.Fragment
import android.view.View
import org.jetbrains.anko.clearTop
import org.jetbrains.anko.intentFor
import org.jetbrains.anko.newTask
import org.jetbrains.anko.singleTop
import org.tokend.template.R
import org.tokend.template.activities.CorporateMainActivity
import org.tokend.template.activities.MainActivity
import org.tokend.template.activities.SingleFragmentActivity
import org.tokend.template.extensions.getBigDecimalExtra
import org.tokend.template.features.accountdetails.view.AccountDetailsFragment
import org.tokend.template.features.assets.buy.marketplace.model.MarketplaceOfferRecord
import org.tokend.template.features.assets.buy.view.AtomicSwapAsksFragment
import org.tokend.template.features.assets.buy.view.BuyAssetOnMarketplaceActivity
import org.tokend.template.features.assets.buy.view.WebInvoiceActivity
import org.tokend.template.features.assets.details.refund.view.AssetRefundConfirmationActivity
import org.tokend.template.features.assets.details.view.AssetDetailsActivity
import org.tokend.template.features.assets.model.Asset
import org.tokend.template.features.assets.model.AssetRecord
import org.tokend.template.features.assets.view.ExploreAssetsFragment
import org.tokend.template.features.balances.view.BalanceDetailsActivity
import org.tokend.template.features.changepassword.ChangePasswordActivity
import org.tokend.template.features.clients.details.movements.view.CompanyClientMovementsActivity
import org.tokend.template.features.clients.details.view.CompanyClientDetailsActivity
import org.tokend.template.features.clients.model.CompanyClientRecord
import org.tokend.template.features.companies.details.view.CompanyDetailsActivity
import org.tokend.template.features.companies.model.CompanyRecord
import org.tokend.template.features.deposit.view.DepositAmountActivity
import org.tokend.template.features.deposit.view.DepositFragment
import org.tokend.template.features.fees.view.FeesActivity
import org.tokend.template.features.history.details.*
import org.tokend.template.features.history.model.BalanceChange
import org.tokend.template.features.history.model.details.BalanceChangeCause
import org.tokend.template.features.invest.model.SaleRecord
import org.tokend.template.features.invest.view.InvestmentConfirmationActivity
import org.tokend.template.features.invest.view.SaleActivity
import org.tokend.template.features.invest.view.SaleInvestActivity
import org.tokend.template.features.invites.view.InviteNewUsersActivity
import org.tokend.template.features.kyc.model.ActiveKyc
import org.tokend.template.features.kyc.model.KycForm
import org.tokend.template.features.kyc.view.ClientKycActivity
import org.tokend.template.features.kyc.view.WaitForKycApprovalActivity
import org.tokend.template.features.limits.view.LimitsActivity
import org.tokend.template.features.localaccount.importt.view.ImportLocalAccountActivity
import org.tokend.template.features.localaccount.view.LocalAccountDetailsActivity
import org.tokend.template.features.massissuance.model.MassIssuanceRequest
import org.tokend.template.features.massissuance.view.MassIssuanceActivity
import org.tokend.template.features.massissuance.view.MassIssuanceConfirmationActivity
import org.tokend.template.features.movements.view.AssetMovementsFragment
import org.tokend.template.features.nfcpayment.model.RawPosPaymentRequest
import org.tokend.template.features.nfcpayment.view.NfcPaymentActivity
import org.tokend.template.features.offers.CreateOfferActivity
import org.tokend.template.features.offers.OfferConfirmationActivity
import org.tokend.template.features.offers.OffersActivity
import org.tokend.template.features.offers.UpdateOfferActivity
import org.tokend.template.features.offers.model.OfferRecord
import org.tokend.template.features.offers.model.OfferRequest
import org.tokend.template.features.offers.view.details.PendingInvestmentDetailsActivity
import org.tokend.template.features.offers.view.details.PendingOfferDetailsActivity
import org.tokend.template.features.qr.ShareQrFragment
import org.tokend.template.features.recovery.RecoveryActivity
import org.tokend.template.features.redeem.accept.view.ConfirmRedemptionActivity
import org.tokend.template.features.redeem.accept.view.ScanRedemptionActivity
import org.tokend.template.features.redeem.create.view.CreateRedemptionActivity
import org.tokend.template.features.redeem.create.view.ShareRedemptionQrFragment
import org.tokend.template.features.redeem.create.view.SimpleRedemptionFragment
import org.tokend.template.features.send.PaymentConfirmationActivity
import org.tokend.template.features.send.SendFragment
import org.tokend.template.features.send.model.PaymentRequest
import org.tokend.template.features.settings.GeneralSettingsFragment
import org.tokend.template.features.settings.phonenumber.view.PhoneNumberSettingsActivity
import org.tokend.template.features.settings.telegram.view.TelegramUsernameSettingsActivity
import org.tokend.template.features.shaketopay.view.ShakeToPayActivity
import org.tokend.template.features.signin.ForceAccountTypeActivity
import org.tokend.template.features.signin.LocalAccountSignInActivity
import org.tokend.template.features.signin.SignInActivity
import org.tokend.template.features.signin.model.ForcedAccountType
import org.tokend.template.features.signin.unlock.view.UnlockAppActivity
import org.tokend.template.features.signup.SignUpActivity
import org.tokend.template.features.trade.TradeActivity
import org.tokend.template.features.trade.pairs.model.AssetPairRecord
import org.tokend.template.features.wallet.view.SimpleBalanceDetailsActivity
import org.tokend.template.features.withdraw.WithdrawFragment
import org.tokend.template.features.withdraw.WithdrawalConfirmationActivity
import org.tokend.template.features.withdraw.model.WithdrawalRequest
import org.tokend.template.util.IntentLock
import java.math.BigDecimal

/**
 * Performs transitions between screens.
 * 'open-' will open related screen as a child.<p>
 * 'to-' will open related screen and finish current.
 */
class Navigator private constructor() {
    private var activity: Activity? = null
    private var fragment: Fragment? = null
    private var context: Context? = null

    companion object {
        fun from(activity: Activity): Navigator {
            val navigator = Navigator()
            navigator.activity = activity
            navigator.context = activity
            return navigator
        }

        fun from(fragment: Fragment): Navigator {
            val navigator = Navigator()
            navigator.fragment = fragment
            navigator.context = fragment.requireContext()
            return navigator
        }

        fun from(context: Context): Navigator {
            val navigator = Navigator()
            navigator.context = context
            return navigator
        }
    }

    private fun performIntent(intent: Intent?, requestCode: Int? = null, bundle: Bundle? = null) {
        if (intent != null) {
            if (!IntentLock.checkIntent(intent, context)) return
            activity?.let {
                if (requestCode != null) {
                    ActivityCompat.startActivityForResult(it, intent, requestCode, bundle)
                } else {
                    ActivityCompat.startActivity(it, intent, bundle)
                }
                return
            }

            fragment?.let {
                if (requestCode != null) {
                    it.startActivityForResult(intent, requestCode, bundle)
                } else {
                    it.startActivity(intent, bundle)
                }
                return
            }

            context?.let {
                ActivityCompat.startActivity(it, intent.newTask(), bundle)
            }
        }
    }

    private fun createAndPerformIntent(activityClass: Class<*>,
                                       extras: Bundle? = null,
                                       requestCode: Int? = null,
                                       transitionBundle: Bundle? = null,
                                       intentModifier: (Intent.() -> Intent)? = null) {
        var intent = context?.let { Intent(it, activityClass) }
                ?: return

        if (extras != null) {
            intent.putExtras(extras)
        }

        if (intentModifier != null) {
            intent = intentModifier.invoke(intent)
        }

        performIntent(intent, requestCode, transitionBundle)
    }

    private fun <R : Any> createAndPerformRequest(request: ActivityRequest<R>,
                                                  activityClass: Class<*>,
                                                  extras: Bundle? = null,
                                                  transitionBundle: Bundle? = null
    ): ActivityRequest<R> {
        createAndPerformIntent(activityClass, extras, request.code, transitionBundle)
        return request
    }

    private fun createAndPerformSimpleRequest(activityClass: Class<*>,
                                              extras: Bundle? = null,
                                              transitionBundle: Bundle? = null
    ) = createAndPerformRequest(ActivityRequest.withoutResultData(),
            activityClass, extras, transitionBundle)

    private fun fadeOut(activity: Activity) {
        ActivityCompat.finishAfterTransition(activity)
        activity.overridePendingTransition(0, R.anim.activity_fade_out)
        activity.finish()
    }

    private fun createTransitionBundle(activity: Activity, vararg pairs: Pair<View?, String>): Bundle {
        val sharedViews = arrayListOf<android.support.v4.util.Pair<View, String>>()

        pairs.forEach {
            val view = it.first
            if (view != null) {
                sharedViews.add(android.support.v4.util.Pair(view, it.second))
            }
        }

        return if (sharedViews.isEmpty()) {
            Bundle.EMPTY
        } else {
            ActivityOptionsCompat.makeSceneTransitionAnimation(activity,
                    *sharedViews.toTypedArray()).toBundle() ?: Bundle.EMPTY
        }
    }

    fun openSignUp() {
        context?.intentFor<SignUpActivity>()
                ?.also { performIntent(it) }
    }

    fun openRecovery(email: String? = null) = createAndPerformSimpleRequest(
            RecoveryActivity::class.java,
            RecoveryActivity.getBundle(email)
    )

    fun toSignIn(finishAffinity: Boolean = false) {
        context?.intentFor<SignInActivity>()
                ?.singleTop()
                ?.clearTop()
                ?.also { performIntent(it) }
        activity?.let {
            if (finishAffinity) {
                it.setResult(Activity.RESULT_CANCELED, null)
                ActivityCompat.finishAffinity(it)
            } else {
                it.finish()
            }
        }
    }

    fun toUnlock() {
        context?.intentFor<UnlockAppActivity>()
                ?.singleTop()
                ?.clearTop()
                ?.also { performIntent(it) }
        activity?.finish()
    }

    fun toClientMainActivity(finishAffinity: Boolean = false) {
        context?.intentFor<MainActivity>()
                ?.also { performIntent(it) }
        activity?.let {
            if (finishAffinity) {
                it.setResult(Activity.RESULT_CANCELED, null)
                ActivityCompat.finishAffinity(it)
            } else {
                fadeOut(it)
            }
        }
    }

    fun toCorporateMainActivity() {
        context?.intentFor<CorporateMainActivity>()
                ?.also { performIntent(it) }
        activity?.let { fadeOut(it) }
    }

    fun openQrShare(title: String,
                    data: String,
                    shareLabel: String,
                    shareText: String? = data,
                    topText: String? = null,
                    bottomText: String? = null
    ) = createAndPerformSimpleRequest(
            SingleFragmentActivity::class.java,
            SingleFragmentActivity.getBundle(
                    ShareQrFragment.ID,
                    ShareQrFragment.getBundle(data, title, shareLabel, shareText, topText, bottomText)
            )
    )

    fun openPasswordChange() = createAndPerformSimpleRequest(
            ChangePasswordActivity::class.java
    )

    fun openWithdrawalConfirmation(withdrawalRequest: WithdrawalRequest
    ) = createAndPerformSimpleRequest(
            WithdrawalConfirmationActivity::class.java,
            WithdrawalConfirmationActivity.getBundle(withdrawalRequest)
    )

    fun openSend(balanceId: String? = null,
                 amount: BigDecimal? = null,
                 recipientAccount: String? = null,
                 recipientNickname: String? = null
    ) = createAndPerformSimpleRequest(
            SingleFragmentActivity::class.java,
            SingleFragmentActivity.getBundle(
                    SendFragment.ID,
                    SendFragment.getBundle(amount,
                            recipientAccount, recipientNickname, balanceId, true)
            )
    )

    fun openAssetDetails(asset: AssetRecord,
                         cardView: View? = null): ActivityRequest<Unit> {
        val transitionBundle = activity?.let {
            createTransitionBundle(it,
                    cardView to it.getString(R.string.transition_asset_card)
            )
        } ?: fragment?.let {
            createTransitionBundle(it.requireActivity(),
                    cardView to it.getString(R.string.transition_asset_card)
            )
        }

        return createAndPerformSimpleRequest(
                AssetDetailsActivity::class.java,
                AssetDetailsActivity.getBundle(asset),
                transitionBundle = transitionBundle
        )
    }

    fun openPaymentConfirmation(paymentRequest: PaymentRequest
    ) = createAndPerformSimpleRequest(
            PaymentConfirmationActivity::class.java,
            PaymentConfirmationActivity.getBundle(paymentRequest)
    )

    fun openOfferConfirmation(offerRequest: OfferRequest
    ) = createAndPerformSimpleRequest(
            OfferConfirmationActivity::class.java,
            OfferConfirmationActivity.getBundle(offerRequest)
    )

    fun openInvestmentConfirmation(investmentRequest: OfferRequest,
                                   displayToReceive: Boolean = true,
                                   saleName: String? = null
    ) = createAndPerformSimpleRequest(
            InvestmentConfirmationActivity::class.java,
            InvestmentConfirmationActivity
                    .getBundle(investmentRequest, displayToReceive, saleName)
    )

    fun openPendingOffers(onlyPrimary: Boolean = false
    ) = createAndPerformSimpleRequest(
            OffersActivity::class.java,
            OffersActivity.getBundle(onlyPrimary)
    )

    fun openSale(sale: SaleRecord) = createAndPerformSimpleRequest(
            SaleActivity::class.java,
            SaleActivity.getBundle(sale)
    )

    fun openBalanceChangeDetails(change: BalanceChange) {
        val activityClass = when (change.cause) {
            is BalanceChangeCause.AmlAlert -> AmlAlertDetailsActivity::class.java
            is BalanceChangeCause.Investment -> InvestmentDetailsActivity::class.java
            is BalanceChangeCause.MatchedOffer -> OfferMatchDetailsActivity::class.java
            is BalanceChangeCause.Issuance -> IssuanceDetailsActivity::class.java
            is BalanceChangeCause.Payment -> PaymentDetailsActivity::class.java
            is BalanceChangeCause.WithdrawalRequest -> WithdrawalDetailsActivity::class.java
            is BalanceChangeCause.Offer -> {
                openPendingOfferDetails(OfferRecord.fromBalanceChange(change))
                return
            }
            is BalanceChangeCause.AssetPairUpdate -> AssetPairUpdateDetailsActivity::class.java
            else -> DefaultBalanceChangeDetailsActivity::class.java
        }

        createAndPerformIntent(
                activityClass,
                BalanceChangeDetailsActivity.getBundle(change)
        )
    }

    fun openPendingOfferDetails(offer: OfferRecord) {
        val activityClass =
                if (offer.isInvestment)
                    PendingInvestmentDetailsActivity::class.java
                else
                    PendingOfferDetailsActivity::class.java

        createAndPerformIntent(
                activityClass,
                PendingOfferDetailsActivity.getBundle(offer)
        )
    }

    fun openTrade(assetPair: AssetPairRecord) = createAndPerformIntent(
            TradeActivity::class.java,
            TradeActivity.getBundle(assetPair)
    )

    fun openCreateOffer(baseAsset: Asset,
                        quoteAsset: Asset,
                        requiredPrice: BigDecimal? = null,
                        forcedOfferType: CreateOfferActivity.ForcedOfferType? = null
    ) = createAndPerformIntent(
            CreateOfferActivity::class.java,
            CreateOfferActivity.getBundle(baseAsset, quoteAsset, requiredPrice, forcedOfferType)
    )

    fun openUpdateOffer(prevOffer: OfferRecord) {
        context?.intentFor<UpdateOfferActivity>()
                ?.putExtras(UpdateOfferActivity.getBundle(prevOffer))
                ?.also { performIntent(it) }
    }

    fun openLimits() = createAndPerformIntent(
            LimitsActivity::class.java
    )

    fun openFees(asset: String? = null,
                 feeType: Int = -1) = createAndPerformIntent(
            FeesActivity::class.java,
            FeesActivity.getBundle(asset, feeType)
    )

    fun openDeposit(asset: String) = createAndPerformSimpleRequest(
            SingleFragmentActivity::class.java,
            SingleFragmentActivity.getBundle(
                    DepositFragment.ID,
                    DepositFragment.getBundle(asset)
            )
    )

    fun openWithdraw(balanceId: String) = createAndPerformSimpleRequest(
            SingleFragmentActivity::class.java,
            SingleFragmentActivity.getBundle(
                    WithdrawFragment.ID,
                    WithdrawFragment.getBundle(balanceId)
            )
    )

    fun openInvest(sale: SaleRecord) = createAndPerformIntent(
            SaleInvestActivity::class.java,
            SaleInvestActivity.getBundle(sale)
    )

    fun openAssetsExplorer() = createAndPerformIntent(
            SingleFragmentActivity::class.java,
            SingleFragmentActivity.getBundle(
                    ExploreAssetsFragment.ID,
                    null
            )
    )

    fun openBalanceDetails(balanceId: String) = createAndPerformIntent(
            BalanceDetailsActivity::class.java,
            BalanceDetailsActivity.getBundle(balanceId)
    )

    fun openSimpleBalanceDetails(balanceId: String) = createAndPerformIntent(
            SimpleBalanceDetailsActivity::class.java,
            SimpleBalanceDetailsActivity.getBundle(balanceId)
    )

    fun openAccountQrShare(useAccountId: Boolean = false) = createAndPerformIntent(
            SingleFragmentActivity::class.java,
            SingleFragmentActivity.getBundle(
                    AccountDetailsFragment.ID,
                    AccountDetailsFragment.getBundle(useAccountId)
            )
    )

    fun openMarketplaceBuy(offer: MarketplaceOfferRecord,
                           amount: BigDecimal? = null
    ) = createAndPerformSimpleRequest(
            BuyAssetOnMarketplaceActivity::class.java,
            BuyAssetOnMarketplaceActivity.getBundle(
                    offer,
                    amount
            )
    )

    fun openAtomicSwapsAsks(assetCode: String) = createAndPerformIntent(
            SingleFragmentActivity::class.java,
            SingleFragmentActivity.getBundle(
                    AtomicSwapAsksFragment.ID,
                    AtomicSwapAsksFragment.getBundle(assetCode)
            )
    )

    fun openRedemptionCreation(assetCode: String? = null) = createAndPerformIntent(
            CreateRedemptionActivity::class.java,
            CreateRedemptionActivity.getBundle(assetCode)
    )

    fun openScanRedemption() = createAndPerformIntent(
            ScanRedemptionActivity::class.java
    )

    fun openRedemptionRequestConfirmation(balanceId: String,
                                          serializedRequest: String
    ) = createAndPerformSimpleRequest(
            ConfirmRedemptionActivity::class.java,
            ConfirmRedemptionActivity.getBundle(balanceId, serializedRequest)
    )

    fun openCompanyClientDetails(client: CompanyClientRecord) = createAndPerformIntent(
            CompanyClientDetailsActivity::class.java,
            CompanyClientDetailsActivity.getBundle(client)
    )

    fun openInvitation() = createAndPerformIntent(
            InviteNewUsersActivity::class.java
    )

    fun openMassIssuance(emails: String? = null,
                         assetCode: String? = null
    ) = createAndPerformSimpleRequest(
            MassIssuanceActivity::class.java,
            MassIssuanceActivity.getBundle(emails, assetCode)
    )

    fun toForcingAccountType(accountType: ForcedAccountType) {
        createAndPerformIntent(
                ForceAccountTypeActivity::class.java,
                ForceAccountTypeActivity.getBundle(accountType)
        )
        activity?.finish()
    }

    fun openCompanyClientMovements(client: CompanyClientRecord,
                                   assetCode: String
    ) = createAndPerformIntent(
            CompanyClientMovementsActivity::class.java,
            CompanyClientMovementsActivity.getBundle(client, assetCode)
    )

    fun openPhoneNumberSettings() = createAndPerformIntent(
            PhoneNumberSettingsActivity::class.java
    )

    fun openTelegramUsernameSettings() = createAndPerformIntent(
            TelegramUsernameSettingsActivity::class.java
    )

    fun toClientKyc() {
        createAndPerformIntent(ClientKycActivity::class.java)
        activity?.finish()
    }

    fun toWaitingForKycApproval() {
        createAndPerformIntent(WaitForKycApprovalActivity::class.java)
        activity?.also(this::fadeOut)
    }

    fun openMassIssuanceConfirmation(massIssuanceRequest: MassIssuanceRequest
    ) = createAndPerformSimpleRequest(
            MassIssuanceConfirmationActivity::class.java,
            MassIssuanceConfirmationActivity.getBundle(massIssuanceRequest)
    )

    fun performPostSignInRouting(activeKyc: ActiveKyc?) {
        val kycForm = (activeKyc as? ActiveKyc.Form)?.formData

        when (kycForm) {
            is KycForm.Corporate -> {
                toCorporateMainActivity()
            }
            is KycForm.General -> {
                toClientMainActivity()
            }
            else -> {
                toClientKyc()
            }
        }
    }

    fun openSettings() = createAndPerformIntent(
            SingleFragmentActivity::class.java,
            SingleFragmentActivity.getBundle(
                    GeneralSettingsFragment.ID
            )
    )

    fun openRedemptionQrShare(serializedRequest: String,
                              shareText: String,
                              referenceToPoll: String,
                              relatedBalanceId: String?
    ) = createAndPerformIntent(
            SingleFragmentActivity::class.java,
            SingleFragmentActivity.getBundle(
                    ShareRedemptionQrFragment.ID,
                    ShareRedemptionQrFragment.getBundle(
                            serializedRequest,
                            shareText,
                            referenceToPoll,
                            relatedBalanceId
                    )
            )
    )

    fun openWebInvoice(invoiceUrl: String) = createAndPerformSimpleRequest(
            WebInvoiceActivity::class.java,
            WebInvoiceActivity.getBundle(invoiceUrl)
    )

    fun openCompanyDetails(company: CompanyRecord) = createAndPerformIntent(
            CompanyDetailsActivity::class.java,
            CompanyDetailsActivity.getBundle(company)
    )

    fun openShakeToPay() = createAndPerformIntent(
            ShakeToPayActivity::class.java
    )

    fun openLocalAccountSignIn() = createAndPerformSimpleRequest(
            LocalAccountSignInActivity::class.java
    )

    fun openLocalAccountDetails() = createAndPerformIntent(
            LocalAccountDetailsActivity::class.java
    )

    fun openLocalAccountImport() = createAndPerformIntent(
            ImportLocalAccountActivity::class.java
    )

    fun openAssetRefundConfirmation(offerRequest: OfferRequest) = createAndPerformSimpleRequest(
            AssetRefundConfirmationActivity::class.java,
            OfferConfirmationActivity.getBundle(offerRequest)
    )

    fun openNfcPayment(request: RawPosPaymentRequest) = createAndPerformIntent(
            NfcPaymentActivity::class.java,
            NfcPaymentActivity.getBundle(request),
            intentModifier = { clearTop() }
    )

    fun openSimpleRedemptionCreation(balanceId: String,
                                     amount: BigDecimal? = null
    ) = createAndPerformSimpleRequest(
            SingleFragmentActivity::class.java,
            SingleFragmentActivity.getBundle(
                    SimpleRedemptionFragment.ID,
                    SimpleRedemptionFragment.getBundle(balanceId, amount)
            )
    )

    fun openAssetMovements(balanceId: String? = null) = createAndPerformIntent(
            SingleFragmentActivity::class.java,
            SingleFragmentActivity.getBundle(
                    AssetMovementsFragment.ID,
                    AssetMovementsFragment.getBundle(balanceId)
            )
    )

    fun openDepositAmountInput(assetCode: String) = createAndPerformRequest(
            ActivityRequest { intent ->
                intent?.getBigDecimalExtra(DepositAmountActivity.RESULT_AMOUNT_EXTRA)
                        ?.takeIf { it.signum() > 0 }
            },
            DepositAmountActivity::class.java,
            DepositAmountActivity.getBundle(assetCode)
    )
}
