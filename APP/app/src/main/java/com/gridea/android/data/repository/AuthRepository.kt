package com.gridea.android.data.repository

import com.gridea.android.data.model.Account
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GitHub OAuth 认证仓库（Device Flow）
 *
 * 移动端 OAuth 推荐方案：无需 Client Secret，安全性高
 *
 * 流程：
 * 1. APP 请求 device code（POST /login/device/code）
 * 2. 用户在任意设备访问 github.com/login/device 输入用户码
 * 3. APP 轮询 token 接口（POST /login/oauth/access_token）直到用户完成授权
 * 4. 用 access_token 调用 /user 获取账户信息
 *
 * 用户需先在 https://github.com/settings/applications/new 创建 OAuth App：
 * - App name: 任意
 * - Homepage URL: 任意
 * - Authorization callback URL: http://localhost:8080（Device Flow 不实际使用回调）
 * - 启用 "Enable Device Flow"
 */
@Singleton
class AuthRepository @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Device Flow 第一步：请求设备码
     * @param clientId OAuth App 的 Client ID
     * @return DeviceCodeResponse 包含 user_code、verification_uri 等
     */
    suspend fun requestDeviceCode(clientId: String): DeviceCodeResponse {
        val body = JSONObject()
            .put("client_id", clientId)
            .put("scope", "repo user")
            .toString()

        val request = Request.Builder()
            .url("https://github.com/login/device/code")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .addHeader("Accept", "application/json")
            .build()

        return executeRequest(request) { json ->
            DeviceCodeResponse(
                deviceCode = json.getString("device_code"),
                userCode = json.getString("user_code"),
                verificationUri = json.optString("verification_uri", "https://github.com/login/device"),
                expiresIn = json.getInt("expires_in"),
                interval = json.getInt("interval")
            )
        }
    }

    /**
     * Device Flow 轮询：用 device_code 换取 access_token
     * @return AccessTokenResult 成功返回 token，授权中返回 pending，超时返回 expired
     */
    suspend fun pollForAccessToken(clientId: String, deviceCode: String): AccessTokenResult {
        val body = JSONObject()
            .put("client_id", clientId)
            .put("device_code", deviceCode)
            .put("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
            .toString()

        val request = Request.Builder()
            .url("https://github.com/login/oauth/access_token")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .addHeader("Accept", "application/json")
            .build()

        return executeRequest(request) { json ->
            val error = json.optString("error", "")
            when {
                error.isEmpty() -> {
                    val token = json.getString("access_token")
                    AccessTokenResult.Success(token)
                }
                error == "authorization_pending" -> AccessTokenResult.Pending
                error == "slow_down" -> AccessTokenResult.SlowDown
                error == "expired_token" -> AccessTokenResult.Expired
                error == "access_denied" -> AccessTokenResult.Denied
                else -> AccessTokenResult.Error(json.optString("error_description", error))
            }
        }
    }

    /**
     * 用 access_token 获取 GitHub 用户信息
     *
     * scope = "repo user" 已包含 user:email 权限，可获取邮箱字段
     * （但用户在 GitHub 设置中未公开邮箱时，email 字段为空字符串）
     */
    suspend fun fetchUserInfo(accessToken: String): Account {
        val request = Request.Builder()
            .url("https://api.github.com/user")
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Accept", "application/vnd.github+json")
            .build()

        return executeRequest(request) { json ->
            // JSONObject.optString("xxx", "") 在 JSON 值为 null 时返回字符串 "null" 而非默认值
            // 需先 isNull 判断；GitHub 对未设置的字段返回 null
            fun optStringOrNull(key: String): String =
                if (json.isNull(key)) "" else json.optString(key, "")

            val rawName = optStringOrNull("name")
            Account(
                accessToken = accessToken,
                login = json.getString("login"),
                name = rawName.ifBlank { json.getString("login") },
                avatarUrl = optStringOrNull("avatar_url"),
                htmlUrl = optStringOrNull("html_url"),
                bio = optStringOrNull("bio"),
                company = optStringOrNull("company"),
                blog = optStringOrNull("blog"),
                location = optStringOrNull("location"),
                email = optStringOrNull("email"),
                publicRepos = json.optInt("public_repos", 0),
                totalPrivateRepos = json.optInt("total_private_repos", 0),
                followers = json.optInt("followers", 0),
                following = json.optInt("following", 0),
                createdAt = optStringOrNull("created_at")
            )
        }
    }

    /**
     * 完整 Device Flow 登录流程（带自动轮询）
     * @param clientId OAuth App Client ID
     * @param onUserCode 回调：显示用户码和验证 URL 给用户
     * @return 登录成功返回 Account，失败抛出异常
     */
    suspend fun loginWithDeviceFlow(
        clientId: String,
        onUserCode: (DeviceCodeResponse) -> Unit
    ): Account {
        // 1. 请求设备码
        val deviceResp = requestDeviceCode(clientId)
        onUserCode(deviceResp)

        // 2. 轮询 token（按 interval 间隔）
        val deadline = System.currentTimeMillis() + deviceResp.expiresIn * 1000L
        var interval = deviceResp.interval.toLong()

        while (System.currentTimeMillis() < deadline) {
            delay(interval * 1000L)
            when (val result = pollForAccessToken(clientId, deviceResp.deviceCode)) {
                is AccessTokenResult.Success -> {
                    // 3. 获取用户信息
                    return fetchUserInfo(result.accessToken)
                }
                AccessTokenResult.Pending -> { /* 继续轮询 */ }
                AccessTokenResult.SlowDown -> {
                    interval += 5L // GitHub 要求 slow_down 时增加 5 秒间隔
                }
                AccessTokenResult.Expired -> throw AuthException("授权超时，请重新尝试")
                AccessTokenResult.Denied -> throw AuthException("已取消授权")
                is AccessTokenResult.Error -> throw AuthException(result.message)
            }
        }
        throw AuthException("授权超时，请重新尝试")
    }

    private suspend inline fun <T> executeRequest(
        request: Request,
        crossinline parser: (JSONObject) -> T
    ): T = withContext(kotlinx.coroutines.Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw AuthException("请求失败：HTTP ${response.code}${if (body.isNotEmpty()) "\n$body" else ""}")
            }
            val json = JSONObject(body)
            parser(json)
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

/** Device Flow 第一步返回的数据 */
data class DeviceCodeResponse(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresIn: Int,
    val interval: Int
)

/** Token 轮询结果 */
sealed class AccessTokenResult {
    data class Success(val accessToken: String) : AccessTokenResult()
    object Pending : AccessTokenResult()
    object SlowDown : AccessTokenResult()
    object Expired : AccessTokenResult()
    object Denied : AccessTokenResult()
    data class Error(val message: String) : AccessTokenResult()
}

/** 认证异常 */
class AuthException(message: String) : Exception(message)
