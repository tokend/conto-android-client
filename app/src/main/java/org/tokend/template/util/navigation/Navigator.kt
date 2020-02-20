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
import org.tokend.template.features.signin.AuthenticatorSignInActivity
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

    fun openRecovery(email: String? = null
    ) = ActivityRequest.withoutResultData().also { request ->
        context?.intentFor<RecoveryActivity>()
                ?.putExtras(RecoveryActivity.getBundle(email))
                ?.also { performIntent(it, request.code) }
    }

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
    ) = ActivityRequest.withoutResultData().also { request ->
        context?.intentFor<SingleFragmentActivity>()
                ?.putExtras(SingleFragmentActivity.getBundle(
                        ShareQrFragment.ID,
                        ShareQrFragment.getBundle(data, title, shareLabel, shareText, topText, bottomText)
                ))
                ?.also { performIntent(it, request.code) }
    }

    fun openPasswordChange() = ActivityRequest.withoutResultData().also { request ->
        context?.intentFor<ChangePasswordActivity>()
                ?.also { performIntent(it, request.code) }
    }

    fun openWithdrawalConfirmation(withdrawalRequest: WithdrawalRequest
    ) = ActivityRequest.withoutResultData().also { request ->
        context?.intentFor<WithdrawalConfirmationActivity>()
                ?.putExtras(WithdrawalConfirmationActivity.getBundle(withdrawalRequest))
                ?.also { performIntent(it, request.code) }
    }

    fun openSend(asset: String? = null,
                 amount: BigDecimal? = null,
                 recipientAccount: String? = null,
                 recipientNickname: String? = null) = ActivityRequest.withoutResultData().also { request ->
        context?.intentFor<SingleFragmentActivity>()
                ?.putExtras(SingleFragmentActivity.getBundle(
                        SendFragment.ID,
                        SendFragment.getBundle(asset, amount,
                                recipientAccount, recipientNickname, true)
                ))
                ?.also { performIntent(it, request.code) }
    }

    fun openAssetDetails(asset: AssetRecord,
                         cardView: View? = null
    ) = ActivityRequest.withoutResultData().also { request ->
        val transitionBundle = activity?.let {
            createTransitionBundle(it,
                    cardView to it.getString(R.string.transition_asset_card)
            )
        } ?: fragment?.let {
            createTransitionBundle(it.requireActivity(),
                    cardView to it.getString(R.string.transition_asset_card)
            )
        }
        context?.intentFor<AssetDetailsActivity>()
                ?.putExtras(AssetDetailsActivity.getBundle(asset))
                ?.also { performIntent(it, request.code, transitionBundle) }
    }

    fun openPaymentConfirmation(paymentRequest: PaymentRequest
    ) = ActivityRequest.withoutResultData().also { request ->
        context?.intentFor<PaymentConfirmationActivity>()
                ?.putExtras(PaymentConfirmationActivity.getBundle(paymentRequest))
                ?.also { performIntent(it, request.code) }
    }

    fun openOfferConfirmation(offerRequest: OfferRequest
    ) = ActivityRequest.withoutResultData().also { request ->
        context?.intentFor<OfferConfirmationActivity>()
                ?.putExtras(OfferConfirmationActivity.getBundle(offerRequest))
                ?.also { performIntent(it, request.code) }
    }

    fun openInvestmentConfirmation(investmentRequest: OfferRequest,
                                   displayToReceive: Boolean = true,
                                   saleName: String? = null
    ) = ActivityRequest.withoutResultData().also { request ->
        context?.intentFor<InvestmentConfirmationActivity>()
                ?.putExtras(InvestmentConfirmationActivity
                        .getBundle(investmentRequest, displayToReceive, saleName))
                ?.also { performIntent(it, request.code) }
    }

    fun openPendingOffers(onlyPrimary: Boolean = false
    ) = ActivityRequest.withoutResultData().also { request ->
        context?.intentFor<OffersActivity>()
                ?.putExtras(OffersActivity.getBundle(onlyPrimary))
                ?.also { performIntent(it, request.code) }
    }

    fun openSale(sale: SaleRecord) = ActivityRequest.withoutResultData().also { request ->
        context?.intentFor<SaleActivity>()
                ?.putExtras(SaleActivity.getBundle(sale))
                ?.also { performIntent(it, request.code) }
    }

    fun openAuthenticatorSignIn() = ActivityRequest.withoutResultData().also { request ->
        context?.intentFor<AuthenticatorSignInActivity>()
                ?.also { performIntent(it, request.code) }
    }

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

        Intent(context, activityClass)
                .putExtras(BalanceChangeDetailsActivity.getBundle(change))
                .also { performIntent(it) }
    }

    fun openPendingOfferDetails(offer: OfferRecord
    ) = ActivityRequest.withoutResultData().also { request ->
        val activityClass =
                if (offer.isInvestment)
                    PendingInvestmentDetailsActivity::class.java
                else
                    PendingOfferDetailsActivity::class.java

        Intent(context, activityClass)
                .putExtras(PendingOfferDetailsActivity.getBundle(offer))
                .also { performIntent(it, request.code) }
    }

    fun openTrade(assetPair: AssetPairRecord) {
        context?.intentFor<TradeActivity>()
                ?.putExtras(TradeActivity.getBundle(assetPair))
                ?.also { performIntent(it) }
    }

    fun openCreateOffer(baseAsset: Asset,
                        quoteAsset: Asset,
                        requiredPrice: BigDecimal? = null,
                        forcedOfferType: CreateOfferActivity.ForcedOfferType? = null) {
        context?.intentFor<CreateOfferActivity>()
                ?.putExtras(CreateOfferActivity.getBundle(baseAsset, quoteAsset,
                        requiredPrice, forcedOfferType))
                ?.also { performIntent(it) }
    }

    fun openUpdateOffer(prevOffer: OfferRecord) {
        context?.intentFor<UpdateOfferActivity>()
                ?.putExtras(UpdateOfferActivity.getBundle(prevOffer))
                ?.also { performIntent(it) }
    }

    fun openLimits() {
        context?.intentFor<LimitsActivity>()
                ?.also { performIntent(it) }
    }

    fun openFees(asset: String? = null, feeType: Int = -1) {
        context?.intentFor<FeesActivity>()
                ?.putExtras(FeesActivity.getBundle(asset, feeType))
                ?.also { performIntent(it) }
    }

    fun openDeposit(asset: String) = ActivityRequest.withoutResultData().also { request ->
        context?.intentFor<SingleFragmentActivity>()
                ?.putExtras(SingleFragmentActivity.getBundle(
                        DepositFragment.ID,
                        DepositFragment.getBundle(asset)
                ))
                ?.also { performIntent(it, request.code) }
    }

    fun openWithdraw(asset: String) = ActivityRequest.withoutResultData().also { request ->
        context?.intentFor<SingleFragmentActivity>()
                ?.putExtras(SingleFragmentActivity.getBundle(
                        WithdrawFragment.ID,
                        WithdrawFragment.getBundle(asset)
                ))
                ?.also { performIntent(it, request.code) }
    }

    fun openInvest(sale: SaleRecord) {
        context?.intentFor<SaleInvestActivity>()
                ?.putExtras(SaleInvestActivity.getBundle(sale))
                ?.also { performIntent(it) }
    }

    fun openAssetsExplorer() {
        context?.intentFor<SingleFragmentActivity>()
                ?.putExtras(SingleFragmentActivity.getBundle(
                        ExploreAssetsFragment.ID,
                        null
                ))
                ?.also { performIntent(it) }
    }

    fun openBalanceDetails(balanceId: String) {
        context?.intentFor<BalanceDetailsActivity>()
                ?.putExtras(BalanceDetailsActivity.getBundle(balanceId))
                ?.also { performIntent(it) }
    }

    fun openSimpleBalanceDetails(balanceId: String) {
        context?.intentFor<SimpleBalanceDetailsActivity>()
                ?.putExtras(SimpleBalanceDetailsActivity.getBundle(balanceId))
                ?.also { performIntent(it) }
    }

    fun openAccountQrShare(useAccountId: Boolean = false) {
        context?.intentFor<SingleFragmentActivity>()
                ?.putExtras(SingleFragmentActivity.getBundle(
                        AccountDetailsFragment.ID,
                        AccountDetailsFragment.getBundle(useAccountId)
                ))
                ?.also { performIntent(it) }
    }

    fun openMarketplaceBuy(offer: MarketplaceOfferRecord,
                           amount: BigDecimal? = null
    ) = ActivityRequest.withoutResultData().also { request ->
        context?.intentFor<BuyAssetOnMarketplaceActivity>()
                ?.putExtras(BuyAssetOnMarketplaceActivity.getBundle(
                        offer,
                        amount
                ))
                ?.also { performIntent(it, request.code) }
    }

    fun openAtomicSwapsAsks(assetCode: String) {
        context?.intentFor<SingleFragmentActivity>()
                ?.putExtras(SingleFragmentActivity.getBundle(
                        AtomicSwapAsksFragment.ID,
                        AtomicSwapAsksFragment.getBundle(assetCode)
                ))
                ?.also { performIntent(it) }
    }

    fun openRedemptionCreation(assetCode: String? = null) {
        context?.intentFor<CreateRedemptionActivity>()
                ?.putExtras(CreateRedemptionActivity.getBundle(assetCode))
                ?.also { performIntent(it) }
    }

    fun openScanRedemption() {
        context?.intentFor<ScanRedemptionActivity>()
                ?.also { performIntent(it) }
    }

    fun openRedemptionRequestConfirmation(balanceId: String,
                                          serializedRequest: String,
                                          requestCode: Int
    ) = ActivityRequest.withoutResultData(requestCode).also { requset ->
        context?.intentFor<ConfirmRedemptionActivity>()
                ?.putExtras(ConfirmRedemptionActivity.getBundle(balanceId, serializedRequest))
                ?.also { performIntent(it, requset.code) }
    }

    fun openCompanyClientDetails(client: CompanyClientRecord) {
        context?.intentFor<CompanyClientDetailsActivity>()
                ?.putExtras(CompanyClientDetailsActivity.getBundle(client))
                ?.also { performIntent(it) }
    }

    fun openInvitation() {
        context?.intentFor<InviteNewUsersActivity>()
                ?.also { performIntent(it) }
    }

    fun openMassIssuance(emails: String? = null,
                         assetCode: String? = null
    ) = ActivityRequest.withoutResultData().also { request ->
        context?.intentFor<MassIssuanceActivity>()
                ?.putExtras(MassIssuanceActivity.getBundle(emails, assetCode))
                ?.also { performIntent(it, request.code) }
    }

    fun toForcingAccountType(accountType: ForcedAccountType) {
        context?.intentFor<ForceAccountTypeActivity>()
                ?.putExtras(ForceAccountTypeActivity.getBundle(accountType))
                ?.also { performIntent(it) }
        activity?.finish()
    }

    fun openCompanyClientMovements(client: CompanyClientRecord,
                                   assetCode: String) {
        context?.intentFor<CompanyClientMovementsActivity>()
                ?.putExtras(CompanyClientMovementsActivity.getBundle(client, assetCode))
                ?.also { performIntent(it) }
    }

    fun openPhoneNumberSettings() {
        context?.intentFor<PhoneNumberSettingsActivity>()
                ?.also { performIntent(it) }
    }

    fun openTelegramUsernameSettings() {
        context?.intentFor<TelegramUsernameSettingsActivity>()
                ?.also { performIntent(it) }
    }

    fun toClientKyc() {
        context?.intentFor<ClientKycActivity>()
                ?.also { performIntent(it) }
        activity?.finish()
    }

    fun toWaitingForKycApproval() {
        context?.intentFor<WaitForKycApprovalActivity>()
                ?.also { performIntent(it) }
        activity?.also(this::fadeOut)
    }

    fun openMassIssuanceConfirmation(massIssuanceRequest: MassIssuanceRequest
    ) = ActivityRequest.withoutResultData().also { request ->
        context?.intentFor<MassIssuanceConfirmationActivity>()
                ?.putExtras(MassIssuanceConfirmationActivity.getBundle(massIssuanceRequest))
                ?.also { performIntent(it, request.code) }
    }

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

    fun openSettings() {
        context?.intentFor<SingleFragmentActivity>()
                ?.putExtras(SingleFragmentActivity.getBundle(
                        GeneralSettingsFragment.ID
                ))
                ?.also { performIntent(it) }
    }

    fun openRedemptionQrShare(serializedRequest: String,
                              shareText: String,
                              referenceToPoll: String,
                              relatedBalanceId: String?) {
        context?.intentFor<SingleFragmentActivity>()
                ?.putExtras(SingleFragmentActivity.getBundle(
                        ShareRedemptionQrFragment.ID,
                        ShareRedemptionQrFragment.getBundle(
                                serializedRequest,
                                shareText,
                                referenceToPoll,
                                relatedBalanceId
                        )
                ))
                ?.also { performIntent(it) }
    }

    fun openWebInvoice(invoiceUrl: String
    ) = ActivityRequest.withoutResultData().also { request ->
        context?.intentFor<WebInvoiceActivity>()
                ?.putExtras(WebInvoiceActivity.getBundle(invoiceUrl))
                ?.also { performIntent(it, request.code) }
    }

    fun openCompanyDetails(company: CompanyRecord) {
        context?.intentFor<CompanyDetailsActivity>()
                ?.putExtras(CompanyDetailsActivity.getBundle(company))
                ?.also { performIntent(it) }
    }

    fun openShakeToPay() {
        context?.intentFor<ShakeToPayActivity>()
                ?.also { performIntent(it) }
    }

    fun openLocalAccountSignIn() = ActivityRequest.withoutResultData().also { request ->
        context?.intentFor<LocalAccountSignInActivity>()
                ?.also { performIntent(it, request.code) }
    }

    fun openLocalAccountDetails() {
        context?.intentFor<LocalAccountDetailsActivity>()
                ?.also { performIntent(it) }
    }

    fun openLocalAccountImport() {
        context?.intentFor<ImportLocalAccountActivity>()
                ?.also { performIntent(it) }
    }

    fun openAssetRefundConfirmation(offerRequest: OfferRequest
    ) = ActivityRequest.withoutResultData().also { request ->
        context?.intentFor<AssetRefundConfirmationActivity>()
                ?.putExtras(OfferConfirmationActivity.getBundle(offerRequest))
                ?.also { performIntent(it, request.code) }
    }

    fun openNfcPayment(request: RawPosPaymentRequest) {
        context?.intentFor<NfcPaymentActivity>()
                ?.putExtras(NfcPaymentActivity.getBundle(request))
                ?.clearTop()
                ?.also { performIntent(it) }
    }

    fun openSimpleRedemptionCreation(balanceId: String,
                                     amount: BigDecimal? = null
    ) = ActivityRequest.withoutResultData().also { request ->
        context?.intentFor<SingleFragmentActivity>()
                ?.putExtras(SingleFragmentActivity.getBundle(
                        SimpleRedemptionFragment.ID,
                        SimpleRedemptionFragment.getBundle(balanceId, amount)
                ))
                ?.also { performIntent(it, request.code) }
    }

    fun openAssetMovements(balanceId: String? = null) {
        context?.intentFor<SingleFragmentActivity>()
                ?.putExtras(SingleFragmentActivity.getBundle(
                        AssetMovementsFragment.ID,
                        AssetMovementsFragment.getBundle(balanceId)
                ))
                ?.also { performIntent(it) }
    }

    fun openDepositAmountInput(assetCode: String) = ActivityRequest { intent ->
        intent?.getBigDecimalExtra(DepositAmountActivity.RESULT_AMOUNT_EXTRA)
                ?.takeIf { it.signum() > 0 }
    }.also { request ->
        context?.intentFor<DepositAmountActivity>()
                ?.putExtras(DepositAmountActivity.getBundle(assetCode))
                ?.also { performIntent(it, request.code) }
    }
}
