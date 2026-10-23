package com.zl.teachertalk.net

import okhttp3.Interceptor
import okhttp3.Response

/**
 * 在已登录请求上动态附加 JWT。
 *
 * 每次请求都重新读取 [SessionManager]，所以登录、退出后不需要重建 OkHttpClient。
 *
 * @param sessionManager 当前会话的唯一实例
 */
class AuthInterceptor(
    private val sessionManager: SessionManager
) : Interceptor {

    /**
     * 有 token 时添加 `Authorization: Bearer <token>`。
     *
     * 获取验证码、登录和注册属于公开接口，不携带旧 token，避免过期会话影响重新登录。
     */
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = sessionManager.token
        val authorizedRequest = if (
            token.isNullOrBlank() || request.url.encodedPath in PUBLIC_PATHS
        ) {
            request
        } else {
            request
                .newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }
        return chain.proceed(authorizedRequest)
    }

    private companion object {
        val PUBLIC_PATHS = setOf(
            "/api/auth/sms-code",
            "/api/auth/login",
            "/api/auth/register"
        )
    }
}
