package com.zl.teachertalk.net

/**
 * 网络层统一异常，业务码和 HTTP 状态码分开保存，便于界面做不同处理。
 *
 * @param code 业务错误码
 * @param message 供调试和未知错误兜底的消息，不应直接作为所有语言的界面文案
 * @param httpStatus HTTP 状态码；纯业务错误或本地错误时为 null
 * @param cause 原始异常，用于保留解析或网络错误链
 */
class ApiException(
    val code: Int,
    override val message: String,
    val httpStatus: Int? = null,
    cause: Throwable? = null
) : Exception(message, cause)
