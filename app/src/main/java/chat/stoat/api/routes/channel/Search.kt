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
import io.ktor.http.isSuccess
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
 * Sealed result type for search operations.
 * Surfaces errors to the UI instead of swallowing them.
 */
sealed class SearchResult {
    data class Success(val data: MessagesInChannel) : SearchResult()
    data class Error(val message: String) : SearchResult()
}

/**
 * Search messages in a channel using the Revolt API.
 * POST /channels/{channelId}/search
 * Returns [SearchResult] so callers can display errors in the UI.
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
): SearchResult {
    val body = MessageSearchRequest(
        query = query,
        limit = limit,
        before = before,
        after = after,
        sort = sort,
        includeUsers = includeUsers,
        pinned = pinned
    )

    val httpResponse = StoatHttp.post("/channels/$channelId/search".api()) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    val responseText = httpResponse.bodyAsText()

    // Check HTTP status before parsing
    if (!httpResponse.status.isSuccess()) {
        val errorMsg = "HTTP ${httpResponse.status.value}: $responseText"
        Log.e("Search", "Search API error: $errorMsg")
        return SearchResult.Error(errorMsg)
    }

    Log.d("Search", "Search response (first 500 chars): ${responseText.take(500)}")

    // Revolt API returns MessagesInChannel when include_users=true,
    // or a plain Message[] array when include_users is false/absent
    return try {
        val data = if (includeUsers == true) {
            try {
                StoatJson.decodeFromString(MessagesInChannel.serializer(), responseText)
            } catch (e: Exception) {
                // Fallback: try parsing as plain message array
                Log.w("Search", "Trying array fallback: ${e.message}")
                val messages = StoatJson.decodeFromString(
                    ListSerializer(Message.serializer()), responseText
                )
                MessagesInChannel(messages = messages, users = emptyList(), members = emptyList())
            }
        } else {
            val messages = StoatJson.decodeFromString(
                ListSerializer(Message.serializer()), responseText
            )
            MessagesInChannel(messages = messages, users = emptyList(), members = emptyList())
        }
        SearchResult.Success(data)
    } catch (e: Exception) {
        val errorMsg = "Parse error: ${e.message}\nResponse: ${responseText.take(200)}"
        Log.e("Search", errorMsg)
        SearchResult.Error(errorMsg)
    }
}
