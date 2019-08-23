package org.tokend.template.util

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
import org.tokend.sdk.keyserver.models.WalletInfo
import org.tokend.template.R
import org.tokend.template.activities.CorporateMainActivity
import org.tokend.template.activities.MainActivity
import org.tokend.template.activities.SingleFragmentActivity
import org.tokend.template.data.model.Asset
import org.tokend.template.data.model.AssetPairRecord
import org.tokend.template.data.model.AssetRecord
import org.tokend.template.data.model.CompanyRecord
import org.tokend.template.data.model.history.BalanceChange
import org.tokend.template.data.model.history.details.BalanceChangeCause
import org.tokend.template.features.assets.AssetDetailsActivity
import org.tokend.template.features.assets.ExploreAssetsFragment
import org.tokend.template.features.assets.buy.BuyWithAtomicSwapActivity
import org.tokend.template.features.assets.buy.view.AtomicSwapAsksFragment
import org.tokend.template.features.changepassword.ChangePasswordActivity
import org.tokend.template.features.clients.details.movements.view.CompanyClientMovementsActivity
import org.tokend.template.features.clients.details.view.CompanyClientDetailsActivity
import org.tokend.template.features.clients.model.CompanyClientRecord
import org.tokend.template.features.companies.add.view.AddCompanyActivity
import org.tokend.template.features.companies.add.view.AddCompanyConfirmationActivity
import org.tokend.template.features.companies.view.CompaniesActivity
import org.tokend.template.features.companies.view.CompanyLoadingActivity
import org.tokend.template.features.companies.view.ExploreCompaniesActivity
import org.tokend.template.features.deposit.DepositFragment
import org.tokend.template.features.fees.view.FeesActivity
import org.tokend.template.features.invest.model.SaleRecord
import org.tokend.template.features.invest.view.InvestmentConfirmationActivity
import org.tokend.template.features.invest.view.SaleActivity
import org.tokend.template.features.invest.view.SaleInvestActivity
import org.tokend.template.features.invites.view.InviteNewUsersActivity
import org.tokend.template.features.kyc.model.KycForm
import org.tokend.template.features.kyc.model.KycState
import org.tokend.template.features.kyc.view.ClientKycActivity
import org.tokend.template.features.kyc.view.WaitForKycApprovalActivity
import org.tokend.template.features.limits.LimitsActivity
import org.tokend.template.features.massissuance.model.MassIssuanceRequest
import org.tokend.template.features.massissuance.view.MassIssuanceActivity
import org.tokend.template.features.massissuance.view.MassIssuanceConfirmationActivity
import org.tokend.template.features.offers.CreateOfferActivity
import org.tokend.template.features.offers.OfferConfirmationActivity
import org.tokend.template.features.offers.OffersActivity
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
import org.tokend.template.features.send.PaymentConfirmationActivity
import org.tokend.template.features.send.SendFragment
import org.tokend.template.features.send.model.PaymentRequest
import org.tokend.template.features.settings.SettingsFragment
import org.tokend.template.features.settings.phonenumber.view.PhoneNumberSettingsActivity
import org.tokend.template.features.settings.telegram.view.TelegramUsernameSettingsActivity
import org.tokend.template.features.signin.AuthenticatorSignInActivity
import org.tokend.template.features.signin.ForceAccountTypeActivity
import org.tokend.template.features.signin.SignInActivity
import org.tokend.template.features.signin.UnlockAppActivity
import org.tokend.template.features.signin.model.ForcedAccountType
import org.tokend.template.features.signup.SignUpActivity
import org.tokend.template.features.trade.TradeActivity
import org.tokend.template.features.wallet.details.*
import org.tokend.template.features.wallet.view.BalanceDetailsActivity
import org.tokend.template.features.withdraw.WithdrawFragment
import org.tokend.template.features.withdraw.WithdrawalConfirmationActivity
import org.tokend.template.features.withdraw.model.WithdrawalRequest
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
                    it.startActivityForResult(intent, requestCode, bundle ?: Bundle.EMPTY)
                } else {
                    it.startActivity(intent, bundle ?: Bundle.EMPTY)
                }
                return
            }

            fragment?.let {
                if (requestCode != null) {
                    it.startActivityForResult(intent, requestCode, bundle ?: Bundle.EMPTY)
                } else {
                    it.startActivity(intent, bundle ?: Bundle.EMPTY)
                }
                return
            }

            context?.startActivity(intent.newTask(), bundle ?: Bundle.EMPTY)
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

    fun openRecovery(email: String? = null) {
        context?.intentFor<RecoveryActivity>()
                ?.putExtras(RecoveryActivity.getBundle(email))
                ?.also { performIntent(it) }
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

    fun toMainActivity() {
        context?.intentFor<MainActivity>()
                ?.also { performIntent(it) }
        activity?.let { fadeOut(it) }
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
                    bottomText: String? = null,
                    requestCode: Int = 0) {

        context?.intentFor<SingleFragmentActivity>()
                ?.putExtras(SingleFragmentActivity.getBundle(
                        ShareQrFragment.ID,
                        ShareQrFragment.getBundle(data, title, shareLabel, shareText, topText, bottomText)
                ))
                ?.also { performIntent(it, requestCode = requestCode) }
    }

    fun openPasswordChange(requestCode: Int) {
        context?.intentFor<ChangePasswordActivity>()
                ?.also { performIntent(it, requestCode = requestCode) }
    }

    fun openWithdrawalConfirmation(requestCode: Int,
                                   withdrawalRequest: WithdrawalRequest) {
        context?.intentFor<WithdrawalConfirmationActivity>()
                ?.putExtras(WithdrawalConfirmationActivity.getBundle(withdrawalRequest))
                ?.also { performIntent(it, requestCode = requestCode) }
    }

    fun openSend(asset: String? = null) {
        context?.intentFor<SingleFragmentActivity>()
                ?.putExtras(SingleFragmentActivity.getBundle(
                        SendFragment.ID,
                        SendFragment.getBundle(asset, true)
                ))
                ?.also { performIntent(it) }
    }

    fun openAssetDetails(requestCode: Int,
                         asset: AssetRecord,
                         cardView: View? = null) {
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
                ?.also { performIntent(it, requestCode, transitionBundle) }
    }

    fun openPaymentConfirmation(requestCode: Int, paymentRequest: PaymentRequest) {
        context?.intentFor<PaymentConfirmationActivity>()
                ?.putExtras(PaymentConfirmationActivity.getBundle(paymentRequest))
                ?.also { performIntent(it, requestCode = requestCode) }
    }

    fun openOfferConfirmation(requestCode: Int, request: OfferRequest) {
        context?.intentFor<OfferConfirmationActivity>()
                ?.putExtras(OfferConfirmationActivity.getBundle(request))
                ?.also { performIntent(it, requestCode = requestCode) }
    }

    fun openInvestmentConfirmation(requestCode: Int,
                                   request: OfferRequest,
                                   displayToReceive: Boolean = true,
                                   saleName: String? = null) {
        context?.intentFor<InvestmentConfirmationActivity>()
                ?.putExtras(InvestmentConfirmationActivity
                        .getBundle(request, displayToReceive, saleName))
                ?.also { performIntent(it, requestCode = requestCode) }
    }

    fun openPendingOffers(requestCode: Int = 0, onlyPrimary: Boolean = false) {
        context?.intentFor<OffersActivity>()
                ?.putExtras(OffersActivity.getBundle(onlyPrimary))
                ?.also { performIntent(it, requestCode = requestCode) }
    }

    fun openSale(requestCode: Int, sale: SaleRecord) {
        context?.intentFor<SaleActivity>()
                ?.putExtras(SaleActivity.getBundle(sale))
                ?.also { performIntent(it, requestCode) }
    }

    fun openAuthenticatorSignIn(requestCode: Int) {
        context?.intentFor<AuthenticatorSignInActivity>()
                ?.also { performIntent(it, requestCode = requestCode) }
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

    fun openPendingOfferDetails(offer: OfferRecord,
                                requestCode: Int = 0) {
        val activityClass =
                if (offer.isInvestment)
                    PendingInvestmentDetailsActivity::class.java
                else
                    PendingOfferDetailsActivity::class.java

        Intent(context, activityClass)
                .putExtras(PendingOfferDetailsActivity.getBundle(offer))
                .also { performIntent(it, requestCode = requestCode) }
    }

    fun openTrade(assetPair: AssetPairRecord) {
        context?.intentFor<TradeActivity>()
                ?.putExtras(TradeActivity.getBundle(assetPair))
                ?.also { performIntent(it) }
    }

    fun openCreateOffer(baseAsset: Asset,
                        quoteAsset: Asset,
                        requiredPrice: BigDecimal? = null) {
        context?.intentFor<CreateOfferActivity>()
                ?.putExtras(CreateOfferActivity.getBundle(baseAsset, quoteAsset, requiredPrice))
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

    fun openDeposit(requestCode: Int, asset: String) {
        context?.intentFor<SingleFragmentActivity>()
                ?.putExtras(SingleFragmentActivity.getBundle(
                        DepositFragment.ID,
                        DepositFragment.getBundle(asset)
                ))
                ?.also { performIntent(it, requestCode = requestCode) }
    }

    fun openWithdraw(requestCode: Int, asset: String) {
        context?.intentFor<SingleFragmentActivity>()
                ?.putExtras(SingleFragmentActivity.getBundle(
                        WithdrawFragment.ID,
                        WithdrawFragment.getBundle(asset)
                ))
                ?.also { performIntent(it, requestCode = requestCode) }
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

    fun openAccountQrShare(walletInfo: WalletInfo,
                           useAccountId: Boolean = false) {
        openQrShare(
                data = if (useAccountId) walletInfo.accountId else walletInfo.email,
                title = context!!.getString(R.string.account_title),
                shareLabel = context!!.getString(R.string.share_account)
        )
    }

    fun openAtomicSwapBuy(assetCode: String,
                          askId: String,
                          requestCode: Int = 0) {
        context?.intentFor<BuyWithAtomicSwapActivity>()
                ?.putExtras(BuyWithAtomicSwapActivity.getBundle(assetCode, askId))
                ?.also { performIntent(it, requestCode = requestCode) }
    }

    fun openAtomicSwapsAsks(assetCode: String) {
        context?.intentFor<SingleFragmentActivity>()
                ?.putExtras(SingleFragmentActivity.getBundle(
                        AtomicSwapAsksFragment.ID,
                        AtomicSwapAsksFragment.getBundle(assetCode)
                ))
                ?.also { performIntent(it) }
    }

    fun toCompaniesActivity() {
        context?.intentFor<CompaniesActivity>()
                ?.also { performIntent(it) }
        activity?.let { fadeOut(it) }
    }

    fun toCompanyLoading(finishAffinity: Boolean = false) {
        context?.intentFor<CompanyLoadingActivity>()
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

    fun openRedemptionCreation(assetCode: String? = null) {
        context?.intentFor<CreateRedemptionActivity>()
                ?.putExtras(CreateRedemptionActivity.getBundle(assetCode))
                ?.also { performIntent(it) }
    }

    fun openScanRedemption() {
        context?.intentFor<ScanRedemptionActivity>()
                ?.also { performIntent(it) }
    }

    fun openRedemptionRequestConfirmation(balanceId: String, request: String, requestCode: Int? = null) {
        context?.intentFor<ConfirmRedemptionActivity>()
                ?.putExtras(ConfirmRedemptionActivity.getBundle(balanceId, request))
                ?.also { performIntent(it, requestCode = requestCode) }
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
                         assetCode: String? = null,
                         requestCode: Int = 0) {
        context?.intentFor<MassIssuanceActivity>()
                ?.putExtras(MassIssuanceActivity.getBundle(emails, assetCode))
                ?.also { performIntent(it, requestCode = requestCode) }
    }

    fun openCompanyAdd() {
        context?.intentFor<AddCompanyActivity>()
                ?.also { performIntent(it) }
    }

    fun openCompanyAddConfirmation(company: CompanyRecord,
                                   requestCode: Int = 0) {
        context?.intentFor<AddCompanyConfirmationActivity>()
                ?.putExtras(AddCompanyConfirmationActivity.getBundle(company))
                ?.also { performIntent(it, requestCode = requestCode) }
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

    fun openExploreCompanies() {
        context?.intentFor<ExploreCompaniesActivity>()
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

    fun openMassIssuanceConfirmation(requestCode: Int, request: MassIssuanceRequest) {
        context?.intentFor<MassIssuanceConfirmationActivity>()
                ?.putExtras(MassIssuanceConfirmationActivity.getBundle(request))
                ?.also { performIntent(it, requestCode = requestCode) }
    }

    fun performPostSignInRouting(kycState: KycState?) {
        val kycForm = (kycState as? KycState.Submitted<*>)?.formData

        if (kycForm is KycForm.Corporate) {
            toCorporateMainActivity()
        } else if (kycForm is KycForm.General) {
            if (kycState is KycState.Submitted.Pending<*>) {
                toWaitingForKycApproval()
            } else {
                toCompaniesActivity()
            }
        } else {
            toClientKyc()
        }
    }

    fun openSettings() {
        context?.intentFor<SingleFragmentActivity>()
                ?.putExtras(SingleFragmentActivity.getBundle(
                        SettingsFragment.ID
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
}
