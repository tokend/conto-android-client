package org.tokend.template.features.accountdetails.view

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import kotlinx.android.synthetic.main.appbar.*
import kotlinx.android.synthetic.main.fragment_movements.*
import kotlinx.android.synthetic.main.include_appbar_elevation.*
import org.tokend.template.R
import org.tokend.template.features.movements.view.AssetMovementsFragment
import org.tokend.template.view.util.ElevationUtil

class BottomSheetAssetMovementsFragment : AssetMovementsFragment() {
    private val recyclerViewSubject = BehaviorSubject.create<View>()
    val recyclerViewObservable: Observable<View> = recyclerViewSubject

    private lateinit var balanceSelectionView: TextView

    override fun initToolbar() {
        appbar.visibility = View.GONE
        ElevationUtil.initScrollElevation(history_list, appbar_elevation_view)
    }

    override fun initList() {
        super.initList()
        recyclerViewSubject.onNext(history_list)
    }

    override fun initBalanceSelection() {
        val balanceSelectionLayout =
                layoutInflater.inflate(R.layout.include_text_view_spinner_for_centering,
                        root_layout, false)
        root_layout.addView(balanceSelectionLayout, 0)

        balanceSelectionView = balanceSelectionLayout.findViewById(R.id.spinner_text_view)
        balanceSelectionLayout.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
            topMargin = resources.getDimensionPixelSize(R.dimen.half_standard_margin)
            bottomMargin = topMargin
            marginStart = resources.getDimensionPixelSize(R.dimen.standard_margin)
            marginEnd = marginStart
        }

        balanceSelectionView.setOnClickListener {
            openBalancePicker()
        }

        super.initBalanceSelection()
    }

    override fun onBalanceChanged() {
        super.onBalanceChanged()
        balanceSelectionView.text = currentBalance?.asset?.name ?: currentBalance?.assetCode
    }
}