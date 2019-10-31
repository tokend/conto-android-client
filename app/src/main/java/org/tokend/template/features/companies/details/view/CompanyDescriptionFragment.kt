package org.tokend.template.features.companies.details.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.fragment_company_description.*
import org.tokend.template.R
import org.tokend.template.data.model.CompanyRecord
import org.tokend.template.extensions.withArguments
import org.tokend.template.fragments.BaseFragment
import org.tokend.template.view.details.DetailsItem
import org.tokend.template.view.details.adapter.DetailsItemViewHolder

class CompanyDescriptionFragment : BaseFragment() {
    private lateinit var company: CompanyRecord

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_company_description, container, false)
    }

    override fun onInitAllowed() {
        company = arguments?.getSerializable(COMPANY_EXTRA) as? CompanyRecord
                ?: throw IllegalArgumentException("Missing $COMPANY_EXTRA")

        displayName()
        displayDetails()
        displayDescription()
    }

    private fun displayName() {
        company_name_text_view.text = company.name
    }

    private fun displayDetails() {
        val addDetailsItem = { it: DetailsItem ->
            val view = layoutInflater.inflate(R.layout.list_item_details_row,
                    details_layout, true)
            DetailsItemViewHolder(view).apply {
                bind(it)
                dividerIsVisible = false
            }
            view.findViewById<View>(R.id.icon_frame).visibility = View.GONE
        }

        if (company.industry != null) {
            addDetailsItem(DetailsItem(
                    text = company.industry,
                    hint = getString(R.string.company_industry)
            ))
        }
    }

    private fun displayDescription() {
        if (company.descriptionMd != null) {
            description_text_view.text = company.descriptionMd
        } else {
            description_text_view.visibility = View.GONE
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