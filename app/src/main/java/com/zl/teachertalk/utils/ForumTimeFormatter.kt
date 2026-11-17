package com.zl.teachertalk.utils

import android.text.format.DateUtils
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * 把论坛接口返回的本地时间转换为界面使用的相对时间。
 */
object ForumTimeFormatter {

    private val formatter = SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss",
        Locale.ROOT
    ).apply {
        timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        isLenient = false
    }

    /**
     * 格式化论坛时间；解析失败时原样返回服务端字符串。
     *
     * @param value 服务端返回的 `yyyy-MM-dd HH:mm:ss` 时间
     * @return 本地化的相对时间
     */
    @Synchronized
    fun format(value: String): String {
        val timestamp = runCatching {
            formatter.parse(value)?.time
        }.getOrNull() ?: return value
        return DateUtils.getRelativeTimeSpanString(
            timestamp,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString()
    }
}
