package com.zl.teachertalk.bean

/**
 * 发布帖子页的不可变界面状态。
 *
 * @param isSubmitting 是否正在压缩、上传或提交帖子
 */
data class CreatePostUiState(
    val isSubmitting: Boolean = false
)

/**
 * 发布帖子流程需要提示的错误类型。
 */
enum class CreatePostError {
    CONTENT_REQUIRED,
    TOO_MANY_IMAGES,
    IMAGE_DECODE_FAILED,
    IMAGE_TOO_LARGE,
    IMAGE_WRITE_FAILED,
    IMAGE_UPLOAD_FAILED,
    INVALID_POST,
    SESSION_EXPIRED,
    PUBLISH_FAILED
}
