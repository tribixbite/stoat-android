package com.tribixbite.stoatally.api.routes.sync

import com.tribixbite.stoatally.api.StoatHttp
import com.tribixbite.stoatally.api.StoatJson
import com.tribixbite.stoatally.api.api
import com.tribixbite.stoatally.core.model.schemas.ChannelUnreadResponse
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.builtins.ListSerializer

suspend fun syncUnreads(): List<ChannelUnreadResponse> {
    val res = StoatHttp.get("/sync/unreads".api())
    val body = res.bodyAsText()

    if (res.status.value !in 200..299) {
        throw Exception("HTTP ${res.status.value}: $body")
    }

    return StoatJson.decodeFromString(
        ListSerializer(ChannelUnreadResponse.serializer()),
        body
    )
}
