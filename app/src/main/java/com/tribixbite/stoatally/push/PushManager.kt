package com.tribixbite.stoatally.push

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Manages push notification registration with the stoatcord-bot relay server.
 * Handles FCM token and UnifiedPush endpoint registration/unregistration.
 */
object PushManager {
    private const val TAG = "PushManager"

    // Default bot URL — user can override in settings
    const val DEFAULT_BOT_URL = "http://10.0.0.131:3210"

    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
            requestTimeoutMillis = 10_000
            socketTimeoutMillis = 10_000
        }
    }

    @Serializable
    data class RegisterRequest(
        val userId: String,
        val deviceId: String,
        val mode: String,
        val fcmToken: String? = null,
        val endpoint: String? = null,
        val p256dh: String? = null,
        val auth: String? = null,
    )

    @Serializable
    data class UnregisterRequest(
        val deviceId: String,
    )

    @Serializable
    data class RegisterResponse(
        val ok: Boolean = false,
        val mode: String? = null,
        val error: String? = null,
    )

    @Serializable
    data class StatusResponse(
        val registered: Boolean = false,
        val mode: String? = null,
        val updatedAt: Long? = null,
        val error: String? = null,
    )

    /**
     * Register this device's FCM token with the bot relay server.
     * @return null on success, error message on failure
     */
    suspend fun registerFcm(
        botUrl: String,
        userId: String,
        deviceId: String,
        fcmToken: String,
    ): String? {
        return try {
            val response = httpClient.post("$botUrl/api/push/register") {
                contentType(ContentType.Application.Json)
                setBody(
                    RegisterRequest(
                        userId = userId,
                        deviceId = deviceId,
                        mode = "fcm",
                        fcmToken = fcmToken,
                    )
                )
            }
            if (response.status.isSuccess()) {
                Log.d(TAG, "FCM registration with bot succeeded")
                null
            } else {
                val body = response.bodyAsText()
                Log.w(TAG, "FCM registration with bot failed: ${response.status} $body")
                "HTTP ${response.status.value}: $body"
            }
        } catch (e: Exception) {
            Log.e(TAG, "FCM registration with bot error", e)
            e.message ?: "Unknown error"
        }
    }

    /**
     * Register a UnifiedPush endpoint with the bot relay server.
     * @return null on success, error message on failure
     */
    suspend fun registerUnifiedPush(
        botUrl: String,
        userId: String,
        deviceId: String,
        endpoint: String,
        p256dh: String,
        auth: String,
    ): String? {
        return try {
            val response = httpClient.post("$botUrl/api/push/register") {
                contentType(ContentType.Application.Json)
                setBody(
                    RegisterRequest(
                        userId = userId,
                        deviceId = deviceId,
                        mode = "webpush",
                        endpoint = endpoint,
                        p256dh = p256dh,
                        auth = auth,
                    )
                )
            }
            if (response.status.isSuccess()) {
                Log.d(TAG, "UnifiedPush registration with bot succeeded")
                null
            } else {
                val body = response.bodyAsText()
                Log.w(TAG, "UnifiedPush registration with bot failed: ${response.status} $body")
                "HTTP ${response.status.value}: $body"
            }
        } catch (e: Exception) {
            Log.e(TAG, "UnifiedPush registration with bot error", e)
            e.message ?: "Unknown error"
        }
    }

    /**
     * Unregister this device from the bot relay server.
     * @return null on success, error message on failure
     */
    suspend fun unregister(botUrl: String, deviceId: String): String? {
        return try {
            val response = httpClient.delete("$botUrl/api/push/unregister") {
                contentType(ContentType.Application.Json)
                setBody(UnregisterRequest(deviceId = deviceId))
            }
            if (response.status.isSuccess()) {
                Log.d(TAG, "Unregistered device from bot")
                null
            } else {
                val body = response.bodyAsText()
                Log.w(TAG, "Unregister from bot failed: ${response.status} $body")
                "HTTP ${response.status.value}: $body"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unregister from bot error", e)
            e.message ?: "Unknown error"
        }
    }

    /**
     * Check registration status for this device.
     */
    suspend fun checkStatus(botUrl: String, deviceId: String): StatusResponse {
        return try {
            val response = httpClient.get("$botUrl/api/push/status?deviceId=$deviceId")
            if (response.status.isSuccess()) {
                json.decodeFromString(StatusResponse.serializer(), response.bodyAsText())
            } else {
                StatusResponse(error = "HTTP ${response.status.value}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Status check error", e)
            StatusResponse(error = e.message ?: "Unknown error")
        }
    }
}
