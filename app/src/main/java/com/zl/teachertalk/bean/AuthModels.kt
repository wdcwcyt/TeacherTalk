package com.zl.teachertalk.bean

/**
 * 请求短信验证码。
 *
 * @param phone 接收验证码的手机号
 */
data class SendSmsCodeRequest(
    val phone: String
)

/**
 * 短信验证码发送结果。
 *
 * @param expiresIn 验证码有效时长，单位由后端契约约定
 * @param resendAfter 距离下次允许重发的时长，单位由后端契约约定
 */
data class SendSmsCodeResponse(
    val expiresIn: Long,
    val resendAfter: Long
)

/**
 * 使用手机号和短信验证码登录。
 *
 * @param phone 登录手机号
 * @param code 短信验证码
 */
data class LoginRequest(
    val phone: String,
    val code: String
)

/**
 * 使用手机号和短信验证码注册。
 *
 * @param phone 注册手机号
 * @param code 短信验证码
 * @param nickname 可选昵称；为空时由后端决定默认值
 * @param role 注册角色
 */
data class RegisterRequest(
    val phone: String,
    val code: String,
    val nickname: String? = null,
    val role: String
)

/**
 * 登录或注册成功后的认证结果。
 *
 * @param token 服务端颁发的认证 token
 * @param user 当前登录用户的公开信息
 */
data class AuthResult(
    val token: String,
    val user: UserInfoBean
) {
    override fun toString(): String =
        "AuthResult(user=${user.id}, token=***)"
}
