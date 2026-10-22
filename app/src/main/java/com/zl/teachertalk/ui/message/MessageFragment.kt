package com.zl.teachertalk.ui.message

import android.view.LayoutInflater
import android.view.ViewGroup
import com.zl.teachertalk.base.BaseFragment
import com.zl.teachertalk.databinding.FragmentMessageBinding

/**
 * 消息内容页：当前只提供主页切换所需的占位界面。
 */
class MessageFragment : BaseFragment<FragmentMessageBinding>() {

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentMessageBinding {
        return FragmentMessageBinding.inflate(inflater, container, false)
    }
}
