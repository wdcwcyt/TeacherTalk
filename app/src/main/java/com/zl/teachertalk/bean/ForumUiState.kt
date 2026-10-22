package com.zl.teachertalk.bean

/**
 * 论坛列表页的不可变界面状态。
 *
 * @param posts 已加载并去重后的帖子
 * @param currentPage 最后一次成功加载的页码
 * @param hasMore 是否还有下一页
 * @param isInitialLoading 首屏是否正在加载
 * @param isRefreshing 下拉或自动刷新是否正在执行
 * @param isLoadingMore 是否正在加载下一页
 * @param initialLoadFailed 首屏加载是否失败
 */
data class ForumUiState(
    val posts: List<ForumPost> = emptyList(),
    val currentPage: Int = 0,
    val hasMore: Boolean = true,
    val isInitialLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val initialLoadFailed: Boolean = false
)

/**
 * 论坛列表需要一次性提示的加载错误。
 */
enum class ForumLoadError {
    REFRESH_FAILED,
    LOAD_MORE_FAILED
}
