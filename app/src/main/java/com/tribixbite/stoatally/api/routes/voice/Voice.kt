package com.tribixbite.stoatally.api.routes.voice

import com.tribixbite.stoatally.api.StoatAPIError
import com.tribixbite.stoatally.api.StoatHttp
import com.tribixbite.stoatally.api.StoatJson
import com.tribixbite.stoatally.api.api
import io.ktor.client.request.post
import io.ktor.client.request.put
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

/**
 * End an incoming voice call ring for a specific user.
 * PUT /channels/{channelId}/end_ring/{userId}
 */
suspend fun endRing(channelId: String, userId: String) {
    val res = StoatHttp.put("/channels/$channelId/end_ring/$userId".api())
    if (res.status.value !in 200..299) {
        val body = res.bodyAsText()
        val error = try { StoatJson.decodeFromString(StoatAPIError.serializer(), body) } catch (_: Exception) { null }
        throw Exception(error?.type ?: "HTTP ${res.status.value}")
    }
}
