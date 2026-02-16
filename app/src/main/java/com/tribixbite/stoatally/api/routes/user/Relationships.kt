package com.tribixbite.stoatally.api.routes.user

import com.tribixbite.stoatally.api.StoatAPI
import com.tribixbite.stoatally.api.StoatAPIError
import com.tribixbite.stoatally.api.StoatHttp
import com.tribixbite.stoatally.api.StoatJson
import com.tribixbite.stoatally.api.api
import com.tribixbite.stoatally.core.model.schemas.User
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType

suspend fun blockUser(userId: String) {
    val res = StoatHttp.put("/users/$userId/block".api())

    val body = res.bodyAsText()
    if (res.status.value !in 200..299) {
        val error = try { StoatJson.decodeFromString(StoatAPIError.serializer(), body) } catch (_: Exception) { null }
        throw Exception(error?.type ?: "HTTP ${res.status.value}")
    }

    // Update user cache immediately so UI reflects the block (#39)
    try {
        val user = StoatJson.decodeFromString(User.serializer(), body)
        user.id?.let { StoatAPI.userCache[it] = user }
    } catch (_: Exception) { /* WebSocket event will update cache as fallback */ }
}

suspend fun unblockUser(userId: String) {
    val res = StoatHttp.delete("/users/$userId/block".api())

    val body = res.bodyAsText()
    if (res.status.value !in 200..299) {
        val error = try { StoatJson.decodeFromString(StoatAPIError.serializer(), body) } catch (_: Exception) { null }
        throw Exception(error?.type ?: "HTTP ${res.status.value}")
    }

    // Update user cache immediately so UI reflects the unblock (#39)
    try {
        val user = StoatJson.decodeFromString(User.serializer(), body)
        user.id?.let { StoatAPI.userCache[it] = user }
    } catch (_: Exception) { /* WebSocket event will update cache as fallback */ }
}

suspend fun friendUser(username: String) {
    val res = StoatHttp.post("/users/friend".api()) {
        contentType(ContentType.Application.Json)
        setBody(mapOf("username" to username))
    }

    val body = res.bodyAsText()
    if (res.status.value !in 200..299) {
        val error = try { StoatJson.decodeFromString(StoatAPIError.serializer(), body) } catch (_: Exception) { null }
        throw Exception(error?.type ?: "HTTP ${res.status.value}")
    }

    // Update user cache immediately so FriendsScreen reflects the new relationship (#39)
    try {
        val user = StoatJson.decodeFromString(User.serializer(), body)
        user.id?.let { StoatAPI.userCache[it] = user }
    } catch (_: Exception) { /* WebSocket event will update cache as fallback */ }
}

suspend fun acceptFriendRequest(userId: String) {
    val res = StoatHttp.put("/users/$userId/friend".api())

    val body = res.bodyAsText()
    if (res.status.value !in 200..299) {
        val error = try { StoatJson.decodeFromString(StoatAPIError.serializer(), body) } catch (_: Exception) { null }
        throw Exception(error?.type ?: "HTTP ${res.status.value}")
    }

    // Update user cache immediately so FriendsScreen reflects the new relationship (#39)
    try {
        val user = StoatJson.decodeFromString(User.serializer(), body)
        user.id?.let { StoatAPI.userCache[it] = user }
    } catch (_: Exception) { /* WebSocket event will update cache as fallback */ }
}

suspend fun unfriendUser(userId: String) {
    val res = StoatHttp.delete("/users/$userId/friend".api())

    val body = res.bodyAsText()
    if (res.status.value !in 200..299) {
        val error = try { StoatJson.decodeFromString(StoatAPIError.serializer(), body) } catch (_: Exception) { null }
        throw Exception(error?.type ?: "HTTP ${res.status.value}")
    }

    // Update user cache immediately so FriendsScreen reflects the removal (#39)
    try {
        val user = StoatJson.decodeFromString(User.serializer(), body)
        user.id?.let { StoatAPI.userCache[it] = user }
    } catch (_: Exception) { /* WebSocket event will update cache as fallback */ }
}
