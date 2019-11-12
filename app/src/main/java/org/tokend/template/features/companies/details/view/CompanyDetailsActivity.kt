package org.tokend.template.features.companies.details.view

import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.view.Menu
import android.view.MenuItem
import android.view.View
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.activity_company_details.*
import kotlinx.android.synthetic.main.toolbar.*
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.data.model.CompanyRecord
import org.tokend.template.data.repository.ClientCompaniesRepository
import org.tokend.template.features.companies.details.logic.AddCompanyUseCase
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.view.util.ImageViewUtil
import org.tokend.template.view.util.ProgressDialogFactory
import kotlin.math.min
import kotlin.math.roundToInt

class CompanyDetailsActivity : BaseActivity() {
    private lateinit var company: CompanyRecord

    private val clientCompaniesRepository: ClientCompaniesRepository
        get() = repositoryProvider.clientCompanies()

    private lateinit var adapter: CompanyDetailsPagerAdapter

    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_company_details)

        val company = intent.getSerializableExtra(COMPANY_EXTRA) as? CompanyRecord
        if (company == null) {
            finishWithMissingArgError(COMPANY_EXTRA)
            return
        }
        this.company = company

        initToolbar()
        initPager()
        initButton()
        initBanner()

        subscribeToClientCompanies()
    }

    private fun initToolbar() {
        setSupportActionBar(toolbar)
        title = company.name
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun initPager() {
        adapter = CompanyDetailsPagerAdapter(company, this, supportFragmentManager)
        pager.adapter = adapter
        pager.offscreenPageLimit = adapter.count
        appbar_tabs.setupWithViewPager(pager)

        switchToShopIfNeeded()
    }

    private fun initButton() {
        add_button.setOnClickListener {
            addCompany()
        }
    }

    private fun initBanner() {
        if (company.bannerUrl != null) {
            updateBannerSize()
            ImageViewUtil.loadImage(
                    company_banner_image_view,
                    company.bannerUrl,
                    ColorDrawable(ContextCompat.getColor(this, R.color.imagePlaceholder))
            )
        } else {
            company_banner_image_view.visibility = View.GONE
        }
    }

    private fun updateBannerSize() {
        val maxWidth = resources.getDimensionPixelSize(R.dimen.max_content_width)
        company_banner_image_view.apply { post {
            val width = min(pager.width, maxWidth)
            val height = (width.toDouble() / 3).roundToInt()

            layoutParams = layoutParams.apply {
                this.width = width
                this.height = height
            }
        } }
    }

    private fun subscribeToClientCompanies() {
        clientCompaniesRepository
                .itemsSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { onClientCompaniesUpdated() }
                .addTo(compositeDisposable)
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
                    it.asset.isOwnedBy(companyId) && it.hasAvailableAmount
                }

        if (!hasNonZeroBalance) {
            pager.currentItem = adapter.getIndexOf(CompanyDetailsPagerAdapter.SHOP_PAGE)
        }
    }

    private fun onClientCompaniesUpdated() {
        updateAddButtonVisibility()
    }

    private fun updateAddButtonVisibility() {
        if (clientCompaniesRepository.itemsList.contains(company)) {
            add_button.visibility = View.GONE
        } else {
            add_button.visibility = View.VISIBLE
        }
    }

    private fun addCompany() {
        var disposable: Disposable? = null

        val progress = ProgressDialogFactory.getDialog(this, cancelListener = {
            disposable?.dispose()
        })

        disposable = AddCompanyUseCase(
                company, clientCompaniesRepository
        )
                .perform()
                .compose(ObservableTransformers.defaultSchedulersCompletable())
                .doOnSubscribe { progress.show() }
                .doOnTerminate { progress.dismiss() }
                .subscribeBy(
                        onComplete = { toastManager.short(R.string.company_added_successfully) },
                        onError = { errorHandlerFactory.getDefault().handle(it) }
                )
                .addTo(compositeDisposable)
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        updateBannerSize()
    }

    companion object {
        private const val COMPANY_EXTRA = "company"

        fun getBundle(company: CompanyRecord) = Bundle().apply {
            putSerializable(COMPANY_EXTRA, company)
        }
    }
}
