package com.zl.teachertalk.net

import android.content.SharedPreferences
import android.util.Log
import com.zl.teachertalk.bean.UserInfoBean
import com.zl.teachertalk.utils.SPUtils

/**
 * 登录会话的本地读写：token 和当前用户信息都放在这里。
 *
 * 整个应用只由 [com.zl.teachertalk.AppContainer] 创建一个实例，页面不要自己 new。
 * 实际存储交给 [SPUtils]，并且使用独立的会话文件，避免退出登录时清掉普通配置。
 *
 * @param spUtils 会话专用存储
 * @param isDebug 是否输出调试日志；Release 构建不会输出 token
 */
class SessionManager(
    private val spUtils: SPUtils,
    private val isDebug: Boolean = false
) {

    /**
     * 当前 token；未登录时为 null。
     */
    val token: String?
        get() = spUtils.getString(KEY_TOKEN)?.takeIf { it.isNotBlank() }

    /**
     * 当前登录用户；没有 token 或本地数据不完整时为 null。
     */
    val userInfo: UserInfoBean?
        get() {
            if (!isLoggedIn) return null

            val id = spUtils.getLong(KEY_USER_ID, INVALID_USER_ID)
            val phone = spUtils.getString(KEY_USER_PHONE)
            val nickname = spUtils.getString(KEY_USER_NICKNAME)
            val role = spUtils.getString(KEY_USER_ROLE)
            if (id <= INVALID_USER_ID || phone.isNullOrBlank() || nickname.isNullOrBlank() || role.isNullOrBlank()) {
                return null
            }
            return UserInfoBean(
                id = id,
                phone = phone,
                nickname = nickname,
                avatarUrl = spUtils.getString(KEY_USER_AVATAR_URL),
                role = role
            )
        }

    /**
     * 是否已登录。
     *
     * MVP 只判断本地有没有非空 token；token 是否过期由服务端返回 401 决定。
     */
    val isLoggedIn: Boolean
        get() = token != null

    /**
     * 登录或注册成功后保存会话。
     *
     * @param token 服务端返回的 JWT
     * @param userInfo 服务端返回的当前用户信息
     */
    fun saveSession(token: String, userInfo: UserInfoBean) {
        require(token.isNotBlank()) { "token 不能为空" }
        spUtils.edit {
            putString(KEY_TOKEN, token)
            putUserInfo(userInfo)
        }
        if (isDebug) {
            Log.e(LOG_TAG, "saveSession token=$token")
        }
    }

    /**
     * 修改资料成功后刷新用户信息，token 保持不变。
     *
     * @param userInfo 服务端返回的最新用户信息
     */
    fun updateUserInfo(userInfo: UserInfoBean) {
        if (!isLoggedIn) return
        spUtils.edit {
            putUserInfo(userInfo)
        }
    }

    /**
     * 退出登录或 token 失效时清空全部会话数据。
     */
    fun clearSession() {
        spUtils.clear()
    }

    private fun SharedPreferences.Editor.putUserInfo(userInfo: UserInfoBean): SharedPreferences.Editor =
        putLong(KEY_USER_ID, userInfo.id)
            .putString(KEY_USER_PHONE, userInfo.phone)
            .putString(KEY_USER_NICKNAME, userInfo.nickname)
            .putString(KEY_USER_AVATAR_URL, userInfo.avatarUrl)
            .putString(KEY_USER_ROLE, userInfo.role)

    companion object {
        /**
         * 登录会话专用的 SharedPreferences 文件名。
         */
        const val FILE_NAME = "teacher_talk_session"

        private const val KEY_TOKEN = "token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_PHONE = "user_phone"
        private const val KEY_USER_NICKNAME = "user_nickname"
        private const val KEY_USER_AVATAR_URL = "user_avatar_url"
        private const val KEY_USER_ROLE = "user_role"
        private const val INVALID_USER_ID = 0L
        private const val LOG_TAG = "TeacherTalkSession"
    }
}
