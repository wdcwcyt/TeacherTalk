package com.zl.teachertalk.net

/**
 * 网络地址的唯一配置入口。
 *
 * Retrofit 要求基础地址以 `/` 结尾；接口路径不要再以 `/` 开头，避免覆盖基础地址中的路径前缀。
 */
object NetworkConfig {

    /**
     * 当前开发电脑在局域网中的后端地址，真机调试时使用。
     */
    const val BASE_URL = "http://10.191.117.240:8080/"

    /**
     * 把服务端返回的相对媒体地址转换成 Glide 可加载的完整地址。
     *
     * @param path 服务端返回的图片相对地址；也兼容完整 HTTP/HTTPS 地址
     * @return 可直接加载的地址
     */
    fun resolveMediaUrl(path: String): String {
        val normalized = path.trim()
        if (normalized.isEmpty()) return ""
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return normalized
        }
        return BASE_URL.trimEnd('/') + "/" + normalized.trimStart('/')
    }
}
