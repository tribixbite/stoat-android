package com.tribixbite.stoatally.api.routes.microservices.health

import com.tribixbite.stoatally.api.StoatHttp
import com.tribixbite.stoatally.api.StoatJson
import com.tribixbite.stoatally.core.model.schemas.HealthNotice
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

suspend fun healthCheck(): HealthNotice {
    val res = StoatHttp.get("https://health.revolt.chat/api/health")
    val body = res.bodyAsText()

    if (res.status.value !in 200..299) {
        throw Exception("HTTP ${res.status.value}: $body")
    }

    return StoatJson.decodeFromString(HealthNotice.serializer(), body)
}