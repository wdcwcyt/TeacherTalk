package com.zl.teachertalk

import android.content.Context
import android.content.pm.ApplicationInfo
import com.zl.teachertalk.net.ApiService
import com.zl.teachertalk.net.AuthRepository
import com.zl.teachertalk.net.ForumRepository
import com.zl.teachertalk.net.NetworkClient
import com.zl.teachertalk.net.SessionManager
import com.zl.teachertalk.utils.PostImageCompressor
import com.zl.teachertalk.utils.SPUtils

/**
 * 手动依赖注入容器：全局单例只在这里创建一次，页面和 ViewModel 通过构造函数拿依赖。
 *
 * 这里统一创建配置存储、登录会话、网络接口和认证仓库，避免页面重复创建。
 */
class AppContainer(context: Context) {

    private val isDebug: Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /**
     * 普通配置，默认使用 app_sp_config 文件。
     */
    val spUtils: SPUtils = SPUtils(context)

    /**
     * 会话专用存储，和普通配置分开，退出登录不会误删别的设置。
     */
    private val sessionPreferences: SPUtils = SPUtils(context, SessionManager.FILE_NAME)

    /**
     * 登录会话的唯一实例。
     */
    val sessionManager: SessionManager = SessionManager(
        spUtils = sessionPreferences,
        isDebug = isDebug
    )

    /**
     * Retrofit 接口的唯一实例，自动为已登录请求附加 JWT。
     */
    val apiService: ApiService = NetworkClient.createApiService(
        sessionManager = sessionManager,
        isDebug = isDebug
    )

    /**
     * 认证数据仓库，统一处理验证码、登录和注册接口的响应。
     */
    val authRepository: AuthRepository = AuthRepository(apiService)

    /**
     * 论坛数据仓库。
     */
    val forumRepository: ForumRepository = ForumRepository(apiService)

    /**
     * 发帖图片压缩器，使用 Application Context 避免持有页面。
     */
    val postImageCompressor: PostImageCompressor = PostImageCompressor(context.applicationContext)
}
