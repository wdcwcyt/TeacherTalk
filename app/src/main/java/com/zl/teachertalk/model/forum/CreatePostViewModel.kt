package com.zl.teachertalk.model.forum

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zl.teachertalk.bean.CreatePostError
import com.zl.teachertalk.bean.CreatePostUiState
import com.zl.teachertalk.bean.PostEditorImage
import com.zl.teachertalk.bean.PostEditorLoadError
import com.zl.teachertalk.net.ApiException
import com.zl.teachertalk.net.ForumRepository
import com.zl.teachertalk.net.SessionManager
import com.zl.teachertalk.utils.Event
import com.zl.teachertalk.utils.PostImageCompressionException
import com.zl.teachertalk.utils.PostImageCompressor
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 创建和编辑帖子共用的流程控制：读取原帖、上传新增图片并提交帖子。
 *
 * @param forumRepository 论坛数据仓库
 * @param imageCompressor 图片压缩器
 * @param sessionManager 登录会话
 * @param postId 编辑模式下的帖子编号；创建模式为 null
 */
class CreatePostViewModel(
    private val forumRepository: ForumRepository,
    private val imageCompressor: PostImageCompressor,
    private val sessionManager: SessionManager,
    private val postId: Long?
) : ViewModel() {

    private val _uiState = MutableLiveData(
        CreatePostUiState(isLoadingOriginal = postId != null)
    )

    /**
     * 帖子编辑页界面状态。
     */
    val uiState: LiveData<CreatePostUiState> = _uiState

    private val _errorEvents = MutableLiveData<Event<CreatePostError>>()

    /**
     * 图片处理或保存失败时的一次性提示。
     */
    val errorEvents: LiveData<Event<CreatePostError>> = _errorEvents

    private val _postSavedEvents = MutableLiveData<Event<Unit>>()

    /**
     * 发布或编辑成功后的一次性事件。
     */
    val postSavedEvents: LiveData<Event<Unit>> = _postSavedEvents

    private val _loginRequiredEvents = MutableLiveData<Event<Unit>>()

    /**
     * 未登录或 token 失效时，需要回到登录页的一次性事件。
     */
    val loginRequiredEvents: LiveData<Event<Unit>> = _loginRequiredEvents

    private var loadJob: Job? = null
    private var saveJob: Job? = null
    private var isLoginRequiredEmitted = false

    /**
     * 加载待编辑帖子的原始数据；非编辑模式直接忽略。
     */
    fun loadOriginalPost() {
        val editPostId = postId ?: return
        val state = _uiState.value ?: return
        if (state.originalPost != null || loadJob?.isActive == true) return
        requestOriginalPost(editPostId)
    }

    /**
     * 原帖加载失败后重试。
     */
    fun retryLoadOriginalPost() {
        val editPostId = postId ?: return
        if (loadJob?.isActive == true) return
        requestOriginalPost(editPostId)
    }

    /**
     * 发布新帖子或保存编辑后的帖子。
     *
     * @param title 标题
     * @param content 正文
     * @param images 已有远程图片和新选本地图片组成的有序草稿
     */
    fun savePost(
        title: String,
        content: String,
        images: List<PostEditorImage>
    ) {
        val state = _uiState.value ?: return
        if (state.isSubmitting ||
            state.isLoadingOriginal ||
            state.loadError != null ||
            saveJob?.isActive == true
        ) {
            return
        }
        if (!sessionManager.isLoggedIn) {
            emitLoginRequired()
            return
        }

        val normalizedTitle = title.trim()
        val normalizedContent = content.trim()
        val validationError = when {
            normalizedContent.isEmpty() -> CreatePostError.CONTENT_REQUIRED
            images.size > MAX_IMAGE_COUNT -> CreatePostError.TOO_MANY_IMAGES
            else -> null
        }
        if (validationError != null) {
            _errorEvents.value = Event(validationError)
            return
        }

        val editingPostId = postId
        _uiState.value = state.copy(isSubmitting = true)
        saveJob = viewModelScope.launch {
            var isSavingPost = false
            try {
                val imageUrls = mutableListOf<String>()
                images.forEach { image ->
                    when (image) {
                        is PostEditorImage.Existing -> imageUrls.add(image.url)
                        is PostEditorImage.Local -> {
                            val file = imageCompressor.compress(Uri.parse(image.uriString))
                            try {
                                imageUrls.add(forumRepository.uploadImage(file).url)
                            } finally {
                                file.delete()
                            }
                        }
                    }
                }

                isSavingPost = true
                if (editingPostId == null) {
                    forumRepository.createPost(
                        title = normalizedTitle.ifEmpty { null },
                        content = normalizedContent,
                        imageUrls = imageUrls
                    )
                } else {
                    forumRepository.updatePost(
                        postId = editingPostId,
                        title = normalizedTitle.ifEmpty { null },
                        content = normalizedContent,
                        imageUrls = imageUrls
                    )
                }
                _postSavedEvents.value = Event(Unit)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: PostImageCompressionException) {
                _errorEvents.value = Event(exception.toCreatePostError())
            } catch (exception: ApiException) {
                handleSaveApiError(exception, isSavingPost, editingPostId)
            } catch (exception: IOException) {
                _errorEvents.value = Event(
                    if (isSavingPost) {
                        saveFailedError(editingPostId)
                    } else {
                        CreatePostError.IMAGE_UPLOAD_FAILED
                    }
                )
            } catch (exception: Exception) {
                _errorEvents.value = Event(
                    if (isSavingPost) {
                        saveFailedError(editingPostId)
                    } else {
                        CreatePostError.IMAGE_UPLOAD_FAILED
                    }
                )
            } finally {
                val currentState = _uiState.value
                if (currentState != null) {
                    _uiState.value = currentState.copy(isSubmitting = false)
                }
                saveJob = null
            }
        }
    }

    private fun requestOriginalPost(editPostId: Long) {
        if (!sessionManager.isLoggedIn) {
            emitLoginRequired()
            return
        }

        val currentState = _uiState.value ?: CreatePostUiState()
        _uiState.value = currentState.copy(
            isLoadingOriginal = true,
            loadError = null
        )
        loadJob = viewModelScope.launch {
            try {
                val post = forumRepository.loadPostDetail(editPostId)
                val currentUser = sessionManager.userInfo
                if (currentUser == null) {
                    emitLoginRequired()
                    return@launch
                }
                if (post.authorId != currentUser.id) {
                    _uiState.value = (_uiState.value ?: CreatePostUiState()).copy(
                        isLoadingOriginal = false,
                        loadError = PostEditorLoadError.NO_PERMISSION
                    )
                    return@launch
                }
                _uiState.value = (_uiState.value ?: CreatePostUiState()).copy(
                    isLoadingOriginal = false,
                    loadError = null,
                    originalPost = post
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: ApiException) {
                handleLoadApiError(exception)
            } catch (exception: IOException) {
                updateLoadError(PostEditorLoadError.NETWORK)
            } catch (exception: Exception) {
                updateLoadError(PostEditorLoadError.UNKNOWN)
            } finally {
                loadJob = null
            }
        }
    }

    private fun handleLoadApiError(exception: ApiException) {
        when {
            exception.code == HTTP_UNAUTHORIZED ||
                exception.httpStatus == HTTP_UNAUTHORIZED -> emitLoginRequired()

            exception.code == HTTP_NOT_FOUND ||
                exception.httpStatus == HTTP_NOT_FOUND ->
                updateLoadError(PostEditorLoadError.NOT_FOUND)

            exception.code == HTTP_SERVER_ERROR ||
                exception.httpStatus == HTTP_SERVER_ERROR ->
                updateLoadError(PostEditorLoadError.SERVER)

            else -> updateLoadError(PostEditorLoadError.UNKNOWN)
        }
    }

    private fun updateLoadError(error: PostEditorLoadError) {
        val currentState = _uiState.value ?: CreatePostUiState()
        _uiState.value = currentState.copy(
            isLoadingOriginal = false,
            loadError = error
        )
    }

    private fun handleSaveApiError(
        exception: ApiException,
        isSavingPost: Boolean,
        editingPostId: Long?
    ) {
        val error = when {
            exception.code == HTTP_UNAUTHORIZED ||
                exception.httpStatus == HTTP_UNAUTHORIZED -> {
                emitLoginRequired()
                return
            }

            exception.code == HTTP_FORBIDDEN ||
                exception.httpStatus == HTTP_FORBIDDEN -> CreatePostError.NO_PERMISSION

            exception.code == HTTP_NOT_FOUND ||
                exception.httpStatus == HTTP_NOT_FOUND -> CreatePostError.POST_NOT_FOUND

            !isSavingPost -> CreatePostError.IMAGE_UPLOAD_FAILED

            exception.code == HTTP_BAD_REQUEST ||
                exception.httpStatus == HTTP_BAD_REQUEST -> CreatePostError.INVALID_POST

            else -> saveFailedError(editingPostId)
        }
        _errorEvents.value = Event(error)
    }

    private fun saveFailedError(editingPostId: Long?): CreatePostError {
        return if (editingPostId == null) {
            CreatePostError.PUBLISH_FAILED
        } else {
            CreatePostError.UPDATE_FAILED
        }
    }

    private fun emitLoginRequired() {
        if (isLoginRequiredEmitted) return
        isLoginRequiredEmitted = true
        sessionManager.clearSession()
        _uiState.value = CreatePostUiState()
        _loginRequiredEvents.value = Event(Unit)
    }

    private fun PostImageCompressionException.toCreatePostError(): CreatePostError {
        return when (reason) {
            PostImageCompressor.Reason.DECODE_FAILED -> CreatePostError.IMAGE_DECODE_FAILED
            PostImageCompressor.Reason.WRITE_FAILED -> CreatePostError.IMAGE_WRITE_FAILED
            PostImageCompressor.Reason.TOO_LARGE -> CreatePostError.IMAGE_TOO_LARGE
        }
    }

    companion object {
        private const val MAX_IMAGE_COUNT = 5
        private const val HTTP_BAD_REQUEST = 400
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_SERVER_ERROR = 500

        /**
         * 创建带仓库、图片压缩器、会话和可选帖子编号的 ViewModel 工厂。
         *
         * @param forumRepository 论坛数据仓库
         * @param imageCompressor 图片压缩器
         * @param sessionManager 登录会话
         * @param postId 编辑模式帖子编号；创建模式传 null
         * @return ViewModel 工厂
         */
        fun factory(
            forumRepository: ForumRepository,
            imageCompressor: PostImageCompressor,
            sessionManager: SessionManager,
            postId: Long?
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(CreatePostViewModel::class.java)) {
                    return CreatePostViewModel(
                        forumRepository = forumRepository,
                        imageCompressor = imageCompressor,
                        sessionManager = sessionManager,
                        postId = postId
                    ) as T
                }
                throw IllegalArgumentException(
                    "Unknown ViewModel class: ${modelClass.name}"
                )
            }
        }
    }
}
