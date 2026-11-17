package com.zl.teachertalk.bean

/**
 * 发布帖子页的不可变界面状态。
 *
 * @param isSubmitting 是否正在压缩、上传或提交帖子
 * @param isLoadingOriginal 编辑模式是否正在读取原帖子
 * @param loadError 编辑模式读取原帖子的失败类型
 * @param originalPost 编辑模式读取到的原帖子
 */
data class CreatePostUiState(
    val isSubmitting: Boolean = false,
    val isLoadingOriginal: Boolean = false,
    val loadError: PostEditorLoadError? = null,
    val originalPost: ForumPostDetail? = null
)

/**
 * 编辑模式读取原帖子失败的类型。
 */
enum class PostEditorLoadError {
    NOT_FOUND,
    NO_PERMISSION,
    NETWORK,
    SERVER,
    UNKNOWN
}

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
    NO_PERMISSION,
    POST_NOT_FOUND,
    PUBLISH_FAILED,
    UPDATE_FAILED
}
