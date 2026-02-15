package chat.stoat.api.routes.bots

import chat.stoat.api.StoatAPIError
import chat.stoat.api.StoatHttp
import chat.stoat.api.StoatJson
import chat.stoat.api.api
import chat.stoat.core.model.schemas.User
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

/** Full bot object returned by the API */
@Serializable
data class BotInfo(
    @SerialName("_id")
    val id: String? = null,
    val owner: String? = null,
    val name: String? = null,
    val public: Boolean? = null,
    val analytics: Boolean? = null,
    @SerialName("interactions_url")
    val interactionsUrl: String? = null,
    val token: String? = null
)

/** Response for bot endpoints that return bot + user */
@Serializable
data class BotWithUserResponse(
    val bot: BotInfo,
    val user: User
)

/** Response for listing owned bots */
@Serializable
data class OwnedBotsResponse(
    val bots: List<BotInfo>,
    val users: List<User>
)

/** Public bot info (no token) */
@Serializable
data class PublicBot(
    @SerialName("_id")
    val id: String? = null,
    val username: String? = null,
    val avatar: String? = null,
    val description: String? = null
)

// --- API functions ---

/** Create a new bot. Returns the bot and its user object. */
suspend fun createBot(name: String): BotWithUserResponse {
    @Serializable
    data class Body(val name: String)

    val response = StoatHttp.post("/bots/create".api()) {
        contentType(ContentType.Application.Json)
        setBody(StoatJson.encodeToString(Body.serializer(), Body(name)))
    }.bodyAsText()

    try {
        val error = StoatJson.decodeFromString(StoatAPIError.serializer(), response)
        throw Exception(error.type)
    } catch (_: SerializationException) {}

    return StoatJson.decodeFromString(BotWithUserResponse.serializer(), response)
}

/** Fetch all bots owned by the current user. */
suspend fun fetchOwnedBots(): OwnedBotsResponse {
    val response = StoatHttp.get("/bots/@me".api()).bodyAsText()

    try {
        val error = StoatJson.decodeFromString(StoatAPIError.serializer(), response)
        throw Exception(error.type)
    } catch (_: SerializationException) {}

    return StoatJson.decodeFromString(OwnedBotsResponse.serializer(), response)
}

/** Fetch a specific bot by ID. Must be owner. */
suspend fun fetchBot(botId: String): BotWithUserResponse {
    val response = StoatHttp.get("/bots/$botId".api()).bodyAsText()

    try {
        val error = StoatJson.decodeFromString(StoatAPIError.serializer(), response)
        throw Exception(error.type)
    } catch (_: SerializationException) {}

    return StoatJson.decodeFromString(BotWithUserResponse.serializer(), response)
}

/** Edit a bot's properties (name, public, analytics, interactions_url). */
suspend fun editBot(
    botId: String,
    name: String? = null,
    public: Boolean? = null,
    analytics: Boolean? = null,
    interactionsUrl: String? = null,
    remove: List<String>? = null
): BotWithUserResponse {
    @Serializable
    data class Body(
        val name: String? = null,
        val public: Boolean? = null,
        val analytics: Boolean? = null,
        @SerialName("interactions_url")
        val interactionsUrl: String? = null,
        val remove: List<String>? = null
    )

    val body = Body(name, public, analytics, interactionsUrl, remove)
    val response = StoatHttp.patch("/bots/$botId".api()) {
        contentType(ContentType.Application.Json)
        setBody(StoatJson.encodeToString(Body.serializer(), body))
    }.bodyAsText()

    try {
        val error = StoatJson.decodeFromString(StoatAPIError.serializer(), response)
        throw Exception(error.type)
    } catch (_: SerializationException) {}

    return StoatJson.decodeFromString(BotWithUserResponse.serializer(), response)
}

/** Delete a bot permanently. */
suspend fun deleteBot(botId: String) {
    val response = StoatHttp.delete("/bots/$botId".api())
    if (response.status.value !in 200..299) {
        val body = response.bodyAsText()
        try {
            val error = StoatJson.decodeFromString(StoatAPIError.serializer(), body)
            throw Exception(error.type)
        } catch (_: SerializationException) {
            throw Exception("HTTP ${response.status.value}: $body")
        }
    }
}

/** Invite a bot to a server or group. */
suspend fun inviteBot(botId: String, serverId: String? = null, groupId: String? = null) {
    @Serializable
    data class Body(
        val server: String? = null,
        val group: String? = null
    )

    val body = Body(server = serverId, group = groupId)
    val response = StoatHttp.post("/bots/$botId/invite".api()) {
        contentType(ContentType.Application.Json)
        setBody(StoatJson.encodeToString(Body.serializer(), body))
    }
    if (response.status.value !in 200..299) {
        val responseBody = response.bodyAsText()
        try {
            val error = StoatJson.decodeFromString(StoatAPIError.serializer(), responseBody)
            throw Exception(error.type)
        } catch (_: SerializationException) {
            throw Exception("HTTP ${response.status.value}: $responseBody")
        }
    }
}

/** Fetch public bot info for invite page. */
suspend fun fetchPublicBot(botId: String): PublicBot {
    val response = StoatHttp.get("/bots/$botId/invite".api()).bodyAsText()

    try {
        val error = StoatJson.decodeFromString(StoatAPIError.serializer(), response)
        throw Exception(error.type)
    } catch (_: SerializationException) {}

    return StoatJson.decodeFromString(PublicBot.serializer(), response)
}
