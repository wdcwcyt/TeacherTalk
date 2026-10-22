package com.zl.teachertalk.bean

/**
 * 服务端统一响应体。
 *
 * @param code 业务状态码，`0` 表示成功
 * @param message 服务端提示信息，客户端展示前应按业务码转换成双语资源
 * @param data 业务数据，失败响应时可以为空
 */
data class ApiResponse<T>(
    val code: Int,
    val message: String,
    val data: T? = null
)
