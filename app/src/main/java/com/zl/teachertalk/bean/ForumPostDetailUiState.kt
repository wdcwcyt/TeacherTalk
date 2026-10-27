package com.zl.teachertalk.bean

/**
 * 帖子详情页的不可变界面状态。
 *
 * @param post 成功加载的帖子详情；加载中或失败时为 null
 * @param isLoading 是否正在请求帖子详情
 * @param loadError 整页加载失败的原因；没有失败时为 null
 */
data class ForumPostDetailUiState(
    val post: ForumPostDetail? = null,
    val isLoading: Boolean = false,
    val loadError: ForumDetailLoadError? = null
)

/**
 * 帖子详情整页加载失败的类型。
 */
enum class ForumDetailLoadError {
    NOT_FOUND,
    NETWORK,
    SERVER,
    UNKNOWN
}
