package org.tokend.template.features.assets.details.view

import android.os.Bundle
import kotlinx.android.synthetic.main.toolbar.*
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.data.model.AssetRecord

class AssetDetailsActivity : BaseActivity() {

    private lateinit var asset: AssetRecord

    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_asset_details)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        asset = (intent.getSerializableExtra(ASSET_EXTRA) as? AssetRecord)
                ?: return

        setTitle(R.string.asset_details)

        supportPostponeEnterTransition()
        setFragment()
    }

    private fun setFragment() {
        val fragment = AssetDetailsFragment.newInstance(AssetDetailsFragment.getBundle(
                asset = asset,
                balanceCreation = false
        ))
        supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container_layout, fragment)
                .commit()
    }

    companion object {
        private const val ASSET_EXTRA = "asset"

        fun getBundle(asset: AssetRecord) = Bundle().apply {
            putSerializable(ASSET_EXTRA, asset)
        }
    }
}
