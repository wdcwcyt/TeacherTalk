package com.zl.teachertalk.ui.forum

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.Scroller
import androidx.appcompat.widget.AppCompatEditText
import kotlin.math.abs

/**
 * 支持纵向惯性滚动的多行输入框。
 *
 * 输入框内部内容超过可视高度后，松手时根据手指速度继续滚动。
 */
class InertialEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    private val verticalScroller = Scroller(context).also(::setScroller)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minimumFlingVelocity =
        ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private val maximumFlingVelocity =
        ViewConfiguration.get(context).scaledMaximumFlingVelocity
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

    private var velocityTracker: VelocityTracker? = null
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var isVerticalDrag = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                verticalScroller.abortAnimation()
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().also {
                    it.addMovement(event)
                }
                downX = event.x
                downY = event.y
                downTime = event.eventTime
                isVerticalDrag = false
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val distanceX = abs(event.x - downX)
                val distanceY = abs(event.y - downY)
                if (distanceY > touchSlop && distanceY > distanceX) {
                    isVerticalDrag = true
                }
            }

            MotionEvent.ACTION_UP -> {
                velocityTracker?.addMovement(event)
            }

            MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.addMovement(event)
            }
        }

        val handled = super.onTouchEvent(event)

        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val isShortGesture = event.eventTime - downTime < longPressTimeout
            if (isVerticalDrag && isShortGesture && selectionStart == selectionEnd) {
                startVerticalFling()
            }
        }
        if (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            endGesture()
        }
        return handled
    }

    override fun onDetachedFromWindow() {
        verticalScroller.abortAnimation()
        velocityTracker?.recycle()
        velocityTracker = null
        super.onDetachedFromWindow()
    }

    private fun startVerticalFling() {
        val tracker = velocityTracker ?: return
        tracker.computeCurrentVelocity(1000, maximumFlingVelocity.toFloat())
        val fingerVelocityY = tracker.yVelocity
        if (abs(fingerVelocityY) < minimumFlingVelocity) return

        val scrollVelocityY = (-fingerVelocityY).toInt()
        val scrollDirection = when {
            scrollVelocityY > 0 -> 1
            scrollVelocityY < 0 -> -1
            else -> 0
        }
        if (!canScrollVertically(scrollDirection)) return

        val layoutHeight = layout?.height ?: return
        val viewportHeight = height - totalPaddingTop - totalPaddingBottom
        val maxScrollY = (layoutHeight - viewportHeight).coerceAtLeast(0)
        if (maxScrollY == 0) return

        verticalScroller.fling(
            scrollX,
            scrollY,
            0,
            scrollVelocityY,
            0,
            0,
            0,
            maxScrollY
        )
        invalidate()
    }

    private fun endGesture() {
        parent?.requestDisallowInterceptTouchEvent(false)
        velocityTracker?.recycle()
        velocityTracker = null
        isVerticalDrag = false
    }
}
