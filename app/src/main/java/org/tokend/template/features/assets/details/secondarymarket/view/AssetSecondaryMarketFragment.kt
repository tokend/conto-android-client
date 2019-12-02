package org.tokend.template.features.assets.details.secondarymarket.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.tokend.template.R
import org.tokend.template.fragments.BaseFragment

class AssetSecondaryMarketFragment: BaseFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_asset_secondary_market, container, false)
    }

    override fun onInitAllowed() {
    }

    companion object {
        fun newInstance() = AssetSecondaryMarketFragment()
    }
}