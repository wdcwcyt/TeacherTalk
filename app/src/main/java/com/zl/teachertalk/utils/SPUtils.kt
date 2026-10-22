package com.zl.teachertalk.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * SharedPreferences 的统一入口。
 *
 * 业务代码通过 [com.zl.teachertalk.AppContainer] 拿到实例，不要自己调用
 * `getSharedPreferences()`。内部只保留 applicationContext，不会泄漏 Activity。
 *
 * @param context 任意 Context
 * @param fileName 文件名；普通配置用默认值，登录会话用独立文件
 */
class SPUtils(
    context: Context,
    fileName: String = DEFAULT_FILE_NAME
) {

    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(fileName, Context.MODE_PRIVATE)

    /**
     * 保存字符串；value 为 null 时删除这个 key。
     */
    fun putString(key: String, value: String?) {
        preferences.edit().putString(key, value).apply()
    }

    /**
     * 保存整数。
     */
    fun putInt(key: String, value: Int) {
        preferences.edit().putInt(key, value).apply()
    }

    /**
     * 保存长整数。
     */
    fun putLong(key: String, value: Long) {
        preferences.edit().putLong(key, value).apply()
    }

    /**
     * 保存浮点数。
     */
    fun putFloat(key: String, value: Float) {
        preferences.edit().putFloat(key, value).apply()
    }

    /**
     * 保存布尔值。
     */
    fun putBoolean(key: String, value: Boolean) {
        preferences.edit().putBoolean(key, value).apply()
    }

    /**
     * 保存字符串集合；value 为 null 时删除这个 key。
     */
    fun putStringSet(key: String, value: Set<String>?) {
        preferences.edit().putStringSet(key, value).apply()
    }

    /**
     * 读取字符串，默认返回 null，便于区分“没存过”和“存了空字符串”。
     */
    fun getString(key: String, defaultValue: String? = null): String? =
        preferences.getString(key, defaultValue)

    /**
     * 读取整数，默认返回 0。
     */
    fun getInt(key: String, defaultValue: Int = 0): Int =
        preferences.getInt(key, defaultValue)

    /**
     * 读取长整数，默认返回 0。
     */
    fun getLong(key: String, defaultValue: Long = 0L): Long =
        preferences.getLong(key, defaultValue)

    /**
     * 读取浮点数，默认返回 0。
     */
    fun getFloat(key: String, defaultValue: Float = 0f): Float =
        preferences.getFloat(key, defaultValue)

    /**
     * 读取布尔值，默认返回 false。
     */
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean =
        preferences.getBoolean(key, defaultValue)

    /**
     * 读取字符串集合，默认返回空集合。
     *
     * 返回值做了一次拷贝，调用方修改集合不会污染 SharedPreferences 里的缓存。
     */
    fun getStringSet(key: String, defaultValue: Set<String> = emptySet()): Set<String> =
        preferences.getStringSet(key, defaultValue)?.toSet() ?: defaultValue

    /**
     * 判断 key 是否存在。
     */
    fun contains(key: String): Boolean = preferences.contains(key)

    /**
     * 删除一个或多个 key。
     */
    fun remove(vararg keys: String) {
        val editor = preferences.edit()
        keys.forEach { editor.remove(it) }
        editor.apply()
    }

    /**
     * 清空当前 SPUtils 对应的整个 SharedPreferences 文件。
     */
    fun clear() {
        preferences.edit().clear().apply()
    }

    /**
     * 一次写入多个 key，所有修改在同一个 Editor 里提交。
     *
     * 适合会话数据这种“token 和用户信息要一起写”的场景。
     */
    fun edit(block: SharedPreferences.Editor.() -> Unit) {
        val editor = preferences.edit()
        block(editor)
        editor.apply()
    }

    companion object {
        /**
         * 普通配置的默认文件名。
         */
        const val DEFAULT_FILE_NAME = "app_sp_config"
    }
}
