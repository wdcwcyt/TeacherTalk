package com.zl.teachertalk.bean

/**
 * 登录或注册页面共享的不可变界面状态。
 *
 * @param isRequestingCode 是否正在请求短信验证码
 * @param isSubmitting 是否正在提交登录或注册
 * @param codeCooldownSeconds 距离再次获取验证码的剩余秒数
 * @param isCheckingSession 是否正在向服务端校验本地保存的登录会话
 */
data class AuthUiState(
    val isRequestingCode: Boolean = false,
    val isSubmitting: Boolean = false,
    val codeCooldownSeconds: Int = 0,
    val isCheckingSession: Boolean = false
)

/**
 * 登录和注册流程需要向用户展示的错误类型。
 */
enum class AuthError {
    INVALID_PHONE,
    INVALID_CODE,
    INVALID_INPUT,
    CODE_INVALID_OR_EXPIRED,
    PHONE_NOT_REGISTERED,
    PHONE_ALREADY_REGISTERED,
    SESSION_EXPIRED,
    NETWORK,
    SERVER,
    UNKNOWN
}
