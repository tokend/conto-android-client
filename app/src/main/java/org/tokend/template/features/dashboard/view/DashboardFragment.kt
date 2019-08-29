package org.tokend.template.features.dashboard.view

import android.annotation.SuppressLint
import android.os.Bundle
import android.support.design.widget.TabLayout
import android.support.v4.view.ViewPager
import android.support.v7.view.SupportMenuInflater
import android.support.v7.widget.Toolbar
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.reactivex.subjects.BehaviorSubject
import kotlinx.android.synthetic.main.appbar_with_tabs.*
import kotlinx.android.synthetic.main.fragment_pager.*
import kotlinx.android.synthetic.main.toolbar.*
import org.tokend.template.R
import org.tokend.template.activities.OnBackPressedListener
import org.tokend.template.fragments.BaseFragment
import org.tokend.template.fragments.ToolbarProvider
import org.tokend.template.view.util.input.SoftInputUtil

class DashboardFragment : BaseFragment(), ToolbarProvider {
    override val toolbarSubject: BehaviorSubject<Toolbar> = BehaviorSubject.create<Toolbar>()

    private lateinit var adapter: DashboardPagerAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_pager, container, false)
    }

    override fun onInitAllowed() {
        toolbarSubject.onNext(toolbar)

        toolbar.title = "TODO"

        initViewPager()
    }

    // region Init
    @SuppressLint("RestrictedApi")
    private fun initViewPager() {
        adapter = DashboardPagerAdapter(requireContext(), childFragmentManager)
        pager.adapter = adapter
        pager.offscreenPageLimit = adapter.count
        appbar_tabs.setupWithViewPager(pager)
        appbar_tabs.tabGravity = TabLayout.GRAVITY_FILL
        appbar_tabs.tabMode = TabLayout.MODE_FIXED

        // Menu.
        val inflatePageMenu = { pagePosition: Int ->
            toolbar.menu.clear()
            adapter
                    .getItem(pagePosition)
                    ?.onCreateOptionsMenu(
                            toolbar.menu,
                            SupportMenuInflater(requireContext())
                    )
        }

        val onPageSelected = { pagePosition: Int ->
            inflatePageMenu(pagePosition)
            SoftInputUtil.hideSoftInput(requireActivity())
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
    // endregion

    override fun onBackPressed(): Boolean {
        val currentPage = adapter.getItem(pager.currentItem)
        return if (currentPage is OnBackPressedListener) {
            currentPage.onBackPressed()
        } else {
            super.onBackPressed()
        }
    }

    companion object {
        const val ID = 1110L

        fun newInstance(): DashboardFragment {
            val fragment = DashboardFragment()
            val args = Bundle()
            fragment.arguments = args
            return fragment
        }
    }
}
