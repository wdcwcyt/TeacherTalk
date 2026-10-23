package com.zl.teachertalk.net

import com.zl.teachertalk.bean.ApiResponse
import com.zl.teachertalk.bean.AuthResult
import com.zl.teachertalk.bean.LoginRequest
import com.zl.teachertalk.bean.RegisterRequest
import com.zl.teachertalk.bean.SendSmsCodeRequest
import com.zl.teachertalk.bean.SendSmsCodeResponse
import com.zl.teachertalk.bean.UserInfoBean
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * 服务端接口声明。
 *
 * 使用 `Response<ApiResponse<T>>` 可以同时拿到 HTTP 状态和统一响应体，所有接口都通过挂起函数调用。
 */
interface ApiService {

    @POST("api/auth/sms-code")
    suspend fun sendSmsCode(
        @Body request: SendSmsCodeRequest
    ): Response<ApiResponse<SendSmsCodeResponse>>

    @POST("api/auth/register")
    suspend fun register(
        @Body request: RegisterRequest
    ): Response<ApiResponse<AuthResult>>

    @POST("api/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<ApiResponse<AuthResult>>

    @GET("api/auth/me")
    suspend fun getCurrentUser(): Response<ApiResponse<UserInfoBean>>
}
