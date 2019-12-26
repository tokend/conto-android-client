package org.tokend.template.features.assets.details.view

import android.os.Bundle
import android.support.design.widget.TabLayout
import kotlinx.android.synthetic.main.appbar_with_tabs.*
import kotlinx.android.synthetic.main.fragment_pager.*
import kotlinx.android.synthetic.main.toolbar.*
import org.tokend.template.BuildConfig
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.data.model.AssetRecord
import org.tokend.template.view.util.input.SoftInputUtil

class AssetDetailsActivity : BaseActivity() {

    private lateinit var asset: AssetRecord

    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.fragment_pager)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        asset = (intent.getSerializableExtra(ASSET_EXTRA) as? AssetRecord)
                ?: return

        setTitle(R.string.asset_details)

        initViewPager()
    }

    private fun initViewPager() {
        val withSecondaryMarketTab = BuildConfig.IS_ASSET_SECONDARY_MARKET_ALLOWED

        val withRefundTab = repositoryProvider.balances().itemsList.find {
            it.assetCode == asset.code
        } != null && BuildConfig.IS_ASSET_SECONDARY_MARKET_ALLOWED

        val adapter = AssetDetailsPagerAdapter(asset, withRefundTab, withSecondaryMarketTab,
                this, supportFragmentManager)

        pager.adapter = adapter
        appbar_tabs.setupWithViewPager(pager)
        pager.offscreenPageLimit = adapter.count

        appbar_tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabReselected(p0: TabLayout.Tab?) {}

            override fun onTabSelected(p0: TabLayout.Tab?) {}

            override fun onTabUnselected(tab: TabLayout.Tab) {
                if (withRefundTab &&
                        adapter.getItemId(tab.position) == AssetDetailsPagerAdapter.REFUND_PAGE) {
                    appbar_tabs.post { SoftInputUtil.hideSoftInput(this@AssetDetailsActivity) }
                }
            }
        })
    }

    companion object {
        private const val ASSET_EXTRA = "asset"

        fun getBundle(asset: AssetRecord) = Bundle().apply {
            putSerializable(ASSET_EXTRA, asset)
        }
    }
}
