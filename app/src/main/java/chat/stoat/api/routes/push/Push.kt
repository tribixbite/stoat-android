package chat.stoat.api.routes.push

import chat.stoat.api.StoatHttp
import chat.stoat.api.routes.account.WebPushData
import chat.stoat.api.api
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import logcat.LogPriority
import logcat.logcat

/**
 * Subscribe this device's FCM token to push notifications.
 * Returns null on success, or an error message on failure.
 */
suspend fun subscribePush(
    endpoint: String = "fcm",
    auth: String,
    p256diffieHellman: String? = null,
): String? {
    return try {
        val data = WebPushData(
            endpoint = endpoint,
            p256diffieHellman = p256diffieHellman ?: "",
            auth = auth
        )

        val response = StoatHttp.post("/push/subscribe".api()) {
            setBody(data)
            contentType(ContentType.Application.Json)
        }

        if (response.status.isSuccess()) {
            logcat("Push", LogPriority.DEBUG) { "Push subscription registered successfully" }
            null
        } else {
            val body = response.bodyAsText()
            logcat("Push", LogPriority.ERROR) { "Push subscribe failed: ${response.status} $body" }
            "HTTP ${response.status.value}: $body"
        }
    } catch (e: Exception) {
        logcat("Push", LogPriority.ERROR) { "Failed to subscribe push: ${e.message}" }
        e.message ?: "Unknown error"
    }
}