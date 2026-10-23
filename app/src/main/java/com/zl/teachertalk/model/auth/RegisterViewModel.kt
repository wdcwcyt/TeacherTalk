package com.zl.teachertalk.model.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zl.teachertalk.bean.AuthError
import com.zl.teachertalk.bean.AuthUiState
import com.zl.teachertalk.bean.RegisterRequest
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
 * 注册页流程控制：请求验证码、提交家长账号注册并保存会话。
 *
 * @param authRepository 认证数据仓库
 * @param sessionManager 登录会话存储
 */
class RegisterViewModel(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableLiveData(AuthUiState())

    /**
     * 注册页按钮和倒计时状态。
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

    private val _registeredEvents = MutableLiveData<Event<Unit>>()

    /**
     * 注册成功后的跳转事件。
     */
    val registeredEvents: LiveData<Event<Unit>> = _registeredEvents

    private var countdownJob: Job? = null

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
     * 使用手机号和验证码注册家长账号。
     *
     * @param phone 用户输入的手机号
     * @param code 用户输入的验证码
     */
    fun register(phone: String, code: String) {
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
                val result = authRepository.register(
                    RegisterRequest(
                        phone = normalizedPhone,
                        code = normalizedCode,
                        nickname = null,
                        role = DEFAULT_ROLE
                    )
                )
                sessionManager.saveSession(result.token, result.user)
                _registeredEvents.value = Event(Unit)
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
                    if (modelClass.isAssignableFrom(RegisterViewModel::class.java)) {
                        return RegisterViewModel(authRepository, sessionManager) as T
                    }
                    throw IllegalArgumentException(
                        "Unknown ViewModel class: ${modelClass.name}"
                    )
                }
            }

        private val PHONE_REGEX = Regex("^1[3-9]\\d{9}$")
        private val CODE_REGEX = Regex("^\\d{6}$")
        private const val COUNTDOWN_INTERVAL_MILLIS = 1_000L

        // 当前注册页不展示身份选择，按产品决定默认注册为家长。
        private const val DEFAULT_ROLE = "PARENT"
    }
}
