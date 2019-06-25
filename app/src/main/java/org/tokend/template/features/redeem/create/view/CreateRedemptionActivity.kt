package org.tokend.template.features.redeem.create.view

import android.os.Bundle
import android.support.v4.content.ContextCompat
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.activity_balance_details.*
import kotlinx.android.synthetic.main.toolbar.*
import org.tokend.sdk.utils.extentions.encodeBase64String
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.data.model.Asset
import org.tokend.template.data.repository.balances.BalancesRepository
import org.tokend.template.features.amountscreen.model.AmountInputResult
import org.tokend.template.features.redeem.create.logic.CreateRedemptionRequestUseCase
import org.tokend.template.util.Navigator
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.view.util.ProgressDialogFactory
import java.math.BigDecimal

class CreateRedemptionActivity : BaseActivity() {
    private lateinit var assetCode: String
    private lateinit var amount: BigDecimal
    private lateinit var asset: Asset

    private val balancesRepository: BalancesRepository
        get() = repositoryProvider.balances()

    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.fragment_user_flow)

        val errorHandler = errorHandlerFactory.getDefault()

        val assetCode = intent.getStringExtra(ASSET_CODE_EXTRA)
        if (assetCode == null) {
            errorHandler.handle(IllegalArgumentException("No $ASSET_CODE_EXTRA specified"))
            finish()
            return
        }
        this.assetCode = assetCode

        initToolbar()
        initSwipeRefresh()

        toAmountScreen()
    }

    // region Init
    private fun initToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.redeem)
    }

    private fun initSwipeRefresh() {
        swipe_refresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.accent))
        swipe_refresh.setOnRefreshListener { balancesRepository.update() }
    }
    // endregion

    private fun toAmountScreen() {
        val fragment = RedemptionAmountInputFragment.newInstance(assetCode)
        fragment
                .resultObservable
                .map { it as AmountInputResult }
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribeBy(
                        onNext = this::onAmountEntered,
                        onError = { errorHandlerFactory.getDefault().handle(it) }
                )
                .addTo(compositeDisposable)

        supportFragmentManager
                .beginTransaction()
                .disallowAddToBackStack()
                .replace(R.id.fragment_container_layout, fragment)
                .commit()
    }

    private fun onAmountEntered(result: AmountInputResult) {
        this.amount = result.amount
        this.asset = result.asset
        createRedemptionRequest()
    }

    private fun createRedemptionRequest() {
        var disposable: Disposable? = null

        val progress = ProgressDialogFactory.getTunedDialog(this)
        progress.setMessage(getString(R.string.loading_data))
        progress.setCancelable(true)
        progress.setOnCancelListener { disposable?.dispose() }

        disposable = CreateRedemptionRequestUseCase(
                amount,
                assetCode,
                repositoryProvider,
                walletInfoProvider,
                accountProvider
        )
                .perform()
                .compose(ObservableTransformers.defaultSchedulersSingle())
                .doOnSubscribe { progress.show() }
                .doOnEvent { _, _ -> progress.dismiss() }
                .subscribeBy(
                        onSuccess = this::onRedemptionRequestCreated,
                        onError = { errorHandlerFactory.getDefault().handle(it) }
                )
                .addTo(compositeDisposable)
    }

    private fun onRedemptionRequestCreated(result: CreateRedemptionRequestUseCase.Result) {
        val serializedBase64 = result.request
                .serialize(result.networkParams)
                .encodeBase64String()

        val explanation = getString(
                R.string.template_redemption_qr_explanation_amount,
                amountFormatter.formatAssetAmount(amount, asset)
        )

        Navigator.from(this)
                .openQrShare(
                        title = "",
                        data = serializedBase64,
                        shareLabel = getString(R.string.share_redemption_request),
                        shareText = explanation,
                        topText = explanation
                )
        finish()
    }

    companion object {
        const val ASSET_CODE_EXTRA = "asset_code"
    }
}
