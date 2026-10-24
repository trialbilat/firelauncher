package com.custom.firelauncher

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/** A FrameLayout that keeps a 16:9 shape (inside its padding) whatever width the grid gives it. */
class CardFrame @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val contentWidth = w - paddingLeft - paddingRight
        val h = (contentWidth * 9f / 16f).toInt() + paddingTop + paddingBottom
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
    }
}
