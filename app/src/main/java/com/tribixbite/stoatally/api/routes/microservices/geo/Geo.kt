package com.tribixbite.stoatally.api.routes.microservices.geo

import com.tribixbite.stoatally.api.HitRateLimitException
import com.tribixbite.stoatally.api.StoatHttp
import com.tribixbite.stoatally.api.StoatJson
import com.tribixbite.stoatally.api.buildUserAgent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

@Serializable
data class GeoResponse(
    val countryCode: String,
    val isAgeRestrictedGeo: Boolean,
)

suspend fun queryGeo(): GeoResponse {
    try {
        val response = StoatHttp.get("https://geo.revolt.chat/?client=android") {
            header("User-Agent", buildUserAgent("Ktor queryGeo"))
        }

        if (response.status == HttpStatusCode.OK) {
            return StoatJson.decodeFromString(response.bodyAsText())
        } else throw Exception("Failed to query geo: ${response.status.value} ${response.status.description}")
    } catch (e: Exception) {
        throw Exception("Failed to query geo: ${e.message}", e).also {
            if (e is HitRateLimitException) {
                throw e
            }
        }
    }
}