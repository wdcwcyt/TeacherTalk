package com.zl.teachertalk.net

import com.zl.teachertalk.bean.AuthRequest
import com.zl.teachertalk.bean.AuthResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 认证数据仓库。
 *
 * 当前由本地账号库提供结果；后端完成后在保持方法签名不变的前提下，替换为调用 [ApiService]。
 *
 * @param localAuthStore 后端完成前使用的本地账号库
 */
class AuthRepository(
    private val localAuthStore: LocalAuthStore
) {

    /**
     * 注册账号。
     *
     * @param request 账号密码请求体
     * @return 认证结果
     */
    suspend fun register(request: AuthRequest): AuthResult = withContext(Dispatchers.IO) {
        localAuthStore.register(request)
    }

    /**
     * 登录账号。
     *
     * @param request 账号密码请求体
     * @return 认证结果
     */
    suspend fun login(request: AuthRequest): AuthResult = withContext(Dispatchers.IO) {
        localAuthStore.login(request)
    }
}
