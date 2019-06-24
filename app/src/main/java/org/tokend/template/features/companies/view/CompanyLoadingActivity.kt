package org.tokend.template.features.companies.view

import android.os.Bundle
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.activity_company_loading.*
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.features.companies.logic.CompanyUserDataLoader
import org.tokend.template.util.Navigator
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.view.util.LogoUtil

class CompanyLoadingActivity : BaseActivity() {
    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_company_loading)

        displayCompanyInfo()
        performLoading()
    }

    private fun displayCompanyInfo() {
        val company = session.getCompany()
                ?: return

        val logoSize = resources.getDimensionPixelSize(R.dimen.unlock_logo_size)
        LogoUtil.setLogo(
                company_logo_image_view,
                company.name,
                company.logoUrl,
                logoSize
        )

        company_name_text_view.text = company.name
    }

    private fun performLoading() {
        CompanyUserDataLoader(repositoryProvider)
                .load()
                .compose(ObservableTransformers.defaultSchedulersCompletable())
                .subscribeBy(
                        onComplete = {
                            Navigator.from(this).toMainActivity()
                        },
                        onError = {
                            errorHandlerFactory.getDefault().handle(it)
                            finish()
                        }
                )
                .addTo(compositeDisposable)
    }
}
