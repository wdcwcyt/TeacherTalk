package com.zl.teachertalk.model.order

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zl.teachertalk.bean.OrderUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 订单列表页 ViewModel。
 *
 * 当前订单数据接口尚未接入，下拉刷新使用最长 3 秒的占位刷新状态，
 * 避免刷新进度条无限旋转；接入真实数据后由请求结果提前结束刷新。
 */
class OrderViewModel : ViewModel() {

    private val mutableUiState = MutableLiveData(OrderUiState())

    /**
     * 订单列表页当前状态。
     */
    val uiState: LiveData<OrderUiState> = mutableUiState

    /**
     * 开始一次下拉刷新。
     *
     * 刷新期间隐藏空态，最多 3 秒后结束；重复下拉不会叠加多个刷新任务。
     */
    fun refresh() {
        val currentState = mutableUiState.value ?: OrderUiState()
        if (currentState.isRefreshing) return

        mutableUiState.value = currentState.copy(isRefreshing = true)
        viewModelScope.launch {
            delay(REFRESH_TIMEOUT_MILLIS)
            val latestState = mutableUiState.value ?: return@launch
            mutableUiState.value = latestState.copy(isRefreshing = false)
        }
    }

    private companion object {
        const val REFRESH_TIMEOUT_MILLIS = 3_000L
    }
}
