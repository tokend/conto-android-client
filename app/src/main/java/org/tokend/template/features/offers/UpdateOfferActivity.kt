package org.tokend.template.features.offers

import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.view.Menu
import android.view.MenuItem
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.activity_create_offer.*
import org.tokend.template.R
import org.tokend.template.features.offers.logic.CancelOfferUseCase
import org.tokend.template.features.offers.model.OfferRecord
import org.tokend.template.logic.TxManager
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.view.util.ProgressDialogFactory

class UpdateOfferActivity : CreateOfferActivity() {
    private lateinit var prevOffer: OfferRecord

    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        val prevOffer = intent.getSerializableExtra(PREV_OFFER_EXTRA) as? OfferRecord
        if (prevOffer == null) {
            finishWithMissingArgError(PREV_OFFER_EXTRA)
            return
        }
        this.prevOffer = prevOffer

        super.onCreateAllowed(savedInstanceState)
    }

    override fun preFillFields() {
        price_edit_text.setAmount(requiredPrice, quoteScale)
        amount_edit_text.setAmount(prevOffer.baseAmount, baseScale)
        total_edit_text.setAmount(prevOffer.quoteAmount, quoteScale)
    }

    override fun getOfferToCancel(): OfferRecord? = prevOffer

    override fun getTitleString(): String = getString(R.string.update_offer_title)

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.offer_details, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.cancel_offer -> confirmOfferCancellation()
        }
        return super.onOptionsItemSelected(item)
    }

    // region Cancel
    private fun confirmOfferCancellation() {
        AlertDialog.Builder(this, R.style.AlertDialogStyle)
                .setMessage(R.string.cancel_offer_confirmation)
                .setPositiveButton(R.string.yes) { _, _ ->
                    cancelOffer()
                }
                .setNegativeButton(R.string.no, null)
                .show()
    }

    private fun cancelOffer() {
        val progress = ProgressDialogFactory.getDialog(this)

        CancelOfferUseCase(
                prevOffer,
                repositoryProvider,
                accountProvider,
                TxManager(apiProvider)
        )
                .perform()
                .compose(ObservableTransformers.defaultSchedulersCompletable())
                .doOnSubscribe { progress.show() }
                .doOnTerminate { progress.dismiss() }
                .subscribeBy(
                        onComplete = this::onOfferCanceled,
                        onError = { errorHandlerFactory.getDefault().handle(it) }
                )
    }

    private fun onOfferCanceled() {
        toastManager.short(R.string.offer_canceled)
        finish()
    }
    // endregion

    companion object {
        private const val PREV_OFFER_EXTRA = "prev_offer"

        fun getBundle(prevOffer: OfferRecord) = Bundle().apply {
            putSerializable(PREV_OFFER_EXTRA, prevOffer)
            putAll(
                    getBundle(
                            baseAsset = prevOffer.baseAsset,
                            quoteAsset = prevOffer.quoteAsset,
                            requiredPrice = prevOffer.price,
                            forcedOfferType =
                            if (prevOffer.isBuy)
                                ForcedOfferType.BUY
                            else
                                ForcedOfferType.SELL
                    )
            )
        }
    }
}