package com.zl.teachertalk.model.forum

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zl.teachertalk.bean.ForumDetailLoadError
import com.zl.teachertalk.bean.ForumPostDetailUiState
import com.zl.teachertalk.net.ApiException
import com.zl.teachertalk.net.ForumRepository
import com.zl.teachertalk.net.SessionManager
import com.zl.teachertalk.utils.Event
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 帖子详情页流程控制：加载详情，并把加载失败转换成界面可展示的状态。
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

    private fun emitLoginRequired() {
        sessionManager.clearSession()
        _uiState.value = ForumPostDetailUiState()
        _loginRequiredEvents.value = Event(Unit)
    }

    companion object {
        private const val INVALID_POST_ID = 0L
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
