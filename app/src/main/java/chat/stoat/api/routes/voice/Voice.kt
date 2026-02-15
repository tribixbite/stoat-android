package chat.stoat.api.routes.voice

import chat.stoat.api.StoatAPIError
import chat.stoat.api.StoatHttp
import chat.stoat.api.StoatJson
import chat.stoat.api.api
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

@Serializable
data class JoinCallResponse(
    val token: String,
    val url: String,
)

suspend fun joinCall(channelId: String, nodeName: String): JoinCallResponse {
    val res = StoatHttp.post("/channels/$channelId/join_call".api()) {
        contentType(ContentType.Application.Json)
        setBody(mapOf("node" to nodeName))
    }
    val body = res.bodyAsText()

    if (res.status.value !in 200..299) {
        val error = try { StoatJson.decodeFromString(StoatAPIError.serializer(), body) } catch (_: Exception) { null }
        throw Exception(error?.type ?: "HTTP ${res.status.value}")
    }

    return StoatJson.decodeFromString(JoinCallResponse.serializer(), body)
}
