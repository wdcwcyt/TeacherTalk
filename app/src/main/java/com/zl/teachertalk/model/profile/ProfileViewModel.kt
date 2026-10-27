package com.zl.teachertalk.model.profile

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zl.teachertalk.bean.ProfileAvatarError
import com.zl.teachertalk.bean.ProfileUiState
import com.zl.teachertalk.net.ApiException
import com.zl.teachertalk.net.AuthRepository
import com.zl.teachertalk.net.SessionManager
import com.zl.teachertalk.utils.Event
import com.zl.teachertalk.utils.PostImageCompressionException
import com.zl.teachertalk.utils.PostImageCompressor
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 个人信息页流程控制：读取当前用户、修改头像并处理退出登录。
 *
 * @param sessionManager 登录会话存储
 * @param authRepository 认证和用户资料仓库
 * @param imageCompressor 图片压缩器
 */
class ProfileViewModel(
    private val sessionManager: SessionManager,
    private val authRepository: AuthRepository,
    private val imageCompressor: PostImageCompressor
) : ViewModel() {

    private val _uiState = MutableLiveData(ProfileUiState())

    /**
     * 个人信息页展示状态。
     */
    val uiState: LiveData<ProfileUiState> = _uiState

    private val _loginRequiredEvents = MutableLiveData<Event<Unit>>()

    /**
     * 未登录、会话不完整或主动退出后，需要回到登录页的一次性事件。
     */
    val loginRequiredEvents: LiveData<Event<Unit>> = _loginRequiredEvents

    private val _avatarErrorEvents = MutableLiveData<Event<ProfileAvatarError>>()

    /**
     * 头像修改失败时的一次性提示。
     */
    val avatarErrorEvents: LiveData<Event<ProfileAvatarError>> = _avatarErrorEvents

    private val _avatarUpdatedEvents = MutableLiveData<Event<Unit>>()

    /**
     * 头像修改成功后的一次性提示。
     */
    val avatarUpdatedEvents: LiveData<Event<Unit>> = _avatarUpdatedEvents

    private var avatarUpdateJob: Job? = null

    /**
     * 从本地会话读取最新用户信息。
     */
    fun loadProfile() {
        val userInfo = sessionManager.userInfo
        if (userInfo == null) {
            _uiState.value = ProfileUiState()
            if (sessionManager.isLoggedIn) {
                sessionManager.clearSession()
            }
            _loginRequiredEvents.value = Event(Unit)
            return
        }
        _uiState.value = (_uiState.value ?: ProfileUiState()).copy(
            userInfo = userInfo
        )
    }

    /**
     * 压缩并上传选中的图片，成功后同步本地会话中的头像地址。
     *
     * @param uri Photo Picker 返回的本地图片地址
     */
    fun updateAvatar(uri: Uri) {
        val currentState = _uiState.value ?: ProfileUiState()
        if (currentState.isUpdatingAvatar) return
        if (sessionManager.userInfo == null) {
            emitLoginRequired()
            return
        }

        _uiState.value = currentState.copy(isUpdatingAvatar = true)
        avatarUpdateJob = viewModelScope.launch {
            var temporaryFile: File? = null
            try {
                temporaryFile = imageCompressor.compress(uri)
                val avatarResponse = authRepository.updateAvatar(temporaryFile)
                val latestUserInfo = sessionManager.userInfo
                if (latestUserInfo == null) {
                    emitLoginRequired()
                    return@launch
                }
                val updatedUserInfo = latestUserInfo.copy(
                    avatarUrl = avatarResponse.avatarUrl
                )
                sessionManager.updateUserInfo(updatedUserInfo)
                _uiState.value = ProfileUiState(userInfo = updatedUserInfo)
                _avatarUpdatedEvents.value = Event(Unit)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: PostImageCompressionException) {
                _avatarErrorEvents.value = Event(exception.toAvatarError())
            } catch (exception: ApiException) {
                handleApiError(exception)
            } catch (exception: IOException) {
                _avatarErrorEvents.value = Event(ProfileAvatarError.NETWORK)
            } catch (exception: Exception) {
                _avatarErrorEvents.value = Event(ProfileAvatarError.UPLOAD_FAILED)
            } finally {
                temporaryFile?.delete()
                val state = _uiState.value ?: ProfileUiState()
                _uiState.value = state.copy(isUpdatingAvatar = false)
                avatarUpdateJob = null
            }
        }
    }

    /**
     * 清除当前登录会话并通知页面返回登录页。
     */
    fun logout() {
        avatarUpdateJob?.cancel()
        avatarUpdateJob = null
        sessionManager.clearSession()
        _uiState.value = ProfileUiState()
        _loginRequiredEvents.value = Event(Unit)
    }

    private fun handleApiError(exception: ApiException) {
        when {
            exception.code == HTTP_UNAUTHORIZED ||
                exception.httpStatus == HTTP_UNAUTHORIZED -> {
                _avatarErrorEvents.value = Event(ProfileAvatarError.SESSION_EXPIRED)
                emitLoginRequired()
            }

            exception.code == HTTP_BAD_REQUEST ||
                exception.httpStatus == HTTP_BAD_REQUEST ->
                _avatarErrorEvents.value = Event(ProfileAvatarError.INVALID_IMAGE)

            exception.code == HTTP_SERVER_ERROR ||
                exception.httpStatus == HTTP_SERVER_ERROR ->
                _avatarErrorEvents.value = Event(ProfileAvatarError.SERVER)

            else ->
                _avatarErrorEvents.value = Event(ProfileAvatarError.UPLOAD_FAILED)
        }
    }

    private fun emitLoginRequired() {
        sessionManager.clearSession()
        _uiState.value = ProfileUiState()
        _loginRequiredEvents.value = Event(Unit)
    }

    private fun PostImageCompressionException.toAvatarError(): ProfileAvatarError {
        return when (reason) {
            PostImageCompressor.Reason.TOO_LARGE -> ProfileAvatarError.IMAGE_TOO_LARGE
            PostImageCompressor.Reason.DECODE_FAILED,
            PostImageCompressor.Reason.WRITE_FAILED -> ProfileAvatarError.INVALID_IMAGE
        }
    }

    companion object {
        /**
         * 创建带会话依赖的 ViewModel 工厂。
         *
         * @param sessionManager 登录会话存储
         * @param authRepository 认证和用户资料仓库
         * @param imageCompressor 图片压缩器
         * @return ViewModel 工厂
         */
        fun factory(
            sessionManager: SessionManager,
            authRepository: AuthRepository,
            imageCompressor: PostImageCompressor
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
                        return ProfileViewModel(
                            sessionManager = sessionManager,
                            authRepository = authRepository,
                            imageCompressor = imageCompressor
                        ) as T
                    }
                    throw IllegalArgumentException(
                        "Unknown ViewModel class: ${modelClass.name}"
                    )
                }
            }

        private const val HTTP_BAD_REQUEST = 400
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_SERVER_ERROR = 500
    }
}
