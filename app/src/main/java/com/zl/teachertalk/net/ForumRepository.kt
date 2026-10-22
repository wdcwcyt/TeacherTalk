package com.zl.teachertalk.net

import com.zl.teachertalk.bean.ForumPage

/**
 * 论坛数据仓库。
 *
 * 后端论坛接口尚未实现，当前返回空页以保持完整的加载流程。接入接口后只替换方法内部的数据来源。
 */
class ForumRepository {

    /**
     * 加载一页帖子。
     *
     * @param page 页码，从 1 开始
     * @param size 每页数量
     * @return 帖子分页结果
     */
    suspend fun loadPosts(page: Int, size: Int): ForumPage {
        return ForumPage(
            list = emptyList(),
            page = page,
            size = size,
            total = 0
        )
    }
}
