package org.tokend.template.features.booking.add.rooms.view.adapter

import android.annotation.SuppressLint
import android.graphics.drawable.ColorDrawable
import android.support.v4.content.ContextCompat
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import org.tokend.template.R
import org.tokend.template.util.imagetransform.RoundedCornersTransform
import org.tokend.template.view.adapter.base.BaseViewHolder
import org.tokend.template.view.util.ImageViewUtil
import org.tokend.template.view.util.formatter.AmountFormatter

class BookingRoomItemViewHolder(
        view: View,
        private val amountFormatter: AmountFormatter
) : BaseViewHolder<BookingRoomListItem>(view) {
    private val nameTextView: TextView = view.findViewById(R.id.room_name_text_view)
    private val priceTextView: TextView = view.findViewById(R.id.price_text_view)
    private val photoImageView: ImageView = view.findViewById(R.id.room_photo_image_view)
    private val dividerView: View = view.findViewById(R.id.divider_view)

    private val imagePlaceholder = ColorDrawable(ContextCompat.getColor(
            view.context, R.color.imagePlaceholder
    ))

    var dividerIsVisible: Boolean
        get() = dividerView.visibility == View.VISIBLE
        set(value) {
            dividerView.visibility = if (value) View.VISIBLE else View.GONE
        }

    @SuppressLint("SetTextI18n")
    override fun bind(item: BookingRoomListItem) {
        nameTextView.text = item.name
        priceTextView.text = view.context.getString(
                R.string.template_price_per_hour,
                amountFormatter.formatAssetAmount(
                        item.price, item.priceAsset,
                        withAssetCode = true
                )
        )

        ImageViewUtil.loadImage(photoImageView, item.logoUrl, imagePlaceholder) {
            transform(RoundedCornersTransform(
                    view.context.resources.getDimensionPixelSize(R.dimen.modern_card_corner_radius)
                            .toFloat()))
        }
    }
}