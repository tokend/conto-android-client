package org.tokend.template.features.swap.view

import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.Toolbar
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.reactivex.subjects.BehaviorSubject
import kotlinx.android.synthetic.main.fragment_swaps.*
import kotlinx.android.synthetic.main.include_appbar_elevation.*
import kotlinx.android.synthetic.main.include_error_empty_view.*
import kotlinx.android.synthetic.main.toolbar.*
import org.tokend.template.R
import org.tokend.template.data.model.SimpleAsset
import org.tokend.template.features.swap.model.SwapRecord
import org.tokend.template.features.swap.model.SwapState
import org.tokend.template.features.swap.view.adapter.SwapListItem
import org.tokend.template.features.swap.view.adapter.SwapsAdapter
import org.tokend.template.fragments.BaseFragment
import org.tokend.template.fragments.ToolbarProvider
import org.tokend.template.view.util.ElevationUtil
import java.math.BigDecimal

class SwapsFragment : BaseFragment(), ToolbarProvider {
    override val toolbarSubject = BehaviorSubject.create<Toolbar>()

    private lateinit var adapter: SwapsAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_swaps, container, false)
    }

    override fun onInitAllowed() {
        initToolbar()
        initList()

        displaySwaps()
    }

    private fun initToolbar() {
        toolbar.title = getString(R.string.swaps_screen_title)
        toolbarSubject.onNext(toolbar)
        ElevationUtil.initScrollElevation(swaps_recycler_view, appbar_elevation_view)
    }

    private fun initList() {
        adapter = SwapsAdapter(amountFormatter)
        swaps_recycler_view.layoutManager = LinearLayoutManager(requireContext())
        swaps_recycler_view.adapter = adapter

        adapter.onItemClick { _, item ->
            item.source?.also(this::openSwapDetails)
        }

        error_empty_view.apply {
            setEmptyDrawable(R.drawable.ic_shop_cart)
            observeAdapter(adapter, R.string.no_offers)
//            setEmptyViewDenial { asksRepository.isNeverUpdated }
        }
    }

    private fun displaySwaps() {
        val items = listOf(
                SwapListItem(
                        BigDecimal("100"), SimpleAsset("", 6, "Gas station bonuses", null),
                        BigDecimal("25"), SimpleAsset("", 6, "Pet shop points", null),
                        "alice@mail.com",
                        SwapState.CREATED,
                        false,
                        null
                ),
                SwapListItem(
                        BigDecimal("10"), SimpleAsset("", 6, "Silpo bonus points", null),
                        BigDecimal("10"), SimpleAsset("", 6, "ATB bonuses", null),
                        "alice@mail.com",
                        SwapState.ABILITY_TO_RECEIVE,
                        true,
                        null
                )
        )

        adapter.setData(items)
    }

    private fun openSwapDetails(swap: SwapRecord) {

    }

    companion object {
        val ID = "swaps".hashCode().toLong()
    }
}