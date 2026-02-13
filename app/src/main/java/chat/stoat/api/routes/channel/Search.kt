package chat.stoat.api.routes.channel

import android.util.Log
import chat.stoat.api.StoatHttp
import chat.stoat.api.StoatJson
import chat.stoat.api.api
import chat.stoat.core.model.schemas.Message
import chat.stoat.core.model.schemas.MessagesInChannel
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
data class MessageSearchRequest(
    val query: String,
    val limit: Int? = null,
    val before: String? = null,
    val after: String? = null,
    val sort: String? = null,
    @SerialName("include_users")
    val includeUsers: Boolean? = null,
    val pinned: Boolean? = null
)

/**
 * Search messages in a channel using the Revolt API.
 * POST /channels/{channelId}/search
 */
suspend fun searchMessages(
    channelId: String,
    query: String,
    limit: Int? = 25,
    before: String? = null,
    after: String? = null,
    sort: String? = null,
    includeUsers: Boolean? = true,
    pinned: Boolean? = null
): MessagesInChannel {
    val body = MessageSearchRequest(
        query = query,
        limit = limit,
        before = before,
        after = after,
        sort = sort,
        includeUsers = includeUsers,
        pinned = pinned
    )

    // Explicitly serialize to JSON string — ContentNegotiation may not
    // pick up the @Serializable data class depending on Ktor config
    val bodyJson = StoatJson.encodeToString(MessageSearchRequest.serializer(), body)

    val response = StoatHttp.post("/channels/$channelId/search".api()) {
        contentType(ContentType.Application.Json)
        setBody(bodyJson)
    }.bodyAsText()

    Log.d("Search", "Search response (first 500 chars): ${response.take(500)}")

    // Revolt API returns MessagesInChannel when include_users=true,
    // or a plain Message[] array when include_users is false/absent
    return if (includeUsers == true) {
        try {
            StoatJson.decodeFromString(MessagesInChannel.serializer(), response)
        } catch (e: Exception) {
            // Fallback: try parsing as plain message array
            Log.w("Search", "Failed to parse as MessagesInChannel, trying array: ${e.message}")
            val messages = StoatJson.decodeFromString(ListSerializer(Message.serializer()), response)
            MessagesInChannel(messages = messages, users = emptyList(), members = emptyList())
        }
    } else {
        val messages = StoatJson.decodeFromString(ListSerializer(Message.serializer()), response)
        MessagesInChannel(messages = messages, users = emptyList(), members = emptyList())
    }
}
