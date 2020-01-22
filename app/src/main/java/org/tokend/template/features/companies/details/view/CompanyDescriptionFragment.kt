package org.tokend.template.features.companies.details.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.fragment_company_description.*
import org.tokend.template.R
import org.tokend.template.features.companies.model.CompanyRecord
import org.tokend.template.extensions.withArguments
import org.tokend.template.fragments.BaseFragment
import org.tokend.template.view.util.CircleLogoUtil
import org.tokend.template.view.util.MarkdownUtil

class CompanyDescriptionFragment : BaseFragment() {
    private lateinit var company: CompanyRecord

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_company_description, container, false)
    }

    override fun onInitAllowed() {
        company = arguments?.getSerializable(COMPANY_EXTRA) as? CompanyRecord
                ?: throw IllegalArgumentException("Missing $COMPANY_EXTRA")

        displayDetails()
        displayDescription()
    }

    private fun displayDetails() {
        company_name_text_view.text = company.name

        if (company.industry != null) {
            company_industry_text_view.text = company.industry
        } else {
            company_industry_text_view.visibility = View.GONE
        }

        CircleLogoUtil.setLogo(company_logo_image_view, company.name, company.logoUrl)
    }

    private fun displayDescription() {
        val descriptionMd = company.descriptionMd
        if (descriptionMd != null) {
            val markdown = MarkdownUtil(MarkdownUtil.getDefaultConfiguration(requireContext()))
                    .toMarkdown(descriptionMd)
            MarkdownUtil.setMarkdownText(markdown, description_text_view)
        } else {
            description_text_view.text = getString(R.string.no_description)
        }
    }

    companion object {
        private const val COMPANY_EXTRA = "company"

        fun getBundle(company: CompanyRecord) = Bundle().apply {
            putSerializable(COMPANY_EXTRA, company)
        }

        fun newInstance(bundle: Bundle): CompanyDescriptionFragment =
                CompanyDescriptionFragment().withArguments(bundle)
    }
}