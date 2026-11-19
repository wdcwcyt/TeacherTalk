package com.zl.teachertalk.bean

/**
 * 个人信息页的不可变界面状态。
 *
 * @param userInfo 当前登录用户；本地会话不完整时为 null
 * @param isUpdatingAvatar 是否正在压缩或上传头像
 * @param isRequestingCancelCode 是否正在发送注销账号验证码
 * @param cancelCodeCooldownSeconds 注销验证码还需等待多少秒才能重发
 * @param isCancellingAccount 是否正在提交账号注销请求
 */
data class ProfileUiState(
    val userInfo: UserInfoBean? = null,
    val isUpdatingAvatar: Boolean = false,
    val isRequestingCancelCode: Boolean = false,
    val cancelCodeCooldownSeconds: Long = 0,
    val isCancellingAccount: Boolean = false
)

/**
 * 头像修改失败时向界面展示的错误类型。
 */
enum class ProfileAvatarError {
    INVALID_IMAGE,
    IMAGE_TOO_LARGE,
    UPLOAD_FAILED,
    NETWORK,
    SERVER,
    SESSION_EXPIRED
}

/**
 * 注销账号验证码或提交阶段向界面展示的错误类型。
 */
enum class CancelAccountError {
    INVALID_PHONE,
    INVALID_CODE,
    NETWORK,
    SERVER,
    SESSION_EXPIRED,
    STATE_CONFLICT,
    UNKNOWN
}
