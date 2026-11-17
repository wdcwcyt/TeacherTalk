package com.zl.teachertalk.model.forum

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zl.teachertalk.bean.ForumDetailLoadError
import com.zl.teachertalk.bean.ForumComment
import com.zl.teachertalk.bean.ForumCommentError
import com.zl.teachertalk.bean.ForumInteractionError
import com.zl.teachertalk.bean.ForumPostDetailUiState
import com.zl.teachertalk.net.ApiException
import com.zl.teachertalk.net.ForumRepository
import com.zl.teachertalk.net.SessionManager
import com.zl.teachertalk.utils.Event
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 帖子详情页流程控制：加载详情和评论、提交互动与评论，并把结果转换为界面状态。
 *
 * @param postId 要加载的帖子编号
 * @param forumRepository 论坛数据仓库
 * @param sessionManager 登录会话
 */
class ForumPostDetailViewModel(
    private val postId: Long,
    private val forumRepository: ForumRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableLiveData(ForumPostDetailUiState())

    /**
     * 帖子详情页界面状态。
     */
    val uiState: LiveData<ForumPostDetailUiState> = _uiState

    private val _loginRequiredEvents = MutableLiveData<Event<Unit>>()

    /**
     * 未登录或 token 失效时，需要回到登录页的一次性事件。
     */
    val loginRequiredEvents: LiveData<Event<Unit>> = _loginRequiredEvents

    private val _interactionErrorEvents =
        MutableLiveData<Event<ForumInteractionError>>()

    /**
     * 点赞或收藏失败时的一次性提示。
     */
    val interactionErrorEvents: LiveData<Event<ForumInteractionError>> =
        _interactionErrorEvents

    private val _interactionSucceededEvents = MutableLiveData<Event<Unit>>()

    /**
     * 点赞、取消点赞、收藏或取消收藏成功后的一次性事件。
     */
    val interactionSucceededEvents: LiveData<Event<Unit>> = _interactionSucceededEvents

    private val _commentErrorEvents = MutableLiveData<Event<ForumCommentError>>()

    /**
     * 评论加载更多或发表失败时的一次性提示。
     */
    val commentErrorEvents: LiveData<Event<ForumCommentError>> = _commentErrorEvents

    private val _commentCreatedEvents = MutableLiveData<Event<ForumComment>>()

    /**
     * 评论发表成功后的一次性事件。
     */
    val commentCreatedEvents: LiveData<Event<ForumComment>> = _commentCreatedEvents

    private var isLoginRequiredEmitted = false

    /**
     * 首次进入页面时加载详情；已有帖子数据时不会重复请求。
     */
    fun loadPost() {
        val state = _uiState.value ?: ForumPostDetailUiState()
        if (state.post != null || state.isLoading) return
        loadPostDetail()
    }

    /**
     * 加载失败后由用户点击重试。
     */
    fun retry() {
        val state = _uiState.value ?: ForumPostDetailUiState()
        if (state.isLoading) return
        loadPostDetail()
    }

    /**
     * 编辑成功后重新拉取帖子详情。
     */
    fun reloadPost() {
        val state = _uiState.value ?: ForumPostDetailUiState()
        if (state.isLoading) return
        loadPostDetail()
    }

    /**
     * 首屏评论加载失败后重新从第一页加载。
     */
    fun retryComments() {
        val state = _uiState.value ?: return
        if (state.post == null ||
            state.isLoadingComments ||
            state.isLoadingMoreComments
        ) {
            return
        }
        loadComments()
    }

    /**
     * 加载下一页评论；没有更多或已有加载任务时忽略。
     */
    fun loadMoreComments() {
        val state = _uiState.value ?: return
        if (state.post == null ||
            !state.commentsHasMore ||
            state.isLoadingComments ||
            state.isLoadingMoreComments ||
            state.isSubmittingComment
        ) {
            return
        }
        if (!sessionManager.isLoggedIn) {
            emitLoginRequired()
            return
        }

        val nextPage = state.commentsPage + 1
        _uiState.value = state.copy(isLoadingMoreComments = true)
        viewModelScope.launch {
            try {
                val page = forumRepository.loadComments(
                    postId = postId,
                    page = nextPage,
                    size = COMMENT_PAGE_SIZE
                )
                val currentState = _uiState.value ?: return@launch
                val currentPost = currentState.post ?: run {
                    _uiState.value = currentState.copy(
                        isLoadingMoreComments = false
                    )
                    return@launch
                }
                _uiState.value = currentState.copy(
                    post = currentPost.copy(
                        commentCount = page.total.toCommentCount()
                    ),
                    comments = sortComments(currentState.comments + page.list),
                    commentsPage = page.page,
                    commentsHasMore = page.page.toLong() * page.size < page.total,
                    isLoadingMoreComments = false,
                    commentsLoadFailed = false
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: ApiException) {
                handleCommentLoadApiError(exception, isInitialLoad = false)
            } catch (exception: IOException) {
                handleCommentLoadFailure(isInitialLoad = false)
            } catch (exception: Exception) {
                handleCommentLoadFailure(isInitialLoad = false)
            }
        }
    }

    /**
     * 发表评论。
     *
     * @param content 用户输入的原始评论内容
     */
    fun submitComment(content: String) {
        val state = _uiState.value ?: return
        if (state.post == null ||
            state.isSubmittingComment ||
            state.isLoadingMoreComments ||
            state.isLikeSubmitting ||
            state.isFavoriteSubmitting
        ) {
            return
        }

        val normalizedContent = content.trim()
        val codePointCount = normalizedContent.codePointCount(0, normalizedContent.length)
        when {
            normalizedContent.isEmpty() -> {
                _commentErrorEvents.value = Event(ForumCommentError.EMPTY_CONTENT)
                return
            }

            codePointCount > MAX_COMMENT_LENGTH -> {
                _commentErrorEvents.value = Event(ForumCommentError.TOO_LONG)
                return
            }
        }
        if (!sessionManager.isLoggedIn) {
            emitLoginRequired()
            return
        }

        _uiState.value = state.copy(isSubmittingComment = true)
        viewModelScope.launch {
            var succeeded = false
            try {
                val comment = forumRepository.createComment(
                    postId = postId,
                    content = normalizedContent
                )
                val currentState = _uiState.value ?: return@launch
                val currentPost = currentState.post ?: return@launch
                _uiState.value = currentState.copy(
                    post = currentPost.copy(
                        commentCount = (currentPost.commentCount + 1)
                            .coerceAtLeast(1)
                    ),
                    comments = sortComments(currentState.comments + comment),
                    isSubmittingComment = false,
                    commentsLoadFailed = false
                )
                succeeded = true
                _commentCreatedEvents.value = Event(comment)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: ApiException) {
                handleCommentSubmitApiError(exception)
            } catch (exception: IOException) {
                _commentErrorEvents.value = Event(ForumCommentError.SUBMIT_FAILED)
            } catch (exception: Exception) {
                _commentErrorEvents.value = Event(ForumCommentError.SUBMIT_FAILED)
            } finally {
                if (!succeeded) {
                    val currentState = _uiState.value
                    if (currentState != null) {
                        _uiState.value = currentState.copy(isSubmittingComment = false)
                    }
                }
            }
        }
    }

    /**
     * 根据当前点赞状态执行点赞或取消点赞。
     */
    fun toggleLike() {
        val state = _uiState.value ?: return
        val post = state.post ?: return
        if (state.isLikeSubmitting ||
            state.isFavoriteSubmitting ||
            state.isSubmittingComment
        ) {
            return
        }
        if (!sessionManager.isLoggedIn) {
            emitLoginRequired()
            return
        }

        _uiState.value = state.copy(isLikeSubmitting = true)
        viewModelScope.launch {
            var succeeded = false
            try {
                val result = if (post.liked) {
                    forumRepository.unlikePost(postId)
                } else {
                    forumRepository.likePost(postId)
                }
                val currentState = _uiState.value ?: return@launch
                val currentPost = currentState.post ?: return@launch
                _uiState.value = currentState.copy(
                    post = currentPost.copy(
                        liked = result.liked,
                        likeCount = result.likeCount
                    ),
                    isLikeSubmitting = false
                )
                succeeded = true
                _interactionSucceededEvents.value = Event(Unit)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: ApiException) {
                handleInteractionApiError(
                    exception = exception,
                    fallbackError = ForumInteractionError.LIKE_FAILED
                )
            } catch (exception: IOException) {
                _interactionErrorEvents.value = Event(ForumInteractionError.LIKE_FAILED)
            } catch (exception: Exception) {
                _interactionErrorEvents.value = Event(ForumInteractionError.LIKE_FAILED)
            } finally {
                if (!succeeded) {
                    val currentState = _uiState.value
                    if (currentState != null) {
                        _uiState.value = currentState.copy(isLikeSubmitting = false)
                    }
                }
            }
        }
    }

    /**
     * 根据当前收藏状态执行收藏或取消收藏。
     */
    fun toggleFavorite() {
        val state = _uiState.value ?: return
        val post = state.post ?: return
        if (state.isLikeSubmitting ||
            state.isFavoriteSubmitting ||
            state.isSubmittingComment
        ) {
            return
        }
        if (!sessionManager.isLoggedIn) {
            emitLoginRequired()
            return
        }

        _uiState.value = state.copy(isFavoriteSubmitting = true)
        viewModelScope.launch {
            var succeeded = false
            try {
                val result = if (post.favorited) {
                    forumRepository.unfavoritePost(postId)
                } else {
                    forumRepository.favoritePost(postId)
                }
                val currentState = _uiState.value ?: return@launch
                val currentPost = currentState.post ?: return@launch
                _uiState.value = currentState.copy(
                    post = currentPost.copy(
                        favorited = result.favorited,
                        favoriteCount = result.favoriteCount
                    ),
                    isFavoriteSubmitting = false
                )
                succeeded = true
                _interactionSucceededEvents.value = Event(Unit)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: ApiException) {
                handleInteractionApiError(
                    exception = exception,
                    fallbackError = ForumInteractionError.FAVORITE_FAILED
                )
            } catch (exception: IOException) {
                _interactionErrorEvents.value = Event(ForumInteractionError.FAVORITE_FAILED)
            } catch (exception: Exception) {
                _interactionErrorEvents.value = Event(ForumInteractionError.FAVORITE_FAILED)
            } finally {
                if (!succeeded) {
                    val currentState = _uiState.value
                    if (currentState != null) {
                        _uiState.value = currentState.copy(isFavoriteSubmitting = false)
                    }
                }
            }
        }
    }

    private fun loadPostDetail() {
        if (postId <= INVALID_POST_ID) {
            _uiState.value = ForumPostDetailUiState(
                loadError = ForumDetailLoadError.NOT_FOUND
            )
            return
        }
        if (!sessionManager.isLoggedIn) {
            emitLoginRequired()
            return
        }

        _uiState.value = ForumPostDetailUiState(isLoading = true)
        viewModelScope.launch {
            try {
                val post = forumRepository.loadPostDetail(postId)
                _uiState.value = ForumPostDetailUiState(post = post)
                loadComments()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: ApiException) {
                handleApiError(exception)
            } catch (exception: IOException) {
                _uiState.value = ForumPostDetailUiState(
                    loadError = ForumDetailLoadError.NETWORK
                )
            } catch (exception: Exception) {
                _uiState.value = ForumPostDetailUiState(
                    loadError = ForumDetailLoadError.UNKNOWN
                )
            }
        }
    }

    private fun loadComments() {
        val state = _uiState.value ?: return
        if (state.post == null ||
            state.isLoadingComments ||
            state.isLoadingMoreComments
        ) {
            return
        }
        if (!sessionManager.isLoggedIn) {
            emitLoginRequired()
            return
        }

        _uiState.value = state.copy(
            isLoadingComments = true,
            commentsLoadFailed = false
        )
        viewModelScope.launch {
            try {
                val page = forumRepository.loadComments(
                    postId = postId,
                    page = FIRST_COMMENT_PAGE,
                    size = COMMENT_PAGE_SIZE
                )
                val currentState = _uiState.value ?: return@launch
                val currentPost = currentState.post ?: run {
                    _uiState.value = currentState.copy(isLoadingComments = false)
                    return@launch
                }
                _uiState.value = currentState.copy(
                    post = currentPost.copy(
                        commentCount = page.total.toCommentCount()
                    ),
                    comments = sortComments(page.list),
                    commentsPage = page.page,
                    commentsHasMore = page.page.toLong() * page.size < page.total,
                    isLoadingComments = false,
                    commentsLoadFailed = false
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: ApiException) {
                handleCommentLoadApiError(exception, isInitialLoad = true)
            } catch (exception: IOException) {
                handleCommentLoadFailure(isInitialLoad = true)
            } catch (exception: Exception) {
                handleCommentLoadFailure(isInitialLoad = true)
            }
        }
    }

    private fun handleCommentLoadApiError(
        exception: ApiException,
        isInitialLoad: Boolean
    ) {
        when {
            exception.code == HTTP_UNAUTHORIZED ||
                exception.httpStatus == HTTP_UNAUTHORIZED -> emitLoginRequired()

            exception.code == HTTP_NOT_FOUND ||
                exception.httpStatus == HTTP_NOT_FOUND -> {
                val currentState = _uiState.value ?: ForumPostDetailUiState()
                _uiState.value = currentState.copy(
                    post = null,
                    loadError = ForumDetailLoadError.NOT_FOUND,
                    isLoadingComments = false,
                    isLoadingMoreComments = false,
                    isSubmittingComment = false
                )
            }

            else -> handleCommentLoadFailure(isInitialLoad)
        }
    }

    private fun handleCommentLoadFailure(isInitialLoad: Boolean) {
        val currentState = _uiState.value ?: return
        if (isInitialLoad) {
            _uiState.value = currentState.copy(
                isLoadingComments = false,
                commentsLoadFailed = true
            )
        } else {
            _uiState.value = currentState.copy(isLoadingMoreComments = false)
            _commentErrorEvents.value = Event(ForumCommentError.LOAD_MORE_FAILED)
        }
    }

    private fun handleCommentSubmitApiError(exception: ApiException) {
        when {
            exception.code == HTTP_UNAUTHORIZED ||
                exception.httpStatus == HTTP_UNAUTHORIZED -> emitLoginRequired()

            exception.code == HTTP_NOT_FOUND ||
                exception.httpStatus == HTTP_NOT_FOUND -> {
                val currentState = _uiState.value ?: ForumPostDetailUiState()
                _uiState.value = currentState.copy(
                    post = null,
                    loadError = ForumDetailLoadError.NOT_FOUND,
                    isSubmittingComment = false
                )
                _commentErrorEvents.value = Event(ForumCommentError.POST_NOT_FOUND)
            }

            else -> _commentErrorEvents.value = Event(ForumCommentError.SUBMIT_FAILED)
        }
    }

    private fun sortComments(comments: List<ForumComment>): List<ForumComment> {
        return comments
            .distinctBy { it.id }
            .sortedWith(compareBy<ForumComment> { it.createdAt }.thenBy { it.id })
    }

    private fun Long.toCommentCount(): Int {
        return coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }

    private fun handleApiError(exception: ApiException) {
        if (exception.code == HTTP_UNAUTHORIZED ||
            exception.httpStatus == HTTP_UNAUTHORIZED
        ) {
            emitLoginRequired()
            return
        }

        val error = when {
            exception.code == HTTP_NOT_FOUND ||
                exception.httpStatus == HTTP_NOT_FOUND -> ForumDetailLoadError.NOT_FOUND

            exception.code == HTTP_SERVER_ERROR ||
                exception.httpStatus == HTTP_SERVER_ERROR -> ForumDetailLoadError.SERVER

            else -> ForumDetailLoadError.UNKNOWN
        }
        _uiState.value = ForumPostDetailUiState(loadError = error)
    }

    private fun handleInteractionApiError(
        exception: ApiException,
        fallbackError: ForumInteractionError
    ) {
        when {
            exception.code == HTTP_UNAUTHORIZED ||
                exception.httpStatus == HTTP_UNAUTHORIZED -> emitLoginRequired()

            exception.code == HTTP_NOT_FOUND ||
                exception.httpStatus == HTTP_NOT_FOUND -> {
                val currentState = _uiState.value ?: ForumPostDetailUiState()
                _uiState.value = currentState.copy(
                    post = null,
                    loadError = ForumDetailLoadError.NOT_FOUND,
                    isLikeSubmitting = false,
                    isFavoriteSubmitting = false
                )
                _interactionErrorEvents.value = Event(ForumInteractionError.POST_NOT_FOUND)
            }

            else ->
                _interactionErrorEvents.value = Event(fallbackError)
        }
    }

    private fun emitLoginRequired() {
        if (isLoginRequiredEmitted) return
        isLoginRequiredEmitted = true
        sessionManager.clearSession()
        _uiState.value = ForumPostDetailUiState()
        _loginRequiredEvents.value = Event(Unit)
    }

    companion object {
        private const val INVALID_POST_ID = 0L
        private const val FIRST_COMMENT_PAGE = 1
        private const val COMMENT_PAGE_SIZE = 20
        private const val MAX_COMMENT_LENGTH = 500
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_SERVER_ERROR = 500

        /**
         * 创建带帖子编号和仓库依赖的 ViewModel 工厂。
         *
         * @param postId 帖子编号
         * @param forumRepository 论坛数据仓库
         * @param sessionManager 登录会话
         * @return ViewModel 工厂
         */
        fun factory(
            postId: Long,
            forumRepository: ForumRepository,
            sessionManager: SessionManager
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(ForumPostDetailViewModel::class.java)) {
                        return ForumPostDetailViewModel(
                            postId = postId,
                            forumRepository = forumRepository,
                            sessionManager = sessionManager
                        ) as T
                    }
                    throw IllegalArgumentException(
                        "Unknown ViewModel class: ${modelClass.name}"
                    )
                }
            }
    }
}
