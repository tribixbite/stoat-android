package chat.stoat.api.routes.channel

import chat.stoat.api.StoatAPIError
import chat.stoat.api.StoatHttp
import chat.stoat.api.StoatJson
import chat.stoat.api.api
import chat.stoat.core.model.schemas.Channel
import chat.stoat.screens.create.MAX_ADDABLE_PEOPLE_IN_GROUP
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

@Serializable
data class CreateGroupDMBody(
    val name: String,
    val users: List<String>
)

suspend fun createGroupDM(name: String, members: List<String>): Channel {
    if (members.size > MAX_ADDABLE_PEOPLE_IN_GROUP) {
        throw Exception("Too many members, maximum is $MAX_ADDABLE_PEOPLE_IN_GROUP")
    }

    val res = StoatHttp.post("/channels/create".api()) {
        contentType(ContentType.Application.Json)
        setBody(CreateGroupDMBody(name, members))
    }
    val body = res.bodyAsText()

    if (res.status.value !in 200..299) {
        val error = try { StoatJson.decodeFromString(StoatAPIError.serializer(), body) } catch (_: Exception) { null }
        throw Error(error?.type ?: "HTTP ${res.status.value}")
    }

    return StoatJson.decodeFromString(Channel.serializer(), body)
}

suspend fun removeMember(channelId: String, userId: String) {
    val response = StoatHttp.delete("/channels/$channelId/recipients/$userId".api())

    if (!response.status.isSuccess()) {
        throw Error(response.status.toString())
    }
}

suspend fun addMember(channelId: String, userId: String) {
    val response = StoatHttp.put("/channels/$channelId/recipients/$userId".api())

    if (!response.status.isSuccess()) {
        throw Error(response.status.toString())
    }
}
