package com.tribixbite.stoatally.api.routes.custom

import com.tribixbite.stoatally.api.StoatHttp
import com.tribixbite.stoatally.api.StoatJson
import com.tribixbite.stoatally.api.api
import com.tribixbite.stoatally.core.model.schemas.Emoji
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

suspend fun fetchEmoji(id: String): Emoji {
    val response = StoatHttp.get("/custom/emoji/$id".api()).bodyAsText()
    return StoatJson.decodeFromString(
        Emoji.serializer(),
        response
    )
}

/** Fetch all emoji for a server. */
suspend fun fetchServerEmoji(serverId: String): List<Emoji> {
    // Emoji are part of server object but can also be fetched from emojiCache
    // The server cache already has them via server.emoji field
    // For a dedicated fetch, we filter the emoji cache by parent server ID
    return com.tribixbite.stoatally.api.StoatAPI.emojiCache.values
        .filter { it.parent?.id == serverId }
        .toList()
}

@Serializable
data class CreateEmojiBody(
    val name: String,
    val parent: EmojiParentBody,
    val nsfw: Boolean = false
)

@Serializable
data class EmojiParentBody(
    val type: String = "Server",
    val id: String
)

/**
 * Create a custom emoji. The id parameter should be the Autumn file ID
 * from uploading the image to autumn/emojis.
 */
suspend fun createEmoji(
    emojiId: String,
    name: String,
    serverId: String,
    nsfw: Boolean = false
): Emoji {
    val body = CreateEmojiBody(
        name = name,
        parent = EmojiParentBody(id = serverId),
        nsfw = nsfw
    )
    val response = StoatHttp.put("/custom/emoji/$emojiId".api()) {
        contentType(ContentType.Application.Json)
        setBody(StoatJson.encodeToString(CreateEmojiBody.serializer(), body))
    }.bodyAsText()
    return StoatJson.decodeFromString(Emoji.serializer(), response)
}

/** Delete a custom emoji. */
suspend fun deleteEmoji(emojiId: String): String? {
    val response = StoatHttp.delete("/custom/emoji/$emojiId".api())
    return if (response.status.isSuccess()) null
    else "HTTP ${response.status.value}: ${response.bodyAsText()}"
}
