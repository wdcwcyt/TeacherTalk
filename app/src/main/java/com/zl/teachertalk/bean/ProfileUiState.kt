package com.zl.teachertalk.bean

/**
 * 个人信息页的不可变界面状态。
 *
 * @param userInfo 当前登录用户；本地会话不完整时为 null
 * @param isUpdatingAvatar 是否正在压缩或上传头像
 */
data class ProfileUiState(
    val userInfo: UserInfoBean? = null,
    val isUpdatingAvatar: Boolean = false
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
