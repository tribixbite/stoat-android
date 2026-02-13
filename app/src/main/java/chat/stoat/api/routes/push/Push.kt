package chat.stoat.api.routes.push

import chat.stoat.api.StoatHttp
import chat.stoat.api.routes.account.WebPushData
import chat.stoat.api.api
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import logcat.LogPriority
import logcat.logcat

/**
 * Subscribe this device's FCM token to push notifications.
 * Returns true on success, false on failure.
 */
suspend fun subscribePush(
    endpoint: String = "fcm",
    auth: String,
    p256diffieHellman: String? = null,
): Boolean {
    return try {
        val data = WebPushData(
            endpoint = endpoint,
            p256diffieHellman = p256diffieHellman ?: "",
            auth = auth
        )

        StoatHttp.post("/push/subscribe".api()) {
            setBody(data)
            contentType(ContentType.Application.Json)
        }
        logcat("Push", LogPriority.DEBUG) { "Push subscription registered successfully" }
        true
    } catch (e: Exception) {
        logcat("Push", LogPriority.ERROR) { "Failed to subscribe push: ${e.message}" }
        false
    }
}