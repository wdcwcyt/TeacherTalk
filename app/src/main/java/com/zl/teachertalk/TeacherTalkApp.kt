package com.zl.teachertalk

import android.app.Application
import android.content.Context

/**
 * 应用入口：只创建全局依赖，不做页面跳转、登录判断等业务。
 *
 * Application 的 `onCreate()` 一定早于任何 Activity，因此登录页和首页都能直接拿到会话数据。
 */
class TeacherTalkApp : Application() {

    /**
     * 全局手动依赖容器，Application 创建后一直存在。
     */
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}

/**
 * 在 Activity / Fragment 里取全局依赖，例如 `context.appContainer.sessionManager`。
 */
val Context.appContainer: AppContainer
    get() = (applicationContext as? TeacherTalkApp)?.appContainer
        ?: error("Application 没有注册 TeacherTalkApp，请检查 AndroidManifest.xml")
