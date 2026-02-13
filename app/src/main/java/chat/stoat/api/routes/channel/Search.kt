package chat.stoat.api.routes.channel

import chat.stoat.api.StoatHttp
import chat.stoat.api.StoatJson
import chat.stoat.api.api
import chat.stoat.core.model.schemas.MessagesInChannel
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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

    val response = StoatHttp.post("/channels/$channelId/search".api()) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }.bodyAsText()

    return StoatJson.decodeFromString(
        MessagesInChannel.serializer(),
        response
    )
}
