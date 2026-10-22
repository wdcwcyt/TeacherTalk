package com.zl.teachertalk.bean

/**
 * 当前登录用户的公开信息。
 *
 * 只放界面真正要用的字段；密码、token、token 过期时间都不放这里。
 *
 * @property id 用户 id，用于发消息、判断内容归属
 * @property phone 手机号，账号标识和「我的」页面展示
 * @property nickname 昵称，帖子、评论、聊天里显示
 * @property avatarUrl 头像地址，没有上传头像时为 null
 * @property role 角色，当前约定为 `STUDENT` 或 `PARENT`
 */
data class UserInfoBean(
    val id: Long,
    val phone: String,
    val nickname: String,
    val avatarUrl: String? = null,
    val role: String
)
