package com.zl.teachertalk.ui.order

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import com.zl.teachertalk.R
import com.zl.teachertalk.base.BaseFragment
import com.zl.teachertalk.bean.OrderUiState
import com.zl.teachertalk.databinding.FragmentOrderBinding
import com.zl.teachertalk.model.order.OrderViewModel

/**
 * 订单内容页：承载需求列表、筛选和页面状态；真实数据接入后续完成。
 */
class OrderFragment : BaseFragment<FragmentOrderBinding>() {

    private val viewModel: OrderViewModel by lazy {
        ViewModelProvider(this)[OrderViewModel::class.java]
    }

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentOrderBinding {
        return FragmentOrderBinding.inflate(inflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val currentBinding = binding ?: return
        currentBinding.swipeRefreshOrders.setColorSchemeResources(R.color.main_primary)
        currentBinding.swipeRefreshOrders.setOnChildScrollUpCallback { _, _ ->
            currentBinding.rvOrders.canScrollVertically(-1)
        }
        currentBinding.swipeRefreshOrders.setOnRefreshListener {
            viewModel.refresh()
        }

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            render(state)
        }
    }

    private fun render(state: OrderUiState) {
        val currentBinding = binding ?: return

        currentBinding.swipeRefreshOrders.isRefreshing = state.isRefreshing
        currentBinding.orderStateContainer.isVisible = !state.hasOrders && !state.isRefreshing
        currentBinding.layoutOrderEmptyState.isVisible = !state.hasOrders
        currentBinding.layoutOrderLoadingState.isVisible = false
        currentBinding.layoutOrderErrorState.isVisible = false
    }
}
