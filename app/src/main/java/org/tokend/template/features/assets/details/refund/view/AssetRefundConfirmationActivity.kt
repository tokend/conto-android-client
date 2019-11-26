package org.tokend.template.features.assets.details.refund.view

import org.tokend.template.R
import org.tokend.template.features.offers.OfferConfirmationActivity

class AssetRefundConfirmationActivity: OfferConfirmationActivity() {
    override fun displayDetails() {
        super.displayDetails()
        mainDataView.displayOperationName(getString(R.string.asset_refund))
    }

    override fun getSuccessMessage(): String {
        return getString(R.string.asset_refund_completed)
    }
}