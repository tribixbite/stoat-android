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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
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

/** Max time for a single search request (prevents HttpRequestRetry from
 *  retrying SocketTimeoutException 5× with exponential backoff). */
private const val SEARCH_TIMEOUT_MS = 15_000L

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

    val responseText: String
    val statusCode: Int

    try {
        // Coroutine timeout caps total time including Ktor's retry mechanism.
        // Without this, HttpRequestRetry retries SocketTimeoutException 5×
        // with exponential backoff (potentially 5+ minutes).
        val httpResponse = withTimeout(SEARCH_TIMEOUT_MS) {
            StoatHttp.post("/channels/$channelId/search".api()) {
                contentType(ContentType.Application.Json)
                setBody(body)
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
        // Rethrow cancellation (coroutine was cancelled by user action)
        throw e
    } catch (e: Exception) {
        val errorMsg = "${e.javaClass.simpleName}: ${e.message}"
        Log.e("Search", "Search request failed: $errorMsg")
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
