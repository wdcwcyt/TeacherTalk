package com.zl.teachertalk.bean

/**
 * 帖子详情页的不可变界面状态。
 *
 * @param post 成功加载的帖子详情；加载中或失败时为 null
 * @param isLoading 是否正在请求帖子详情
 * @param loadError 整页加载失败的原因；没有失败时为 null
 * @param isLikeSubmitting 是否正在提交点赞或取消点赞
 * @param isFavoriteSubmitting 是否正在提交收藏或取消收藏
 * @param comments 已加载并去重的评论
 * @param commentsPage 最后成功加载的评论页码
 * @param commentsHasMore 是否还有下一页评论
 * @param isLoadingComments 首屏评论是否正在加载
 * @param isLoadingMoreComments 是否正在加载下一页评论
 * @param commentsLoadFailed 首屏评论是否加载失败
 * @param isSubmittingComment 是否正在发表评论
 */
data class ForumPostDetailUiState(
    val post: ForumPostDetail? = null,
    val isLoading: Boolean = false,
    val loadError: ForumDetailLoadError? = null,
    val isLikeSubmitting: Boolean = false,
    val isFavoriteSubmitting: Boolean = false,
    val comments: List<ForumComment> = emptyList(),
    val commentsPage: Int = 0,
    val commentsHasMore: Boolean = false,
    val isLoadingComments: Boolean = false,
    val isLoadingMoreComments: Boolean = false,
    val commentsLoadFailed: Boolean = false,
    val isSubmittingComment: Boolean = false
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

/**
 * 点赞或收藏操作失败时的一次性提示类型。
 */
enum class ForumInteractionError {
    LIKE_FAILED,
    FAVORITE_FAILED,
    POST_NOT_FOUND
}

/**
 * 评论加载或发表失败时的一次性提示类型。
 */
enum class ForumCommentError {
    EMPTY_CONTENT,
    TOO_LONG,
    SUBMIT_FAILED,
    LOAD_MORE_FAILED,
    POST_NOT_FOUND
}
