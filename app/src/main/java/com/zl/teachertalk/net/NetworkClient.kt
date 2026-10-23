package com.zl.teachertalk.net

import android.util.Log
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * 创建网络客户端和 Retrofit 接口，不承载具体业务请求。
 */
object NetworkClient {

    /**
     * 创建全局共享的 [ApiService]。
     *
     * @param sessionManager 为请求提供当前 JWT
     * @param isDebug 是否启用基础调试日志；只记录请求行和状态，不打印请求体
     * @return 通过 Retrofit 生成的接口实例
     */
    fun createApiService(
        sessionManager: SessionManager,
        isDebug: Boolean
    ): ApiService {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(sessionManager))
            .apply {
                if (isDebug) {
                    addInterceptor(
                        HttpLoggingInterceptor { message ->
                            Log.e(HTTP_LOG_TAG, message)
                        }.apply {
                            level = HttpLoggingInterceptor.Level.BASIC
                            redactHeader("Authorization")
                        }
                    )
                }
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(NetworkConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    private const val HTTP_LOG_TAG = "TeacherTalkHttp"
}
