package org.tokend.template.features.companies.add.view

import android.app.Activity
import android.os.Bundle
import android.view.View
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.activity_add_company_confirmation.*
import kotlinx.android.synthetic.main.toolbar.*
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.data.model.CompanyRecord
import org.tokend.template.features.companies.add.logic.AddCompanyUseCase
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.view.util.LogoUtil
import org.tokend.template.view.util.ProgressDialogFactory

class AddCompanyConfirmationActivity : BaseActivity() {
    private lateinit var company: CompanyRecord

    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_add_company_confirmation)

        val company = intent.getSerializableExtra(COMPANY_EXTRA) as? CompanyRecord
        if (company == null) {
            errorHandlerFactory.getDefault().handle(IllegalArgumentException(
                    "Invalid $company"
            ))
            finish()
            return
        }
        this.company = company

        initToolbar()
        initButtons()
        displayCompanyInfo()
    }

    private fun initToolbar() {
        setSupportActionBar(toolbar)
        setTitle(R.string.add_company)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun initButtons() {
        confirm_button.setOnClickListener {
            addCompany()
        }
        cancel_button.setOnClickListener {
            finish()
        }
    }

    private fun displayCompanyInfo() {
        val logoSize = resources.getDimensionPixelSize(R.dimen.unlock_logo_size)
        LogoUtil.setLogo(
                company_logo_image_view,
                company.name,
                company.logoUrl,
                logoSize
        )

        company_name_text_view.text = company.name

        company.industry?.let {
            company_industry_text_view.apply {
                visibility = View.VISIBLE
                text = it
            }
        }
    }

    private fun addCompany() {
        var disposable: Disposable? = null

        val progress = ProgressDialogFactory.getDialog(this) {
            disposable?.dispose()
        }

        disposable = AddCompanyUseCase(
                company,
                repositoryProvider.clientCompanies()
        )
                .perform()
                .compose(ObservableTransformers.defaultSchedulersCompletable())
                .doOnSubscribe { progress.show() }
                .doOnEvent { progress.dismiss() }
                .subscribeBy(
                        onComplete = this::onCompanyAdded,
                        onError = { errorHandlerFactory.getDefault().handle(it) }
                )
    }

    private fun onCompanyAdded() {
        toastManager.short(R.string.company_added_successfully)
        setResult(Activity.RESULT_OK)
        finish()
    }

    companion object {
        private const val COMPANY_EXTRA = "company"

        fun getBundle(company: CompanyRecord) = Bundle().apply {
            putSerializable(COMPANY_EXTRA, company)
        }
    }
}
