package chat.stoat.api.routes.channel

import android.util.Log
import chat.stoat.api.StoatHttp
import chat.stoat.api.StoatJson
import chat.stoat.api.api
import chat.stoat.core.model.schemas.Message
import chat.stoat.core.model.schemas.MessagesInChannel
import io.ktor.client.plugins.timeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/**
 * Request body for POST /channels/{channelId}/search.
 *
 * Per API spec, [query] and [pinned] are mutually exclusive:
 * - Text search: send query (1-64 chars), optionally with sort/limit/before/after
 * - Pinned browse: send pinned=true only, with sort/limit/before/after
 *
 * Query uses MongoDB $text $search syntax:
 * - Multiple words: OR match (any word)
 * - "exact phrase": wrap in escaped quotes
 * - -negation: prefix with hyphen to exclude
 * - Stemming: "running" matches "run", "runs", etc.
 */
@Serializable
data class MessageSearchRequest(
    val query: String? = null,
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
 *
 * Uses per-request HttpTimeout overrides (30s socket, 45s request) because
 * the search endpoint is significantly slower than other API calls (~3-10s).
 *
 * @param query Text to search for (1-64 chars). Null for pinned-only browse.
 * @param includeUsers True returns {messages,users,members}; false returns Message[].
 *                     Use false for speed when user data is already cached.
 * @param pinned True to browse pinned messages only (mutually exclusive with query).
 * @param limit 1-100, validated server-side. Default 10 for performance.
 * @param sort "Relevance", "Latest", or "Oldest" (PascalCase required).
 */
suspend fun searchMessages(
    channelId: String,
    query: String? = null,
    limit: Int? = 10,
    before: String? = null,
    after: String? = null,
    sort: String? = null,
    includeUsers: Boolean? = null,
    pinned: Boolean? = null
): SearchResult {
    // API requires either query or pinned, not both
    val body = MessageSearchRequest(
        query = if (pinned == true) null else query,
        limit = limit?.coerceIn(1, 100),
        before = before,
        after = after,
        sort = sort,
        includeUsers = includeUsers,
        pinned = if (pinned == true) true else null
    )

    val responseText: String
    val statusCode: Int

    try {
        val httpResponse = StoatHttp.post("/channels/$channelId/search".api()) {
            contentType(ContentType.Application.Json)
            setBody(body)
            // Search endpoint is slow (~3-30s+ for large channels).
            // socketTimeoutMillis: max time waiting for server response data.
            // requestTimeoutMillis: total cap including Ktor's 5x retry with exponential backoff.
            // Without the request cap, a 60s socket timeout retried 5x = 5+ min hang.
            timeout {
                socketTimeoutMillis = 60_000
                requestTimeoutMillis = 75_000
            }
        }
        statusCode = httpResponse.status.value
        responseText = httpResponse.bodyAsText()

        if (!httpResponse.status.isSuccess()) {
            val errorMsg = "HTTP $statusCode: ${responseText.take(300)}"
            Log.e("Search", "Search API error: $errorMsg")
            return SearchResult.Error(errorMsg)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val errorMsg = "${e.javaClass.simpleName}: ${e.message}"
        Log.e("Search", "Search request failed: $errorMsg")
        return SearchResult.Error(errorMsg)
    }

    Log.d("Search", "Search response (first 500 chars): ${responseText.take(500)}")

    // Response format depends on include_users:
    // - true: JSON object {messages: [], users: [], members?: []}
    // - false/null: bare JSON array of Message objects
    return try {
        val data = if (includeUsers == true) {
            try {
                StoatJson.decodeFromString(MessagesInChannel.serializer(), responseText)
            } catch (e: Exception) {
                Log.w("Search", "Object parse failed, trying array fallback: ${e.message}")
                val messages = StoatJson.decodeFromString(
                    ListSerializer(Message.serializer()), responseText
                )
                MessagesInChannel(messages = messages, users = emptyList(), members = emptyList())
            }
        } else {
            // Without include_users, API returns bare Message[] array
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
