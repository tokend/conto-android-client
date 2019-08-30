package org.tokend.template.features.invest.view.fragments

import android.os.Bundle
import android.view.View
import kotlinx.android.synthetic.main.fragment_asset_details.*
import kotlinx.android.synthetic.main.include_appbar_elevation.*
import org.tokend.template.R
import org.tokend.template.extensions.withArguments
import org.tokend.template.features.assets.AssetDetailsFragment
import org.tokend.template.features.invest.logic.InvestmentInfoHolder
import org.tokend.template.features.invest.model.SaleRecord
import org.tokend.template.view.InfoCard
import org.tokend.template.view.util.formatter.DateFormatter

class SaleDetailsFragment : AssetDetailsFragment() {
    private lateinit var sale: SaleRecord

    override fun onInitAllowed() {
        super.onInitAllowed()

        sale = (requireActivity() as? InvestmentInfoHolder)
                ?.sale
                ?: throw IllegalStateException("Parent activity must hold SaleRecord")
    }

    override fun initScrollElevation() {
        appbar_elevation_view.visibility = View.GONE
    }

    override fun displaySummary() {
        val card = InfoCard(cards_layout)
                .setHeading(R.string.sale_summary_title, null)

        card
                .addRow(getString(R.string.sale_info_start_time),
                        DateFormatter(requireContext()).formatCompact(sale.startDate))
                .addRow(getString(R.string.sale_info_close_time),
                        DateFormatter(requireContext()).formatCompact(sale.endDate))
                .addRow(getString(R.string.sale_info_soft_cap),
                        amountFormatter.formatAssetAmount(sale.softCap, sale.defaultQuoteAsset))
                .addRow(getString(R.string.sale_info_hard_cap),
                        amountFormatter.formatAssetAmount(sale.hardCap, sale.defaultQuoteAsset))
                .addRow(getString(R.string.sale_info_to_sell_template, sale.baseAsset.code),
                        amountFormatter.formatAssetAmount(sale.baseHardCap, sale.baseAsset,
                                withAssetCode = false))

        super.displaySummary()
    }

    companion object {
        fun newInstance(bundle: Bundle): SaleDetailsFragment =
                SaleDetailsFragment().withArguments(bundle)

        fun getBundle(saleAssetCode: String) = getBundle(saleAssetCode, false)
    }
}