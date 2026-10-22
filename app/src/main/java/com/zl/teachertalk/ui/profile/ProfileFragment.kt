package com.zl.teachertalk.ui.profile

import android.view.LayoutInflater
import android.view.ViewGroup
import com.zl.teachertalk.base.BaseFragment
import com.zl.teachertalk.databinding.FragmentProfileBinding

/**
 * 我的内容页：当前只提供主页切换所需的占位界面。
 */
class ProfileFragment : BaseFragment<FragmentProfileBinding>() {

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentProfileBinding {
        return FragmentProfileBinding.inflate(inflater, container, false)
    }
}
