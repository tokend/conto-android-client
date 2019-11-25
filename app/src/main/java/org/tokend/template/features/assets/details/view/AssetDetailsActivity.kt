package org.tokend.template.features.assets.details.view

import android.os.Bundle
import kotlinx.android.synthetic.main.appbar_with_tabs.*
import kotlinx.android.synthetic.main.fragment_pager.*
import kotlinx.android.synthetic.main.toolbar.*
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.data.model.AssetRecord

class AssetDetailsActivity : BaseActivity() {

    private lateinit var asset: AssetRecord

    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.fragment_pager)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        asset = (intent.getSerializableExtra(ASSET_EXTRA) as? AssetRecord)
                ?: return

        setTitle(R.string.asset_details)

        supportPostponeEnterTransition()

        initViewPager()
    }

    private fun initViewPager() {
        val adapter = AssetDetailsPagerAdapter(asset, this, supportFragmentManager)
        pager.adapter = adapter
        appbar_tabs.setupWithViewPager(pager)
        pager.offscreenPageLimit = adapter.count
    }

    companion object {
        private const val ASSET_EXTRA = "asset"

        fun getBundle(asset: AssetRecord) = Bundle().apply {
            putSerializable(ASSET_EXTRA, asset)
        }
    }
}
