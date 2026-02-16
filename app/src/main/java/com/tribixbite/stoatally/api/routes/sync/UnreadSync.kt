package com.tribixbite.stoatally.api.routes.sync

import com.tribixbite.stoatally.api.StoatHttp
import com.tribixbite.stoatally.api.StoatJson
import com.tribixbite.stoatally.api.api
import com.tribixbite.stoatally.core.model.schemas.ChannelUnreadResponse
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.builtins.ListSerializer

suspend fun syncUnreads(): List<ChannelUnreadResponse> {
    val response = StoatHttp.get("/sync/unreads".api())
        .bodyAsText()

    return StoatJson.decodeFromString(
        ListSerializer(ChannelUnreadResponse.serializer()),
        response
    )
}
