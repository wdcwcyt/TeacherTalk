package com.zl.teachertalk.net

import android.util.Base64
import com.zl.teachertalk.bean.AuthRequest
import com.zl.teachertalk.bean.AuthResult
import com.zl.teachertalk.utils.SPUtils
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import org.json.JSONArray
import org.json.JSONObject

/**
 * 后端完成前的本地账号库。
 *
 * 账号以 JSON 存在独立 SharedPreferences 文件中；密码只保存随机盐和 PBKDF2 派生值，不保存明文。
 * 该类不是长期安全方案，接入后端后应由服务端认证替代。
 *
 * @param spUtils 本地账号专用存储
 */
class LocalAuthStore(
    private val spUtils: SPUtils
) {

    /**
     * 注册本地账号，并返回临时 token。
     *
     * @param request 账号密码请求体
     * @return 本地认证结果
     * @throws ApiException 参数为空、账号重复或本地数据损坏时抛出
     */
    @Synchronized
    fun register(request: AuthRequest): AuthResult {
        val account = normalizeAndValidate(request)
        val accounts = readAccounts()
        if (accounts.any { it.account == account }) {
            throw ApiException(code = CODE_CONFLICT, message = "Account already exists")
        }

        val salt = createSalt()
        accounts += LocalAccountRecord(
            account = account,
            salt = salt,
            passwordHash = derivePassword(request.password, salt)
        )
        writeAccounts(accounts)
        return AuthResult(token = createLocalToken(), account = account)
    }

    /**
     * 校验本地账号和密码，并返回临时 token。
     *
     * @param request 账号密码请求体
     * @return 本地认证结果
     * @throws ApiException 参数为空、账号不存在、密码错误或本地数据损坏时抛出
     */
    @Synchronized
    fun login(request: AuthRequest): AuthResult {
        val account = normalizeAndValidate(request)
        val record = readAccounts().firstOrNull { it.account == account }
            ?: throw ApiException(code = CODE_UNAUTHORIZED, message = "Account or password is invalid")
        val candidateHash = derivePassword(request.password, record.salt)

        if (!MessageDigest.isEqual(
                record.passwordHash.toByteArray(Charsets.UTF_8),
                candidateHash.toByteArray(Charsets.UTF_8)
            )
        ) {
            throw ApiException(code = CODE_UNAUTHORIZED, message = "Account or password is invalid")
        }
        return AuthResult(token = createLocalToken(), account = account)
    }

    private fun normalizeAndValidate(request: AuthRequest): String {
        val account = request.account.trim()
        if (account.isBlank() || request.password.isBlank()) {
            throw ApiException(code = CODE_BAD_REQUEST, message = "Account and password are required")
        }
        return account
    }

    private fun readAccounts(): MutableList<LocalAccountRecord> {
        val rawJson = spUtils.getString(KEY_ACCOUNTS) ?: return mutableListOf()
        return try {
            val jsonArray = JSONArray(rawJson)
            buildList(jsonArray.length()) {
                for (index in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(index)
                    add(
                        LocalAccountRecord(
                            account = item.getString(JSON_ACCOUNT),
                            salt = item.getString(JSON_SALT),
                            passwordHash = item.getString(JSON_PASSWORD_HASH)
                        )
                    )
                }
            }.toMutableList()
        } catch (exception: Exception) {
            throw ApiException(
                code = CODE_LOCAL_DATA_ERROR,
                message = "Local account data is corrupted",
                cause = exception
            )
        }
    }

    private fun writeAccounts(accounts: List<LocalAccountRecord>) {
        val jsonArray = JSONArray()
        accounts.forEach { record ->
            jsonArray.put(
                JSONObject()
                    .put(JSON_ACCOUNT, record.account)
                    .put(JSON_SALT, record.salt)
                    .put(JSON_PASSWORD_HASH, record.passwordHash)
            )
        }
        spUtils.putString(KEY_ACCOUNTS, jsonArray.toString())
    }

    private fun createSalt(): String {
        val salt = ByteArray(SALT_BYTE_COUNT)
        secureRandom.nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    private fun derivePassword(password: String, encodedSalt: String): String {
        val salt = Base64.decode(encodedSalt, Base64.NO_WRAP)
        val spec = PBEKeySpec(
            password.toCharArray(),
            salt,
            PBKDF2_ITERATIONS,
            PBKDF2_KEY_LENGTH_BITS
        )
        return try {
            val hash = SecretKeyFactory
                .getInstance(PBKDF2_ALGORITHM)
                .generateSecret(spec)
                .encoded
            Base64.encodeToString(hash, Base64.NO_WRAP)
        } finally {
            spec.clearPassword()
        }
    }

    private fun createLocalToken(): String = "local-${UUID.randomUUID()}"

    private data class LocalAccountRecord(
        val account: String,
        val salt: String,
        val passwordHash: String
    )

    companion object {
        /**
         * 本地账号专用 SharedPreferences 文件名，与普通配置和登录会话分开。
         */
        const val FILE_NAME = "teacher_talk_local_auth"

        private const val KEY_ACCOUNTS = "accounts"
        private const val JSON_ACCOUNT = "account"
        private const val JSON_SALT = "salt"
        private const val JSON_PASSWORD_HASH = "password_hash"
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA1"
        private const val PBKDF2_ITERATIONS = 100_000
        private const val PBKDF2_KEY_LENGTH_BITS = 256
        private const val SALT_BYTE_COUNT = 16
        private const val CODE_BAD_REQUEST = 400
        private const val CODE_UNAUTHORIZED = 401
        private const val CODE_CONFLICT = 409
        private const val CODE_LOCAL_DATA_ERROR = 500

        private val secureRandom = SecureRandom()
    }
}
