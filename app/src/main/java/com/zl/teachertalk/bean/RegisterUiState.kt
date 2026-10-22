package com.zl.teachertalk.bean

/**
 * 注册页的不可变界面状态。
 *
 * @param isSubmitting 是否正在提交注册请求
 */
data class RegisterUiState(
    val isSubmitting: Boolean = false
)

/**
 * 注册流程中需要界面提示的错误类型。
 *
 * 这里存类型而不是直接存文案，避免 ViewModel 依赖 Android 资源，也方便后续做中英文切换。
 */
enum class RegisterError {
    EMPTY_FIELDS,
    PASSWORD_MISMATCH,
    ACCOUNT_EXISTS,
    UNKNOWN
}
