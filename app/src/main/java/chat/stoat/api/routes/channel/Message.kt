package chat.stoat.api.routes.channel

import chat.stoat.api.StoatAPI
import chat.stoat.api.StoatHttp
import chat.stoat.api.StoatJson
import chat.stoat.api.api
import io.ktor.client.request.delete
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

suspend fun react(channelId: String, messageId: String, emoji: String) {
    StoatHttp.put("/channels/$channelId/messages/$messageId/reactions/$emoji".api())
}

suspend fun unreact(channelId: String, messageId: String, emoji: String) {
    StoatHttp.delete("/channels/$channelId/messages/$messageId/reactions/$emoji".api())
}

/**
 * Pin a message in a channel.
 * Requires ManageMessages permission.
 * Pinned status is mutually exclusive with query in search.
 */
suspend fun pinMessage(channelId: String, messageId: String) {
    StoatHttp.put("/channels/$channelId/messages/$messageId/pin".api())
    // Update local cache: set pinned flag on cached message
    StoatAPI.messageCache[messageId]?.let { msg ->
        StoatAPI.messageCache[messageId] = msg.copy(pinned = true)
    }
}

/**
 * Unpin a message in a channel.
 * Requires ManageMessages permission.
 */
suspend fun unpinMessage(channelId: String, messageId: String) {
    StoatHttp.delete("/channels/$channelId/messages/$messageId/pin".api())
    // Update local cache: clear pinned flag on cached message
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
    StoatHttp.delete("/channels/$channelId/messages/bulk".api()) {
        contentType(ContentType.Application.Json)
        setBody(StoatJson.encodeToString(BulkDeleteBody.serializer(), BulkDeleteBody(messageIds)))
    }
    // Remove from local cache
    messageIds.forEach { StoatAPI.messageCache.remove(it) }
}