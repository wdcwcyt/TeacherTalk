package com.zl.teachertalk.bean

/**
 * 登录和注册接口共用的请求体。
 *
 * 当前只包含账号和密码，后续新增手机号登录时应另建专用请求类，避免影响已有接口。
 *
 * @param account 账号，当前不做大小写转换
 * @param password 原始密码，只在内存和请求体中使用
 */
data class AuthRequest(
    val account: String,
    val password: String
) {
    /**
     * 隐藏密码，防止调试日志直接打印请求对象时泄露凭据。
     */
    override fun toString(): String = "AuthRequest(account=$account, password=***)"
}

/**
 * 认证成功后返回的临时结果。
 *
 * 后端未完成时由本地仓库生成临时 token；接入后端后将换成服务端 JWT，并按接口契约补充用户信息。
 *
 * @param token 认证 token，本地开发期为临时字符串
 * @param account 当前登录账号
 */
data class AuthResult(
    val token: String,
    val account: String
) {
    /**
     * 隐藏 token，避免日志或异常信息泄露登录凭据。
     */
    override fun toString(): String = "AuthResult(account=$account, token=***)"
}
