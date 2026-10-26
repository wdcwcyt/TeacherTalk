package com.zl.teachertalk.model.forum

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zl.teachertalk.bean.CreatePostError
import com.zl.teachertalk.bean.CreatePostUiState
import com.zl.teachertalk.net.ApiException
import com.zl.teachertalk.net.ForumRepository
import com.zl.teachertalk.utils.Event
import com.zl.teachertalk.utils.PostImageCompressionException
import com.zl.teachertalk.utils.PostImageCompressor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 发布帖子流程控制：校验输入、压缩图片、上传图片并提交帖子。
 *
 * @param forumRepository 论坛数据仓库
 * @param imageCompressor 图片压缩器
 */
class CreatePostViewModel(
    private val forumRepository: ForumRepository,
    private val imageCompressor: PostImageCompressor
) : ViewModel() {

    private val _uiState = MutableLiveData(CreatePostUiState())

    /**
     * 发布页界面状态。
     */
    val uiState: LiveData<CreatePostUiState> = _uiState

    private val _errorEvents = MutableLiveData<Event<CreatePostError>>()

    /**
     * 发布失败时的一次性提示。
     */
    val errorEvents: LiveData<Event<CreatePostError>> = _errorEvents

    private val _postCreatedEvents = MutableLiveData<Event<Unit>>()

    /**
     * 帖子发布成功后的一次性事件。
     */
    val postCreatedEvents: LiveData<Event<Unit>> = _postCreatedEvents

    /**
     * 发布帖子。
     *
     * @param title 标题
     * @param content 正文
     * @param imageUris 已选择的本地图片
     */
    fun publishPost(title: String, content: String, imageUris: List<Uri>) {
        if (_uiState.value?.isSubmitting == true) return

        val normalizedTitle = title.trim()
        val normalizedContent = content.trim()
        val validationError = when {
            normalizedContent.isEmpty() -> CreatePostError.CONTENT_REQUIRED
            imageUris.size > MAX_IMAGE_COUNT -> CreatePostError.TOO_MANY_IMAGES
            else -> null
        }
        if (validationError != null) {
            _errorEvents.value = Event(validationError)
            return
        }

        _uiState.value = CreatePostUiState(isSubmitting = true)
        viewModelScope.launch {
            var isCreatingPost = false
            try {
                val uploadedUrls = mutableListOf<String>()
                imageUris.forEach { uri ->
                    val file = imageCompressor.compress(uri)
                    try {
                        uploadedUrls.add(forumRepository.uploadImage(file).url)
                    } finally {
                        file.delete()
                    }
                }

                isCreatingPost = true
                forumRepository.createPost(
                    title = normalizedTitle.ifEmpty { null },
                    content = normalizedContent,
                    imageUrls = uploadedUrls
                )
                _postCreatedEvents.value = Event(Unit)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: PostImageCompressionException) {
                _errorEvents.value = Event(exception.toCreatePostError())
            } catch (exception: ApiException) {
                _errorEvents.value = Event(
                    when {
                        exception.code == HTTP_UNAUTHORIZED -> CreatePostError.SESSION_EXPIRED
                        isCreatingPost && exception.code == HTTP_BAD_REQUEST ->
                            CreatePostError.INVALID_POST

                        isCreatingPost -> CreatePostError.PUBLISH_FAILED
                        else -> CreatePostError.IMAGE_UPLOAD_FAILED
                    }
                )
            } catch (exception: Exception) {
                _errorEvents.value = Event(
                    if (isCreatingPost) {
                        CreatePostError.PUBLISH_FAILED
                    } else {
                        CreatePostError.IMAGE_UPLOAD_FAILED
                    }
                )
            } finally {
                _uiState.value = CreatePostUiState(isSubmitting = false)
            }
        }
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

        /**
         * 创建带论坛仓库和图片压缩器的 ViewModel 工厂。
         *
         * @param forumRepository 论坛数据仓库
         * @param imageCompressor 图片压缩器
         * @return ViewModel 工厂
         */
        fun factory(
            forumRepository: ForumRepository,
            imageCompressor: PostImageCompressor
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(CreatePostViewModel::class.java)) {
                    return CreatePostViewModel(forumRepository, imageCompressor) as T
                }
                throw IllegalArgumentException(
                    "Unknown ViewModel class: ${modelClass.name}"
                )
            }
        }
    }
}
