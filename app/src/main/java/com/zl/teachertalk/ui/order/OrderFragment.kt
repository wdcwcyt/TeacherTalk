package com.zl.teachertalk.ui.order

import android.view.LayoutInflater
import android.view.ViewGroup
import com.zl.teachertalk.base.BaseFragment
import com.zl.teachertalk.databinding.FragmentOrderBinding

/**
 * 订单内容页：当前只提供主页切换所需的占位界面。
 */
class OrderFragment : BaseFragment<FragmentOrderBinding>() {

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentOrderBinding {
        return FragmentOrderBinding.inflate(inflater, container, false)
    }
}
