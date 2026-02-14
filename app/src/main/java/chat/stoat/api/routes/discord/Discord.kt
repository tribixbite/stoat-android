package chat.stoat.api.routes.discord

import chat.stoat.api.StoatJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/**
 * Client for the stoatcord-bot HTTP API.
 * Used by the Discord Import wizard to fetch guild channel lists.
 */

// Lightweight HTTP client without Stoat auth interceptors
private val discordApiHttp = HttpClient(OkHttp)

// --- Data classes matching stoatcord-bot API responses ---

@Serializable
data class DiscordGuildPreview(
    val id: String,
    val name: String,
    val icon: String? = null,
    val memberCount: Int = 0,
    val channelCount: Int = 0
)

@Serializable
data class DiscordChannelInfo(
    val id: String,
    val name: String,
    val type: String, // "text", "voice", "announcement", "forum", "stage"
    val category: String? = null,
    val position: Int = 0
)

@Serializable
data class DiscordRoleInfo(
    val id: String,
    val name: String,
    val color: String = "#000000",
    val position: Int = 0,
    val memberCount: Int? = null
)

@Serializable
data class DiscordGuildInfo(
    val id: String,
    val name: String,
    val icon: String? = null,
    val description: String? = null,
    val memberCount: Int = 0
)

@Serializable
data class GuildChannelsResponse(
    val guild: DiscordGuildInfo,
    val channels: List<DiscordChannelInfo> = emptyList(),
    val roles: List<DiscordRoleInfo> = emptyList()
)

// --- API calls ---

/**
 * Fetch list of Discord guilds the stoatcord-bot is in.
 * @param botApiUrl Base URL of the stoatcord-bot API (e.g. "http://192.168.1.100:3210")
 * @param apiKey Optional API key for authentication
 */
suspend fun fetchBotGuilds(
    botApiUrl: String,
    apiKey: String = ""
): List<DiscordGuildPreview> {
    val url = "${botApiUrl.trimEnd('/')}/api/guilds"
    val response = discordApiHttp.get(url) {
        if (apiKey.isNotBlank()) header("x-api-key", apiKey)
    }.bodyAsText()

    return StoatJson.decodeFromString(
        ListSerializer(DiscordGuildPreview.serializer()),
        response
    )
}

/**
 * Fetch full channel and role list for a Discord guild.
 * @param botApiUrl Base URL of the stoatcord-bot API
 * @param apiKey Optional API key
 * @param guildId Discord guild ID
 */
suspend fun fetchGuildChannels(
    botApiUrl: String,
    apiKey: String = "",
    guildId: String
): GuildChannelsResponse {
    val url = "${botApiUrl.trimEnd('/')}/api/guilds/$guildId/channels"
    val response = discordApiHttp.get(url) {
        if (apiKey.isNotBlank()) header("x-api-key", apiKey)
    }.bodyAsText()

    return StoatJson.decodeFromString(
        GuildChannelsResponse.serializer(),
        response
    )
}
