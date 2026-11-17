package com.zl.teachertalk.model.forum

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zl.teachertalk.bean.ForumLoadError
import com.zl.teachertalk.bean.ForumPostSource
import com.zl.teachertalk.bean.ForumUiState
import com.zl.teachertalk.net.ApiException
import com.zl.teachertalk.net.ForumRepository
import com.zl.teachertalk.net.SessionManager
import com.zl.teachertalk.utils.Event
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 帖子列表的加载流程控制，可复用于论坛、我的帖子和我的收藏。
 *
 * 首页每次加载 [PAGE_SIZE] 条；滚动到距离列表末尾 [PREFETCH_DISTANCE] 条时加载下一页。
 * 自动刷新至少间隔 [AUTO_REFRESH_INTERVAL_MS] 毫秒，用户主动下拉不受此限制。
 *
 * @param forumRepository 论坛数据仓库
 * @param sessionManager 登录会话
 * @param postSource 帖子数据来源
 * @param currentTimeMillis 当前时间来源，默认使用系统时间，便于后续做逻辑测试
 */
class ForumViewModel(
    private val forumRepository: ForumRepository,
    private val sessionManager: SessionManager,
    private val postSource: ForumPostSource = ForumPostSource.ALL,
    private val currentTimeMillis: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val _uiState = MutableLiveData(ForumUiState())

    /**
     * 帖子列表界面状态。
     */
    val uiState: LiveData<ForumUiState> = _uiState

    private val _loadErrors = MutableLiveData<Event<ForumLoadError>>()

    /**
     * 刷新或加载更多失败时的一次性提示。
     */
    val loadErrors: LiveData<Event<ForumLoadError>> = _loadErrors

    private val _loginRequiredEvents = MutableLiveData<Event<Unit>>()

    /**
     * token 失效时，需要回到登录页的一次性事件。
     */
    val loginRequiredEvents: LiveData<Event<Unit>> = _loginRequiredEvents

    private var lastRefreshStartedAt = 0L
    private var failedLoadMorePage: Int? = null
    private var refreshJob: Job? = null
    private var loadMoreJob: Job? = null
    private var isLoginRequiredEmitted = false

    /**
     * 页面重新显示时调用。距离上次刷新不足 10 秒时跳过本次自动刷新。
     */
    fun refreshIfStale() {
        val state = _uiState.value ?: return
        if (state.isInitialLoading || state.isRefreshing || state.isLoadingMore) return

        val now = currentTimeMillis()
        if (now - lastRefreshStartedAt < AUTO_REFRESH_INTERVAL_MS) return
        refresh()
    }

    /**
     * 用户主动下拉刷新。始终立即执行，并重新从第 1 页加载。
     */
    fun refreshManually() {
        refresh()
    }

    /**
     * 加载下一页。没有更多数据或已有加载任务时会直接忽略。
     */
    fun loadNextPage() {
        val state = _uiState.value ?: return
        if (!state.hasMore ||
            state.isInitialLoading ||
            state.isRefreshing ||
            state.isLoadingMore
        ) {
            return
        }

        val nextPage = state.currentPage + 1
        if (failedLoadMorePage == nextPage) return

        _uiState.value = state.copy(isLoadingMore = true)
        loadMoreJob = viewModelScope.launch {
            try {
                val page = loadPostPage(
                    page = nextPage,
                    size = PAGE_SIZE
                )
                val oldPosts = _uiState.value?.posts.orEmpty()
                val mergedPosts = (oldPosts + page.list).distinctBy { it.id }
                _uiState.value = (_uiState.value ?: ForumUiState()).copy(
                    posts = mergedPosts,
                    currentPage = page.page,
                    hasMore = page.page.toLong() * page.size < page.total,
                    isLoadingMore = false
                )
                failedLoadMorePage = null
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                if (exception.isUnauthorized()) {
                    emitLoginRequired()
                    return@launch
                }
                _uiState.value = (_uiState.value ?: ForumUiState()).copy(
                    isLoadingMore = false
                )
                failedLoadMorePage = nextPage
                _loadErrors.value = Event(ForumLoadError.LOAD_MORE_FAILED)
            }
        }
    }

    private fun refresh() {
        refreshJob?.cancel()
        loadMoreJob?.cancel()
        failedLoadMorePage = null

        lastRefreshStartedAt = currentTimeMillis()
        val oldState = _uiState.value ?: ForumUiState()
        _uiState.value = oldState.copy(
            isInitialLoading = oldState.posts.isEmpty(),
            isRefreshing = oldState.posts.isNotEmpty(),
            isLoadingMore = false,
            initialLoadFailed = false
        )

        refreshJob = viewModelScope.launch {
            try {
                val page = loadPostPage(
                    page = FIRST_PAGE,
                    size = PAGE_SIZE
                )
                _uiState.value = ForumUiState(
                    posts = page.list.distinctBy { it.id },
                    currentPage = page.page,
                    hasMore = page.page.toLong() * page.size < page.total
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                if (exception.isUnauthorized()) {
                    emitLoginRequired()
                    return@launch
                }
                val currentState = _uiState.value ?: ForumUiState()
                _uiState.value = currentState.copy(
                    isInitialLoading = false,
                    isRefreshing = false,
                    initialLoadFailed = currentState.posts.isEmpty()
                )
                if (currentState.posts.isNotEmpty()) {
                    _loadErrors.value = Event(ForumLoadError.REFRESH_FAILED)
                }
            }
        }
    }

    private suspend fun loadPostPage(page: Int, size: Int) =
        when (postSource) {
            ForumPostSource.ALL -> forumRepository.loadPosts(page, size)
            ForumPostSource.MY_POSTS -> forumRepository.loadMyPosts(page, size)
            ForumPostSource.FAVORITES -> forumRepository.loadFavoritePosts(page, size)
        }

    private fun Exception.isUnauthorized(): Boolean {
        val apiException = this as? ApiException ?: return false
        return apiException.code == HTTP_UNAUTHORIZED ||
            apiException.httpStatus == HTTP_UNAUTHORIZED
    }

    private fun emitLoginRequired() {
        if (isLoginRequiredEmitted) return
        isLoginRequiredEmitted = true
        sessionManager.clearSession()
        _uiState.value = ForumUiState()
        _loginRequiredEvents.value = Event(Unit)
    }

    companion object {
        /**
         * 首次和每次刷新加载的帖子数量。
         */
        const val PAGE_SIZE = 20

        /**
         * 距离列表末尾达到该数量时预加载下一页。
         */
        const val PREFETCH_DISTANCE = 10

        /**
         * 自动刷新的最小间隔。
         */
        const val AUTO_REFRESH_INTERVAL_MS = 10_000L

        private const val FIRST_PAGE = 1
        private const val HTTP_UNAUTHORIZED = 401

        /**
         * 创建带 [ForumRepository] 依赖的 ViewModel 工厂。
         *
         * @param forumRepository 论坛数据仓库
         * @param sessionManager 登录会话
         * @param postSource 帖子数据来源
         * @return ViewModel 工厂
         */
        fun factory(
            forumRepository: ForumRepository,
            sessionManager: SessionManager,
            postSource: ForumPostSource = ForumPostSource.ALL
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(ForumViewModel::class.java)) {
                        return ForumViewModel(
                            forumRepository = forumRepository,
                            sessionManager = sessionManager,
                            postSource = postSource
                        ) as T
                    }
                    throw IllegalArgumentException(
                        "Unknown ViewModel class: ${modelClass.name}"
                    )
                }
            }
    }
}
