package org.tokend.template.util.imagetransform

import android.graphics.*
import android.support.annotation.Dimension
import com.squareup.picasso.Transformation

class RoundedCornersTransform(
        @Dimension
        private val cornerRadius: Float
) : Transformation {
    override fun key(): String = "rounded-corners"

    override fun transform(source: Bitmap): Bitmap {
        val bitmap = Bitmap.createBitmap(source.width, source.height, source.config)

        val canvas = Canvas(bitmap)
        val paint = Paint()
        val shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        paint.shader = shader
        paint.isAntiAlias = true

        canvas.drawRoundRect(
                RectF(0f, 0f, source.width.toFloat(), source.height.toFloat()),
                cornerRadius,
                cornerRadius,
                paint
        )

        source.recycle()

        return bitmap
    }
}