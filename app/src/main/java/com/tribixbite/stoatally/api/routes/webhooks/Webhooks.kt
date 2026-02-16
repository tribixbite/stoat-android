package com.tribixbite.stoatally.api.routes.webhooks

import com.tribixbite.stoatally.api.StoatAPIError
import com.tribixbite.stoatally.api.StoatHttp
import com.tribixbite.stoatally.api.StoatJson
import com.tribixbite.stoatally.api.api
import com.tribixbite.stoatally.core.model.schemas.Webhook
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

// --- Channel-scoped webhook endpoints ---

/** Create a webhook in a channel. Requires ManageWebhooks permission. */
suspend fun createWebhook(channelId: String, name: String, avatar: String? = null): Webhook {
    @Serializable
    data class Body(val name: String, val avatar: String? = null)

    val res = StoatHttp.post("/channels/$channelId/webhooks".api()) {
        contentType(ContentType.Application.Json)
        setBody(StoatJson.encodeToString(Body.serializer(), Body(name, avatar)))
    }
    val body = res.bodyAsText()

    if (res.status.value !in 200..299) {
        val error = try { StoatJson.decodeFromString(StoatAPIError.serializer(), body) } catch (_: Exception) { null }
        throw Exception(error?.type ?: "HTTP ${res.status.value}")
    }

    return StoatJson.decodeFromString(Webhook.serializer(), body)
}

/** List all webhooks in a channel. Requires ManageWebhooks permission. */
suspend fun fetchChannelWebhooks(channelId: String): List<Webhook> {
    val res = StoatHttp.get("/channels/$channelId/webhooks".api())
    val body = res.bodyAsText()

    if (res.status.value !in 200..299) {
        val error = try { StoatJson.decodeFromString(StoatAPIError.serializer(), body) } catch (_: Exception) { null }
        throw Exception(error?.type ?: "HTTP ${res.status.value}")
    }

    return StoatJson.decodeFromString(ListSerializer(Webhook.serializer()), body)
}

// --- Webhook-scoped endpoints (authenticated) ---

/** Fetch a webhook by ID (authenticated). Does NOT include token. */
suspend fun fetchWebhook(webhookId: String): Webhook {
    val res = StoatHttp.get("/webhooks/$webhookId".api())
    val body = res.bodyAsText()

    if (res.status.value !in 200..299) {
        val error = try { StoatJson.decodeFromString(StoatAPIError.serializer(), body) } catch (_: Exception) { null }
        throw Exception(error?.type ?: "HTTP ${res.status.value}")
    }

    return StoatJson.decodeFromString(Webhook.serializer(), body)
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
    val res = StoatHttp.patch("/webhooks/$webhookId".api()) {
        contentType(ContentType.Application.Json)
        setBody(StoatJson.encodeToString(Body.serializer(), body))
    }
    val responseBody = res.bodyAsText()

    if (res.status.value !in 200..299) {
        val error = try { StoatJson.decodeFromString(StoatAPIError.serializer(), responseBody) } catch (_: Exception) { null }
        throw Exception(error?.type ?: "HTTP ${res.status.value}")
    }

    return StoatJson.decodeFromString(Webhook.serializer(), responseBody)
}

/** Delete a webhook (authenticated). Requires ManageWebhooks permission. */
suspend fun deleteWebhook(webhookId: String) {
    val res = StoatHttp.delete("/webhooks/$webhookId".api())
    if (res.status.value !in 200..299) {
        val body = res.bodyAsText()
        val error = try { StoatJson.decodeFromString(StoatAPIError.serializer(), body) } catch (_: Exception) { null }
        throw Exception(error?.type ?: "HTTP ${res.status.value}: $body")
    }
}

// --- Token-authenticated webhook endpoints ---

/** Fetch a webhook by ID and token. Includes the token field. */
suspend fun fetchWebhookWithToken(webhookId: String, token: String): Webhook {
    val res = StoatHttp.get("/webhooks/$webhookId/$token".api())
    val body = res.bodyAsText()

    if (res.status.value !in 200..299) {
        val error = try { StoatJson.decodeFromString(StoatAPIError.serializer(), body) } catch (_: Exception) { null }
        throw Exception(error?.type ?: "HTTP ${res.status.value}")
    }

    return StoatJson.decodeFromString(Webhook.serializer(), body)
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
    val res = StoatHttp.patch("/webhooks/$webhookId/$token".api()) {
        contentType(ContentType.Application.Json)
        setBody(StoatJson.encodeToString(Body.serializer(), body))
    }
    val responseBody = res.bodyAsText()

    if (res.status.value !in 200..299) {
        val error = try { StoatJson.decodeFromString(StoatAPIError.serializer(), responseBody) } catch (_: Exception) { null }
        throw Exception(error?.type ?: "HTTP ${res.status.value}")
    }

    return StoatJson.decodeFromString(Webhook.serializer(), responseBody)
}

/** Delete a webhook using token auth. */
suspend fun deleteWebhookWithToken(webhookId: String, token: String) {
    val res = StoatHttp.delete("/webhooks/$webhookId/$token".api())
    if (res.status.value !in 200..299) {
        val body = res.bodyAsText()
        val error = try { StoatJson.decodeFromString(StoatAPIError.serializer(), body) } catch (_: Exception) { null }
        throw Exception(error?.type ?: "HTTP ${res.status.value}: $body")
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
