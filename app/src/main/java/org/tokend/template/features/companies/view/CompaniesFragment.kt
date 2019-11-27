package org.tokend.template.features.companies.view

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.support.design.widget.TabLayout
import android.support.v4.view.ViewPager
import android.support.v7.widget.Toolbar
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.subjects.BehaviorSubject
import kotlinx.android.synthetic.main.appbar_with_tabs.*
import kotlinx.android.synthetic.main.fragment_pager.*
import kotlinx.android.synthetic.main.toolbar.*
import org.tokend.template.R
import org.tokend.template.data.model.CompanyRecord
import org.tokend.template.data.repository.ClientCompaniesRepository
import org.tokend.template.data.repository.CompaniesRepository
import org.tokend.template.features.companies.logic.CompanyLoader
import org.tokend.template.fragments.BaseFragment
import org.tokend.template.fragments.ToolbarProvider
import org.tokend.template.util.Navigator
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.util.PermissionManager
import org.tokend.template.util.QrScannerUtil
import org.tokend.template.view.util.MenuSearchViewManager
import org.tokend.template.view.util.ProgressDialogFactory
import org.tokend.wallet.Base32Check

class CompaniesFragment : BaseFragment(), ToolbarProvider {
    override val toolbarSubject: BehaviorSubject<Toolbar> = BehaviorSubject.create()

    private val clientCompaniesRepository: ClientCompaniesRepository
        get() = repositoryProvider.clientCompanies()

    private val companiesRepository: CompaniesRepository
        get() = repositoryProvider.companies()

    private lateinit var adapter: CompaniesPagerAdapter

    private var searchMenuItem: MenuItem? = null
    private var filterChangesSubject = BehaviorSubject.create<String>()

    private val cameraPermission = PermissionManager(Manifest.permission.CAMERA, 404)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_pager, container, false)
    }

    override fun onInitAllowed() {
        initToolbar()
        initPager()

        switchToAllIfNeeded()
    }

    private fun initToolbar() {
        toolbar.title = getString(R.string.companies_title)
        toolbarSubject.onNext(toolbar)
        initMenu()
    }

    private fun initMenu() {
        toolbar.inflateMenu(R.menu.explore_companies)
        val menu = toolbar.menu

        val searchItem = menu.findItem(R.id.search) ?: return
        this.searchMenuItem = searchItem

        try {
            val searchManager = MenuSearchViewManager(searchItem, toolbar, compositeDisposable)

            searchManager.queryHint = getString(R.string.search)
            searchManager
                    .queryChanges
                    .subscribe(filterChangesSubject)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        menu.findItem(R.id.scan)?.setOnMenuItemClickListener {
            tryOpenQrScanner()
            true
        }
    }

    @SuppressLint("RestrictedApi")
    private fun initPager() {
        adapter = CompaniesPagerAdapter(requireContext(), childFragmentManager)
        pager.adapter = adapter
        pager.offscreenPageLimit = adapter.count
        appbar_tabs.setupWithViewPager(pager)
        appbar_tabs.tabGravity = TabLayout.GRAVITY_FILL
        appbar_tabs.tabMode = TabLayout.MODE_FIXED

        // Filter.
        val onPageSelected = { pagePosition: Int ->
            adapter
                    .getItem(pagePosition)
                    ?.observeFilterChanges(filterChangesSubject)
        }

        pager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageSelected(position: Int) {
                onPageSelected(position)
            }

            override fun onPageScrollStateChanged(state: Int) {}

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
        })

        onPageSelected(0)
    }

    private fun switchToAllIfNeeded() {
        if (clientCompaniesRepository.itemsList.isEmpty()) {
            pager.currentItem = adapter.getIndexOf(CompaniesPagerAdapter.ALL_COMPANIES_PAGE)
        }
    }

    override fun onBackPressed(): Boolean {
        return if (searchMenuItem?.isActionViewExpanded == true) {
            searchMenuItem?.collapseActionView()
            false
        } else {
            super.onBackPressed()
        }
    }

    private fun tryOpenQrScanner() {
        cameraPermission.check(this) {
            QrScannerUtil.openScanner(this, getString(R.string.scan_company_qr_code))
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        cameraPermission.handlePermissionResult(requestCode, permissions, grantResults)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        QrScannerUtil.getStringFromResult(requestCode, resultCode, data)
                ?.takeIf { Base32Check.isValid(Base32Check.VersionByte.ACCOUNT_ID, it.toCharArray()) }
                ?.also(this::onCompanyIdScanned)
    }

    private fun onCompanyIdScanned(companyId: String) {
        loadAndDisplayCompany(companyId)
    }

    private fun loadAndDisplayCompany(companyId: String) {
        val loadedCompany = companiesRepository
                .itemsMap[companyId]
                ?: clientCompaniesRepository
                        .itemsMap[companyId]

        if (loadedCompany != null) {
            openCompanyDetails(loadedCompany)
            return
        }

        var disposable: Disposable? = null

        val progress = ProgressDialogFactory.getDialog(requireContext(), R.string.loading_data) {
            disposable?.dispose()
        }

        disposable = CompanyLoader(companiesRepository)
                .load(companyId)
                .compose(ObservableTransformers.defaultSchedulersSingle())
                .doOnSubscribe { progress.show() }
                .doOnEvent { _, _ -> progress.dismiss() }
                .subscribeBy(
                        onSuccess = this::openCompanyDetails,
                        onError = this::onCompanyLoadingError
                )
    }

    private fun openCompanyDetails(company: CompanyRecord) {
        Navigator.from(this).openCompanyDetails(company)
    }

    private fun onCompanyLoadingError(error: Throwable) {
        when (error) {
            is CompanyLoader.NoCompanyFoundException ->
                toastManager.short(R.string.error_company_not_found)
            else -> errorHandlerFactory.getDefault().handle(error)
        }
    }

    companion object {
        val ID = "companies".hashCode().toLong()

        fun newInstance() = CompaniesFragment()
    }
}