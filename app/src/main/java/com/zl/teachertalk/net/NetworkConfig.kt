package com.zl.teachertalk.net

/**
 * 网络地址的唯一配置入口。
 *
 * Retrofit 要求基础地址以 `/` 结尾；接口路径不要再以 `/` 开头，避免覆盖基础地址中的路径前缀。
 */
object NetworkConfig {

    /**
     * Android 模拟器访问电脑本机的后端地址。
     */
    const val BASE_URL = "http://10.0.2.2:8080/"
}
