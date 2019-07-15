package org.tokend.template.features.clients.details.view

import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.activity_company_client_details.*
import kotlinx.android.synthetic.main.include_appbar_elevation.*
import kotlinx.android.synthetic.main.toolbar.*
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.features.clients.model.CompanyClientRecord
import org.tokend.template.view.details.DetailsItem
import org.tokend.template.view.details.adapter.DetailsItemsAdapter
import org.tokend.template.view.util.CopyDataDialogFactory
import org.tokend.template.view.util.ElevationUtil
import org.tokend.template.view.util.LocalizedName

class CompanyClientDetailsActivity : BaseActivity() {
    private val adapter = DetailsItemsAdapter()
    private lateinit var client: CompanyClientRecord

    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_company_client_details)

        val client = (intent.getSerializableExtra(CLIENT_EXTRA) as? CompanyClientRecord)
        if (client == null) {
            errorHandlerFactory.getDefault().handle(IllegalArgumentException("Invalid $CLIENT_EXTRA"))
            finish()
            return
        }
        this.client = client

        initToolbar()
        initList()

        displayDetails()
    }

    // region Init
    private fun initToolbar() {
        setSupportActionBar(toolbar)
        setTitle(R.string.company_client_info_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun initList() {
        details_list.layoutManager = LinearLayoutManager(this)
        details_list.adapter = adapter

        adapter.onItemClick { _, item ->
            when (item.id) {
                CLIENT_EMAIL_ITEM_ID -> showEmailCopyDialog()
            }
        }

        ElevationUtil.initScrollElevation(details_list, appbar_elevation_view)
    }
    // endregion

    private fun displayDetails() {
        adapter.addData(
                DetailsItem(
                        header = getString(R.string.company_client_main_info),
                        hint = getString(R.string.email),
                        text = client.email,
                        icon = ContextCompat.getDrawable(this, R.drawable.ic_email),
                        id = CLIENT_EMAIL_ITEM_ID
                ),
                DetailsItem(
                        hint = getString(R.string.company_client_status),
                        text = LocalizedName(this).forCompanyClientStatus(client.status),
                        icon = ContextCompat.getDrawable(
                                this,
                                when (client.status) {
                                    CompanyClientRecord.Status.NOT_REGISTERED ->
                                        R.drawable.ic_account_pending_outline
                                    CompanyClientRecord.Status.ACTIVE ->
                                        R.drawable.ic_account_check_outline
                                    CompanyClientRecord.Status.BLOCKED ->
                                        R.drawable.ic_account_blocked_outline
                                }
                        )
                )
        )

        val balanceIcon = ContextCompat.getDrawable(this, R.drawable.ic_coins)

        if (client.balances.isEmpty()) {
            adapter.addData(
                    DetailsItem(
                            header = getString(R.string.company_client_balances),
                            text = getString(R.string.no_balances_found),
                            icon = balanceIcon,
                            isEnabled = false
                    )
            )
        } else {
            adapter.addData(
                    DetailsItem(
                            header = getString(R.string.company_client_balances),
                            text = amountFormatter.formatAssetAmount(
                                    client.balances.first().amount,
                                    client.balances.first().asset
                            ),
                            icon = balanceIcon
                    )
            )
            adapter.addData(
                    client.balances
                            .subList(1, client.balances.size)
                            .map { balance ->
                                DetailsItem(
                                        text = amountFormatter.formatAssetAmount(
                                                balance.amount,
                                                balance.asset
                                        ),
                                        icon = balanceIcon
                                )
                            }
            )
        }
    }

    private fun showEmailCopyDialog() {
        CopyDataDialogFactory.getDialog(
                content = client.email,
                context = this,
                title = getString(R.string.email),
                toastManager = toastManager
        )
    }

    companion object {
        private const val CLIENT_EXTRA = "client"
        private val CLIENT_EMAIL_ITEM_ID = "email".hashCode().toLong()

        fun getBundle(client: CompanyClientRecord) = Bundle().apply {
            putSerializable(CLIENT_EXTRA, client)
        }
    }
}
