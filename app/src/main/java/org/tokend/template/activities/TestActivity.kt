package org.tokend.template.activities

import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v4.graphics.drawable.DrawableCompat
import kotlinx.android.synthetic.main.activity_new_balance_details.*
import kotlinx.android.synthetic.main.toolbar.*
import org.tokend.template.R
import org.tokend.template.data.model.Asset
import org.tokend.template.data.model.SimpleAsset
import org.tokend.template.view.util.CircleLogoUtil
import java.math.BigDecimal

class TestActivity : BaseActivity() {
    override val allowUnauthorized: Boolean
        get() = true

    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_new_balance_details)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        title = null

        swipe_refresh.isEnabled = false

        //val balance = repositoryProvider.balances().itemsList.find { it.asset.name == "Parrot" }!!
        val balance = object {
            val asset: Asset = SimpleAsset(code = "CYCL", name = "New Balance bonus",
                    logoUrl = "https://dgalywyr863hv.cloudfront.net/challenges/5c7b1de1-e080-4b0e-91bb-9f1e24a54caf.png", trailingDigits = 2)
            val available = BigDecimal("405.3")
            val company = object {
                val name = "Mak company"
                val logoUrl = "https://www.mcdonalds.ua/content/ua/_jcr_content/logo/logo.img.png/1571904697424.png"
            }
        }
        val company = balance.company!!

        CircleLogoUtil.setLogo(company_logo_image_view, company.name, company.logoUrl)
        company_name_text_view.text = company.name

        available_text_view.text = amountFormatter.formatAssetAmount(
                balance.available, balance.asset, withAssetCode = false
        )
        asset_name_text_view.text = balance.asset.name
        CircleLogoUtil.setAssetLogo(asset_logo_image_view, balance.asset)
        asset_logo_image_view.setOnClickListener {
            displayDetails()
        }

        amount_view.amountWrapper.setAmount(BigDecimal.ONE)

        val sendDrawable = ContextCompat.getDrawable(this, R.drawable.ic_send_fab)!!
        DrawableCompat.setTint(sendDrawable, ContextCompat.getColor(this, R.color.primary_action))
        send_button.setCompoundDrawablesRelativeWithIntrinsicBounds(sendDrawable, null, null, null)
        send_button.setOnClickListener {
            send(amount_view.amountWrapper.scaledAmount)
        }

        val redeemDrawable = ContextCompat.getDrawable(this, R.drawable.ic_qr_code)!!
        DrawableCompat.setTint(redeemDrawable, ContextCompat.getColor(this, R.color.primary_action))
        redeem_button.setCompoundDrawablesRelativeWithIntrinsicBounds(redeemDrawable, null, null, null)
        redeem_button.setOnClickListener {
            redeem(amount_view.amountWrapper.scaledAmount)
        }
    }

    private fun displayDetails() {
        toastManager.short("Details screen")
    }

    private fun send(amount: BigDecimal) {
        toastManager.short("Send $amount")
    }

    private fun redeem(amount: BigDecimal) {
        toastManager.short("Redeem $amount")
    }
}