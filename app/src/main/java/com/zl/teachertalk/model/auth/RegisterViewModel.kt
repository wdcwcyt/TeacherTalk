package com.zl.teachertalk.model.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zl.teachertalk.bean.AuthRequest
import com.zl.teachertalk.bean.RegisterError
import com.zl.teachertalk.bean.RegisterUiState
import com.zl.teachertalk.net.ApiException
import com.zl.teachertalk.net.AuthRepository
import com.zl.teachertalk.utils.Event
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 注册页的流程控制：校验输入、调用注册仓库、发出成功或失败事件。
 *
 * @param authRepository 认证数据仓库
 */
class RegisterViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableLiveData(RegisterUiState())

    /**
     * 注册按钮加载状态。
     */
    val uiState: LiveData<RegisterUiState> = _uiState

    private val _errorEvents = MutableLiveData<Event<RegisterError>>()

    /**
     * 只消费一次的错误提示事件。
     */
    val errorEvents: LiveData<Event<RegisterError>> = _errorEvents

    private val _registeredEvents = MutableLiveData<Event<Unit>>()

    /**
     * 注册成功后的一次性导航事件。
     */
    val registeredEvents: LiveData<Event<Unit>> = _registeredEvents

    /**
     * 提交账号注册。
     *
     * @param account 账号
     * @param password 密码
     * @param confirmPassword 确认密码
     */
    fun registerWithAccount(
        account: String,
        password: String,
        confirmPassword: String
    ) {
        if (_uiState.value?.isSubmitting == true) return

        val normalizedAccount = account.trim()
        val validationError = when {
            normalizedAccount.isBlank() || password.isBlank() || confirmPassword.isBlank() ->
                RegisterError.EMPTY_FIELDS

            password != confirmPassword ->
                RegisterError.PASSWORD_MISMATCH

            else -> null
        }
        if (validationError != null) {
            _errorEvents.value = Event(validationError)
            return
        }

        _uiState.value = RegisterUiState(isSubmitting = true)
        viewModelScope.launch {
            try {
                authRepository.register(
                    AuthRequest(
                        account = normalizedAccount,
                        password = password
                    )
                )
                _registeredEvents.value = Event(Unit)
            } catch (exception: ApiException) {
                _errorEvents.value = Event(mapError(exception))
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _errorEvents.value = Event(RegisterError.UNKNOWN)
            } finally {
                _uiState.value = RegisterUiState(isSubmitting = false)
            }
        }
    }

    private fun mapError(exception: ApiException): RegisterError = when (exception.code) {
        400 -> RegisterError.EMPTY_FIELDS
        409 -> RegisterError.ACCOUNT_EXISTS
        else -> RegisterError.UNKNOWN
    }

    companion object {
        /**
         * 创建带 [AuthRepository] 依赖的 ViewModel 工厂。
         *
         * @param authRepository 认证数据仓库
         * @return ViewModel 工厂
         */
        fun factory(authRepository: AuthRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(RegisterViewModel::class.java)) {
                        return RegisterViewModel(authRepository) as T
                    }
                    throw IllegalArgumentException(
                        "Unknown ViewModel class: ${modelClass.name}"
                    )
                }
            }
    }
}
