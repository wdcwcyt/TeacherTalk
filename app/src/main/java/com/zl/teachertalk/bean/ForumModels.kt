package com.zl.teachertalk.bean

/**
 * 论坛列表中的一条帖子。
 *
 * 列表只携带正文预览，完整正文由帖子详情接口返回。
 *
 * @param id 帖子编号
 * @param title 帖子标题；没有标题时为空字符串
 * @param contentPreview 服务端截取的正文预览
 * @param authorId 发布者用户 id
 * @param authorNickname 发布者昵称
 * @param authorAvatarUrl 发布者头像地址，没有头像时为 null
 * @param thumbnailUrls 按顺序排列的前三张缩略图地址；兼容服务端异常返回 null 时按空数组处理
 * @param likeCount 点赞数
 * @param favoriteCount 收藏数
 * @param commentCount 评论数
 * @param liked 当前登录用户是否已点赞
 * @param favorited 当前登录用户是否已收藏
 * @param createdAt 发布时间，格式为 `yyyy-MM-dd HH:mm:ss`
 */
data class ForumPost(
    val id: Long,
    val title: String,
    val contentPreview: String,
    val authorId: Long,
    val authorNickname: String,
    val authorAvatarUrl: String? = null,
    val thumbnailUrls: List<String>? = null,
    val likeCount: Int,
    val favoriteCount: Int,
    val commentCount: Int,
    val liked: Boolean,
    val favorited: Boolean,
    val createdAt: String
)

/**
 * 一页论坛帖子数据。
 *
 * @param list 当前页帖子
 * @param page 当前页码，从 1 开始
 * @param size 每页数量
 * @param total 帖子总数
 */
data class ForumPage(
    val list: List<ForumPost>,
    val page: Int,
    val size: Int,
    val total: Long
)
