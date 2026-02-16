package com.tribixbite.stoatally.api.routes.microservices.health

import com.tribixbite.stoatally.api.StoatHttp
import com.tribixbite.stoatally.api.StoatJson
import com.tribixbite.stoatally.core.model.schemas.HealthNotice
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

suspend fun healthCheck(): HealthNotice {
    val response = StoatHttp.get("https://health.revolt.chat/api/health").bodyAsText()
    return StoatJson.decodeFromString(HealthNotice.serializer(), response)
}