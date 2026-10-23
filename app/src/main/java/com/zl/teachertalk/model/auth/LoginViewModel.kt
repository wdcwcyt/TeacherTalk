package com.zl.teachertalk.model.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zl.teachertalk.bean.AuthError
import com.zl.teachertalk.bean.AuthUiState
import com.zl.teachertalk.bean.LoginRequest
import com.zl.teachertalk.bean.SendSmsCodeRequest
import com.zl.teachertalk.net.ApiException
import com.zl.teachertalk.net.AuthRepository
import com.zl.teachertalk.net.SessionManager
import com.zl.teachertalk.utils.Event
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 登录页流程控制：请求验证码、提交登录、保存会话和通知跳转。
 *
 * @param authRepository 认证数据仓库
 * @param sessionManager 登录会话存储
 */
class LoginViewModel(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableLiveData(AuthUiState())

    /**
     * 登录页按钮和倒计时状态。
     */
    val uiState: LiveData<AuthUiState> = _uiState

    private val _errorEvents = MutableLiveData<Event<AuthError>>()

    /**
     * 需要展示一次的错误提示。
     */
    val errorEvents: LiveData<Event<AuthError>> = _errorEvents

    private val _codeSentEvents = MutableLiveData<Event<Unit>>()

    /**
     * 验证码发送成功后的一次性提示。
     */
    val codeSentEvents: LiveData<Event<Unit>> = _codeSentEvents

    private val _authenticatedEvents = MutableLiveData<Event<Unit>>()

    /**
     * 登录成功后的跳转事件。
     */
    val authenticatedEvents: LiveData<Event<Unit>> = _authenticatedEvents

    private var countdownJob: Job? = null
    private var hasStartedSessionCheck = false

    /**
     * 启动时校验本地保存的 token，有效则直接进入主页。
     */
    fun checkSavedSession() {
        if (hasStartedSessionCheck) return
        hasStartedSessionCheck = true
        if (!sessionManager.isLoggedIn) return

        updateUiState { it.copy(isCheckingSession = true) }
        viewModelScope.launch {
            try {
                val userInfo = authRepository.getCurrentUser()
                sessionManager.updateUserInfo(userInfo)
                _authenticatedEvents.value = Event(Unit)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: ApiException) {
                if (exception.code == 401 || exception.code == 404) {
                    sessionManager.clearSession()
                    _errorEvents.value = Event(AuthError.SESSION_EXPIRED)
                } else {
                    emitError(exception)
                }
            } catch (exception: Exception) {
                emitError(exception)
            } finally {
                updateUiState { it.copy(isCheckingSession = false) }
            }
        }
    }

    /**
     * 请求短信验证码。
     *
     * @param phone 用户输入的手机号
     */
    fun sendSmsCode(phone: String) {
        val state = _uiState.value ?: AuthUiState()
        if (state.isRequestingCode || state.isSubmitting || state.codeCooldownSeconds > 0) {
            return
        }

        val normalizedPhone = phone.trim()
        if (!PHONE_REGEX.matches(normalizedPhone)) {
            _errorEvents.value = Event(AuthError.INVALID_PHONE)
            return
        }

        updateUiState { it.copy(isRequestingCode = true) }
        viewModelScope.launch {
            try {
                val response = authRepository.sendSmsCode(
                    SendSmsCodeRequest(phone = normalizedPhone)
                )
                _codeSentEvents.value = Event(Unit)
                startCodeCountdown(response.resendAfter)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                emitError(exception)
            } finally {
                updateUiState { it.copy(isRequestingCode = false) }
            }
        }
    }

    /**
     * 使用手机号和验证码登录。
     *
     * @param phone 用户输入的手机号
     * @param code 用户输入的验证码
     */
    fun login(phone: String, code: String) {
        val state = _uiState.value ?: AuthUiState()
        if (state.isRequestingCode || state.isSubmitting) return

        val normalizedPhone = phone.trim()
        val normalizedCode = code.trim()
        val validationError = when {
            !PHONE_REGEX.matches(normalizedPhone) -> AuthError.INVALID_PHONE
            !CODE_REGEX.matches(normalizedCode) -> AuthError.INVALID_CODE
            else -> null
        }
        if (validationError != null) {
            _errorEvents.value = Event(validationError)
            return
        }

        updateUiState { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            try {
                val result = authRepository.login(
                    LoginRequest(
                        phone = normalizedPhone,
                        code = normalizedCode
                    )
                )
                sessionManager.saveSession(result.token, result.user)
                _authenticatedEvents.value = Event(Unit)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                emitError(exception)
            } finally {
                updateUiState { it.copy(isSubmitting = false) }
            }
        }
    }

    private fun startCodeCountdown(resendAfterSeconds: Long) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var remainingSeconds = resendAfterSeconds
                .coerceIn(0L, Int.MAX_VALUE.toLong())
                .toInt()
            updateUiState { it.copy(codeCooldownSeconds = remainingSeconds) }
            while (remainingSeconds > 0) {
                delay(COUNTDOWN_INTERVAL_MILLIS)
                remainingSeconds--
                updateUiState { it.copy(codeCooldownSeconds = remainingSeconds) }
            }
        }
    }

    private fun emitError(exception: Exception) {
        val error = when (exception) {
            is ApiException -> when (exception.code) {
                400 -> AuthError.INVALID_INPUT
                401 -> AuthError.CODE_INVALID_OR_EXPIRED
                404 -> AuthError.PHONE_NOT_REGISTERED
                409 -> AuthError.PHONE_ALREADY_REGISTERED
                500 -> AuthError.SERVER
                else -> AuthError.UNKNOWN
            }

            is IOException -> AuthError.NETWORK
            else -> AuthError.UNKNOWN
        }
        _errorEvents.value = Event(error)
    }

    private fun updateUiState(transform: (AuthUiState) -> AuthUiState) {
        _uiState.value = transform(_uiState.value ?: AuthUiState())
    }

    companion object {
        /**
         * 创建带认证依赖的 ViewModel 工厂。
         *
         * @param authRepository 认证数据仓库
         * @param sessionManager 登录会话存储
         * @return ViewModel 工厂
         */
        fun factory(
            authRepository: AuthRepository,
            sessionManager: SessionManager
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
                        return LoginViewModel(authRepository, sessionManager) as T
                    }
                    throw IllegalArgumentException(
                        "Unknown ViewModel class: ${modelClass.name}"
                    )
                }
            }

        private val PHONE_REGEX = Regex("^1[3-9]\\d{9}$")
        private val CODE_REGEX = Regex("^\\d{6}$")
        private const val COUNTDOWN_INTERVAL_MILLIS = 1_000L
    }
}
