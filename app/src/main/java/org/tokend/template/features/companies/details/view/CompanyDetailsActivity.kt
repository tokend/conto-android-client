package org.tokend.template.features.companies.details.view

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.design.widget.TabLayout
import android.view.Menu
import android.view.MenuItem
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.appbar_with_tabs.*
import kotlinx.android.synthetic.main.fragment_pager.*
import kotlinx.android.synthetic.main.toolbar.*
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.data.model.CompanyRecord
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.view.util.ProgressDialogFactory

class CompanyDetailsActivity : BaseActivity() {
    private lateinit var company: CompanyRecord

    private lateinit var adapter: CompanyDetailsPagerAdapter

    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.fragment_pager)

        val company = intent.getSerializableExtra(COMPANY_EXTRA) as? CompanyRecord
        if (company == null) {
            finishWithMissingArgError(COMPANY_EXTRA)
            return
        }
        this.company = company

        initToolbar()
        initPager()
    }

    private fun initToolbar() {
        setSupportActionBar(toolbar)
        title = company.name
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun initPager() {
        adapter = CompanyDetailsPagerAdapter(company.id, this, supportFragmentManager)
        pager.adapter = adapter
        pager.offscreenPageLimit = adapter.count
        appbar_tabs.setupWithViewPager(pager)
        appbar_tabs.tabGravity = TabLayout.GRAVITY_FILL
        appbar_tabs.tabMode = TabLayout.MODE_FIXED

        switchToShopIfNeeded()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.company, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.contact -> contactCompany()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun contactCompany() {
        var disposable: Disposable? = null

        val progress = ProgressDialogFactory.getDialog(this, R.string.loading_data) {
            disposable?.dispose()
        }

        disposable = repositoryProvider
                .accountDetails()
                .getEmailByAccountId(company.id)
                .compose(ObservableTransformers.defaultSchedulersSingle())
                .doOnSubscribe { progress.show() }
                .doOnEvent { _, _ -> progress.dismiss() }
                .subscribeBy(
                        onSuccess = this::openEmailCreation,
                        onError = { errorHandlerFactory.getDefault().handle(it) }
                )
                .addTo(compositeDisposable)
    }

    private fun openEmailCreation(recipient: String) {
        val emailIntent = Intent(Intent.ACTION_SENDTO,
                Uri.fromParts("mailto", recipient, null))
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
        startActivity(Intent.createChooser(emailIntent,
                getString(
                        R.string.template_contact_email,
                        recipient
                )
        ))
    }

    private fun switchToShopIfNeeded() {
        val companyId = company.id

        val hasNonZeroBalance = repositoryProvider
                .balances()
                .itemsList
                .any {
                    it.asset.ownerAccountId == companyId && it.available.signum() > 0
                }

        if (!hasNonZeroBalance) {
            pager.currentItem = adapter.getIndexOf(CompanyDetailsPagerAdapter.SHOP_PAGE)
        }
    }

    companion object {
        private const val COMPANY_EXTRA = "company"

        fun getBundle(company: CompanyRecord) = Bundle().apply {
            putSerializable(COMPANY_EXTRA, company)
        }
    }
}
