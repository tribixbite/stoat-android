package chat.stoat.api.routes.webhooks

import chat.stoat.api.StoatAPIError
import chat.stoat.api.StoatHttp
import chat.stoat.api.StoatJson
import chat.stoat.api.api
import chat.stoat.core.model.schemas.Webhook
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer

// --- Channel-scoped webhook endpoints ---

/** Create a webhook in a channel. Requires ManageWebhooks permission. */
suspend fun createWebhook(channelId: String, name: String, avatar: String? = null): Webhook {
    @Serializable
    data class Body(val name: String, val avatar: String? = null)

    val response = StoatHttp.post("/channels/$channelId/webhooks".api()) {
        contentType(ContentType.Application.Json)
        setBody(StoatJson.encodeToString(Body.serializer(), Body(name, avatar)))
    }.bodyAsText()

    try {
        val error = StoatJson.decodeFromString(StoatAPIError.serializer(), response)
        throw Exception(error.type)
    } catch (_: SerializationException) {}

    return StoatJson.decodeFromString(Webhook.serializer(), response)
}

/** List all webhooks in a channel. Requires ManageWebhooks permission. */
suspend fun fetchChannelWebhooks(channelId: String): List<Webhook> {
    val response = StoatHttp.get("/channels/$channelId/webhooks".api()).bodyAsText()

    try {
        val error = StoatJson.decodeFromString(StoatAPIError.serializer(), response)
        throw Exception(error.type)
    } catch (_: SerializationException) {}

    return StoatJson.decodeFromString(ListSerializer(Webhook.serializer()), response)
}

// --- Webhook-scoped endpoints (authenticated) ---

/** Fetch a webhook by ID (authenticated). Does NOT include token. */
suspend fun fetchWebhook(webhookId: String): Webhook {
    val response = StoatHttp.get("/webhooks/$webhookId".api()).bodyAsText()

    try {
        val error = StoatJson.decodeFromString(StoatAPIError.serializer(), response)
        throw Exception(error.type)
    } catch (_: SerializationException) {}

    return StoatJson.decodeFromString(Webhook.serializer(), response)
}

/** Edit a webhook (authenticated). Requires ManageWebhooks permission. */
suspend fun editWebhook(
    webhookId: String,
    name: String? = null,
    avatar: String? = null,
    permissions: Long? = null,
    remove: List<String>? = null
): Webhook {
    @Serializable
    data class Body(
        val name: String? = null,
        val avatar: String? = null,
        val permissions: Long? = null,
        val remove: List<String>? = null
    )

    val body = Body(name, avatar, permissions, remove)
    val response = StoatHttp.patch("/webhooks/$webhookId".api()) {
        contentType(ContentType.Application.Json)
        setBody(StoatJson.encodeToString(Body.serializer(), body))
    }.bodyAsText()

    try {
        val error = StoatJson.decodeFromString(StoatAPIError.serializer(), response)
        throw Exception(error.type)
    } catch (_: SerializationException) {}

    return StoatJson.decodeFromString(Webhook.serializer(), response)
}

/** Delete a webhook (authenticated). Requires ManageWebhooks permission. */
suspend fun deleteWebhook(webhookId: String) {
    val response = StoatHttp.delete("/webhooks/$webhookId".api())
    if (response.status.value !in 200..299) {
        val body = response.bodyAsText()
        try {
            val error = StoatJson.decodeFromString(StoatAPIError.serializer(), body)
            throw Exception(error.type)
        } catch (_: SerializationException) {
            throw Exception("HTTP ${response.status.value}: $body")
        }
    }
}

// --- Token-authenticated webhook endpoints ---

/** Fetch a webhook by ID and token. Includes the token field. */
suspend fun fetchWebhookWithToken(webhookId: String, token: String): Webhook {
    val response = StoatHttp.get("/webhooks/$webhookId/$token".api()).bodyAsText()

    try {
        val error = StoatJson.decodeFromString(StoatAPIError.serializer(), response)
        throw Exception(error.type)
    } catch (_: SerializationException) {}

    return StoatJson.decodeFromString(Webhook.serializer(), response)
}

/** Edit a webhook using token auth. */
suspend fun editWebhookWithToken(
    webhookId: String,
    token: String,
    name: String? = null,
    avatar: String? = null,
    permissions: Long? = null,
    remove: List<String>? = null
): Webhook {
    @Serializable
    data class Body(
        val name: String? = null,
        val avatar: String? = null,
        val permissions: Long? = null,
        val remove: List<String>? = null
    )

    val body = Body(name, avatar, permissions, remove)
    val response = StoatHttp.patch("/webhooks/$webhookId/$token".api()) {
        contentType(ContentType.Application.Json)
        setBody(StoatJson.encodeToString(Body.serializer(), body))
    }.bodyAsText()

    try {
        val error = StoatJson.decodeFromString(StoatAPIError.serializer(), response)
        throw Exception(error.type)
    } catch (_: SerializationException) {}

    return StoatJson.decodeFromString(Webhook.serializer(), response)
}

/** Delete a webhook using token auth. */
suspend fun deleteWebhookWithToken(webhookId: String, token: String) {
    val response = StoatHttp.delete("/webhooks/$webhookId/$token".api())
    if (response.status.value !in 200..299) {
        val body = response.bodyAsText()
        try {
            val error = StoatJson.decodeFromString(StoatAPIError.serializer(), body)
            throw Exception(error.type)
        } catch (_: SerializationException) {
            throw Exception("HTTP ${response.status.value}: $body")
        }
    }
}

/** Execute a webhook (send a message). Token-authenticated. */
suspend fun executeWebhook(
    webhookId: String,
    token: String,
    content: String? = null,
    attachments: List<String>? = null
): String {
    @Serializable
    data class Body(
        val content: String? = null,
        val attachments: List<String>? = null
    )

    val body = Body(content, attachments)
    val response = StoatHttp.post("/webhooks/$webhookId/$token".api()) {
        contentType(ContentType.Application.Json)
        setBody(StoatJson.encodeToString(Body.serializer(), body))
    }.bodyAsText()

    return response
}
