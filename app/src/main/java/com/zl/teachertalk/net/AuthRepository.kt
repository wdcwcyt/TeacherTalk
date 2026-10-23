package com.zl.teachertalk.net

import com.zl.teachertalk.bean.ApiResponse
import com.zl.teachertalk.bean.AuthResult
import com.zl.teachertalk.bean.LoginRequest
import com.zl.teachertalk.bean.RegisterRequest
import com.zl.teachertalk.bean.SendSmsCodeRequest
import com.zl.teachertalk.bean.SendSmsCodeResponse
import com.zl.teachertalk.bean.UserInfoBean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

/**
 * 认证数据仓库：负责调用短信认证接口并把统一响应转换为业务结果。
 *
 * @param apiService Retrofit 接口
 */
class AuthRepository(
    private val apiService: ApiService
) {

    /**
     * 请求短信验证码。
     *
     * @param request 手机号请求体
     * @return 验证码有效期和重发间隔
     */
    suspend fun sendSmsCode(request: SendSmsCodeRequest): SendSmsCodeResponse =
        withContext(Dispatchers.IO) {
            apiService.sendSmsCode(request).requireData()
        }

    /**
     * 使用手机号和验证码登录。
     *
     * @param request 登录请求体
     * @return 认证 token 和用户信息
     */
    suspend fun login(request: LoginRequest): AuthResult =
        withContext(Dispatchers.IO) {
            apiService.login(request).requireData()
        }

    /**
     * 使用手机号和验证码注册。
     *
     * @param request 注册请求体
     * @return 认证 token 和用户信息
     */
    suspend fun register(request: RegisterRequest): AuthResult =
        withContext(Dispatchers.IO) {
            apiService.register(request).requireData()
        }

    /**
     * 使用当前会话 token 获取用户信息，同时由服务端校验 token 是否有效。
     *
     * @return 当前登录用户
     */
    suspend fun getCurrentUser(): UserInfoBean =
        withContext(Dispatchers.IO) {
            apiService.getCurrentUser().requireData()
        }

    private fun <T> Response<ApiResponse<T>>.requireData(): T {
        val responseBody = body()
        if (!isSuccessful || responseBody == null || responseBody.code != 0) {
            throw ApiException(
                code = responseBody?.code ?: code(),
                message = responseBody?.message?.takeIf { it.isNotBlank() } ?: message(),
                httpStatus = code()
            )
        }
        return responseBody.data ?: throw ApiException(
            code = UNKNOWN_RESPONSE_CODE,
            message = "Response data is empty",
            httpStatus = code()
        )
    }

    private companion object {
        const val UNKNOWN_RESPONSE_CODE = -1
    }
}
