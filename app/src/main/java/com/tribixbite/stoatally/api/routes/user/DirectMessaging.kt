package com.tribixbite.stoatally.api.routes.user

import com.tribixbite.stoatally.api.StoatAPIError
import com.tribixbite.stoatally.api.StoatHttp
import com.tribixbite.stoatally.api.StoatJson
import com.tribixbite.stoatally.api.api
import com.tribixbite.stoatally.core.model.schemas.Channel
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

suspend fun openDM(userId: String): Channel {
    val res = StoatHttp.get("/users/$userId/dm".api())
    val body = res.bodyAsText()

    if (res.status.value !in 200..299) {
        val error = try { StoatJson.decodeFromString(StoatAPIError.serializer(), body) } catch (_: Exception) { null }
        throw Error(error?.type ?: "HTTP ${res.status.value}")
    }

    return StoatJson.decodeFromString(Channel.serializer(), body)
}
