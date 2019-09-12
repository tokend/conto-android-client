package org.tokend.template.features.shaketopay.view

import android.os.Bundle
import kotlinx.android.synthetic.main.toolbar.*
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity

class ShakeToPayActivity : BaseActivity() {
    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_shake_to_pay)

        initToolbar()
    }

    private fun initToolbar() {
        setSupportActionBar(toolbar)
        title = ""
        toolbar.setNavigationIcon(R.drawable.ic_close)
    }
}
