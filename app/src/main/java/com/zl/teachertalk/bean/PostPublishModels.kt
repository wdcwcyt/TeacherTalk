package com.zl.teachertalk.bean

/**
 * 图片上传返回结果。
 *
 * @param url 原图相对地址，发帖时提交该地址
 * @param thumbnailUrl 列表缩略图相对地址
 */
data class UploadedImage(
    val url: String,
    val thumbnailUrl: String
)

/**
 * 发布帖子请求。
 *
 * @param title 帖子标题；没有标题时为 null
 * @param content 帖子正文
 * @param imageUrls 已上传原图地址，按选择顺序排列
 */
data class PostCreateRequest(
    val title: String?,
    val content: String,
    val imageUrls: List<String>
)

/**
 * 发布帖子成功后的最小返回数据。
 *
 * @param id 新帖子编号
 */
data class CreatedPost(
    val id: Long
)
