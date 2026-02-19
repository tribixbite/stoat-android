package com.tribixbite.stoatally.api.routes.channel

import com.tribixbite.stoatally.api.StoatAPI
import com.tribixbite.stoatally.api.StoatAPIError
import com.tribixbite.stoatally.api.StoatHttp
import com.tribixbite.stoatally.api.StoatJson
import com.tribixbite.stoatally.api.api
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

suspend fun react(channelId: String, messageId: String, emoji: String) {
    val res = StoatHttp.put("/channels/$channelId/messages/$messageId/reactions/$emoji".api())
    if (res.status.value !in 200..299) {
        val body = res.bodyAsText()
        val error = try { StoatJson.decodeFromString(StoatAPIError.serializer(), body) } catch (_: Exception) { null }
        throw Exception(error?.type ?: "HTTP ${res.status.value}")
    }
}

suspend fun unreact(channelId: String, messageId: String, emoji: String) {
    val res = StoatHttp.delete("/channels/$channelId/messages/$messageId/reactions/$emoji".api())
    if (res.status.value !in 200..299) {
        val body = res.bodyAsText()
        val error = try { StoatJson.decodeFromString(StoatAPIError.serializer(), body) } catch (_: Exception) { null }
        throw Exception(error?.type ?: "HTTP ${res.status.value}")
    }
}

/**
 * Remove all reactions from a message.
 * Requires ManageMessages permission.
 */
suspend fun removeAllReactions(channelId: String, messageId: String) {
    val res = StoatHttp.delete("/channels/$channelId/messages/$messageId/reactions".api())
    if (res.status.value !in 200..299) {
        val body = res.bodyAsText()
        val error = try { StoatJson.decodeFromString(StoatAPIError.serializer(), body) } catch (_: Exception) { null }
        throw Exception(error?.type ?: "HTTP ${res.status.value}")
    }
    // Update local cache only after server confirms success
    StoatAPI.messageCache[messageId]?.let { msg ->
        StoatAPI.messageCache[messageId] = msg.copy(reactions = null)
    }
}

/**
 * Pin a message in a channel.
 * Requires ManageMessages permission.
 * Pinned status is mutually exclusive with query in search.
 */
suspend fun pinMessage(channelId: String, messageId: String) {
    val res = StoatHttp.post("/channels/$channelId/messages/$messageId/pin".api())
    if (res.status.value !in 200..299) {
        val body = res.bodyAsText()
        val error = try { StoatJson.decodeFromString(StoatAPIError.serializer(), body) } catch (_: Exception) { null }
        throw Exception(error?.type ?: "HTTP ${res.status.value}")
    }
    // Update local cache only after server confirms success
    StoatAPI.messageCache[messageId]?.let { msg ->
        StoatAPI.messageCache[messageId] = msg.copy(pinned = true)
    }
}

/**
 * Unpin a message in a channel.
 * Requires ManageMessages permission.
 */
suspend fun unpinMessage(channelId: String, messageId: String) {
    val res = StoatHttp.delete("/channels/$channelId/messages/$messageId/pin".api())
    if (res.status.value !in 200..299) {
        val body = res.bodyAsText()
        val error = try { StoatJson.decodeFromString(StoatAPIError.serializer(), body) } catch (_: Exception) { null }
        throw Exception(error?.type ?: "HTTP ${res.status.value}")
    }
    // Update local cache only after server confirms success
    StoatAPI.messageCache[messageId]?.let { msg ->
        StoatAPI.messageCache[messageId] = msg.copy(pinned = false)
    }
}

@Serializable
data class BulkDeleteBody(val ids: List<String>)

/**
 * Bulk delete messages in a channel.
 * Requires ManageMessages permission.
 * Max 100 messages per request.
 */
suspend fun bulkDeleteMessages(channelId: String, messageIds: List<String>) {
    val res = StoatHttp.delete("/channels/$channelId/messages/bulk".api()) {
        contentType(ContentType.Application.Json)
        setBody(StoatJson.encodeToString(BulkDeleteBody.serializer(), BulkDeleteBody(messageIds)))
    }
    if (res.status.value !in 200..299) {
        val body = res.bodyAsText()
        val error = try { StoatJson.decodeFromString(StoatAPIError.serializer(), body) } catch (_: Exception) { null }
        throw Exception(error?.type ?: "HTTP ${res.status.value}")
    }
    // Remove from local cache only after server confirms success
    messageIds.forEach { StoatAPI.messageCache.remove(it) }
}