package com.zl.teachertalk.bean

/**
 * 订单列表页状态。
 *
 * @param hasOrders 当前筛选条件下是否存在订单
 * @param isRefreshing 是否正在进行下拉刷新
 */
data class OrderUiState(
    val hasOrders: Boolean = false,
    val isRefreshing: Boolean = false,
)
