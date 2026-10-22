package com.zl.teachertalk.net

import com.zl.teachertalk.bean.ApiResponse
import com.zl.teachertalk.bean.AuthRequest
import com.zl.teachertalk.bean.AuthResult
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * 服务端接口声明。
 *
 * 使用 `Response<ApiResponse<T>>` 可以同时拿到 HTTP 状态和统一响应体，所有接口都通过挂起函数调用。
 */
interface ApiService {

    /**
     * 使用账号和密码登录。
     *
     * @param request 账号密码请求体
     * @return HTTP 响应及统一业务数据
     */
    @POST("api/auth/login")
    suspend fun login(@Body request: AuthRequest): Response<ApiResponse<AuthResult>>

    /**
     * 使用账号和密码注册。
     *
     * @param request 账号密码请求体
     * @return HTTP 响应及统一业务数据
     */
    @POST("api/auth/register")
    suspend fun register(@Body request: AuthRequest): Response<ApiResponse<AuthResult>>
}
