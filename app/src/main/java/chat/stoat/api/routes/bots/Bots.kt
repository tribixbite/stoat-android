package chat.stoat.api.routes.bots

import chat.stoat.api.StoatAPI
import chat.stoat.api.StoatAPIError
import chat.stoat.api.StoatHttp
import chat.stoat.api.StoatJson
import chat.stoat.api.api
import chat.stoat.core.model.schemas.User
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement

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

    val res = StoatHttp.post("/bots/create".api()) {
        contentType(ContentType.Application.Json)
        setBody(StoatJson.encodeToString(Body.serializer(), Body(name)))
    }
    val body = res.bodyAsText()

    if (res.status.value !in 200..299) {
        val error = try { StoatJson.decodeFromString(StoatAPIError.serializer(), body) } catch (_: Exception) { null }
        throw Exception(error?.type ?: "HTTP ${res.status.value}")
    }

    return StoatJson.decodeFromString(BotWithUserResponse.serializer(), body)
}

/** Fetch all bots owned by the current user. */
suspend fun fetchOwnedBots(): OwnedBotsResponse {
    val res = StoatHttp.get("/bots/@me".api())
    val body = res.bodyAsText()

    if (res.status.value !in 200..299) {
        val error = try { StoatJson.decodeFromString(StoatAPIError.serializer(), body) } catch (_: Exception) { null }
        throw Exception(error?.type ?: "HTTP ${res.status.value}")
    }

    return StoatJson.decodeFromString(OwnedBotsResponse.serializer(), body)
}

/** Fetch a specific bot by ID. Must be owner. */
suspend fun fetchBot(botId: String): BotWithUserResponse {
    val res = StoatHttp.get("/bots/$botId".api())
    val body = res.bodyAsText()

    if (res.status.value !in 200..299) {
        val error = try { StoatJson.decodeFromString(StoatAPIError.serializer(), body) } catch (_: Exception) { null }
        throw Exception(error?.type ?: "HTTP ${res.status.value}")
    }

    return StoatJson.decodeFromString(BotWithUserResponse.serializer(), body)
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
    val res = StoatHttp.patch("/bots/$botId".api()) {
        contentType(ContentType.Application.Json)
        setBody(StoatJson.encodeToString(Body.serializer(), body))
    }
    val responseBody = res.bodyAsText()

    if (res.status.value !in 200..299) {
        val error = try { StoatJson.decodeFromString(StoatAPIError.serializer(), responseBody) } catch (_: Exception) { null }
        throw Exception(error?.type ?: "HTTP ${res.status.value}")
    }

    return StoatJson.decodeFromString(BotWithUserResponse.serializer(), responseBody)
}

/** Delete a bot permanently. */
suspend fun deleteBot(botId: String) {
    val res = StoatHttp.delete("/bots/$botId".api())
    if (res.status.value !in 200..299) {
        val body = res.bodyAsText()
        val error = try { StoatJson.decodeFromString(StoatAPIError.serializer(), body) } catch (_: Exception) { null }
        throw Exception(error?.type ?: "HTTP ${res.status.value}: $body")
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
    val res = StoatHttp.post("/bots/$botId/invite".api()) {
        contentType(ContentType.Application.Json)
        setBody(StoatJson.encodeToString(Body.serializer(), body))
    }
    if (res.status.value !in 200..299) {
        val responseBody = res.bodyAsText()
        val error = try { StoatJson.decodeFromString(StoatAPIError.serializer(), responseBody) } catch (_: Exception) { null }
        throw Exception(error?.type ?: "HTTP ${res.status.value}: $responseBody")
    }
}

/**
 * Edit the bot's user profile (avatar, bio/description) by authenticating
 * as the bot itself. The PATCH /bots/{id} endpoint only supports name/public/analytics/
 * interactions_url — avatar and description are on the bot's User object, requiring
 * the bot's own token to PATCH /users/@me.
 */
suspend fun editBotUserProfile(
    botToken: String,
    avatar: String? = null,
    bio: String? = null,
    remove: List<String>? = null
): User {
    val bodyMap = mutableMapOf<String, JsonElement>()

    if (avatar != null) {
        bodyMap["avatar"] = StoatJson.encodeToJsonElement(String.serializer(), avatar)
    }

    if (bio != null) {
        bodyMap["profile"] = StoatJson.encodeToJsonElement(
            MapSerializer(String.serializer(), String.serializer()),
            mapOf("content" to bio)
        )
    }

    if (remove != null) {
        bodyMap["remove"] = StoatJson.encodeToJsonElement(
            ListSerializer(String.serializer()),
            remove
        )
    }

    val res = StoatHttp.patch("/users/@me".api()) {
        // Override the session token interceptor by setting it explicitly to the bot token
        header(StoatAPI.TOKEN_HEADER_NAME, botToken)
        contentType(ContentType.Application.Json)
        setBody(
            StoatJson.encodeToString(
                MapSerializer(String.serializer(), JsonElement.serializer()),
                bodyMap
            )
        )
    }
    val responseBody = res.bodyAsText()

    if (res.status.value !in 200..299) {
        val error = try { StoatJson.decodeFromString(StoatAPIError.serializer(), responseBody) } catch (_: Exception) { null }
        throw Exception(error?.type ?: "HTTP ${res.status.value}")
    }

    return StoatJson.decodeFromString(User.serializer(), responseBody)
}

/** Fetch public bot info for invite page. */
suspend fun fetchPublicBot(botId: String): PublicBot {
    val res = StoatHttp.get("/bots/$botId/invite".api())
    val body = res.bodyAsText()

    if (res.status.value !in 200..299) {
        val error = try { StoatJson.decodeFromString(StoatAPIError.serializer(), body) } catch (_: Exception) { null }
        throw Exception(error?.type ?: "HTTP ${res.status.value}")
    }

    return StoatJson.decodeFromString(PublicBot.serializer(), body)
}
