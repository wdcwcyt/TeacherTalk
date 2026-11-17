package com.zl.teachertalk.bean

/**
 * 帖子评论。
 *
 * @param id 评论编号
 * @param postId 所属帖子编号
 * @param authorId 评论作者用户 id
 * @param authorNickname 评论作者昵称
 * @param authorAvatarUrl 评论作者头像地址，没有头像时为 null
 * @param content 评论正文
 * @param createdAt 评论时间，格式为 `yyyy-MM-dd HH:mm:ss`
 */
data class ForumComment(
    val id: Long,
    val postId: Long,
    val authorId: Long,
    val authorNickname: String,
    val authorAvatarUrl: String?,
    val content: String,
    val createdAt: String
)

/**
 * 一页帖子评论。
 *
 * @param list 当前页评论
 * @param page 当前页码，从 1 开始
 * @param size 每页数量
 * @param total 评论总数
 */
data class ForumCommentPage(
    val list: List<ForumComment>,
    val page: Int,
    val size: Int,
    val total: Long
)

/**
 * 发布评论请求。
 *
 * @param content 去除首尾空白后 1 到 500 个字符的评论正文
 */
data class CommentCreateRequest(
    val content: String
)
