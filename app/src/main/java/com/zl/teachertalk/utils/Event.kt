package com.zl.teachertalk.utils

/**
 * 只应被消费一次的界面事件包装，例如 Toast、页面跳转。
 *
 * LiveData 在页面重建时会重新发送最后一个值，直接放普通值可能导致重复提示；
 * 通过 [getContentIfNotHandled] 可以保证同一个事件只消费一次。
 *
 * @param content 事件内容
 */
class Event<out T>(private val content: T) {

    private var hasBeenHandled = false

    /**
     * 第一次调用返回事件内容，之后返回 null。
     */
    fun getContentIfNotHandled(): T? {
        if (hasBeenHandled) return null
        hasBeenHandled = true
        return content
    }

    /**
     * 查看事件内容但不标记为已消费，主要用于测试或特殊场景。
     */
    fun peekContent(): T = content
}
