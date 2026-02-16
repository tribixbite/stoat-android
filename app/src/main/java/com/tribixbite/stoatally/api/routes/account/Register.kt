package com.tribixbite.stoatally.api.routes.account

import com.tribixbite.stoatally.api.StoatAPIError
import com.tribixbite.stoatally.api.StoatHttp
import com.tribixbite.stoatally.api.StoatJson
import com.tribixbite.stoatally.core.model.util.RsResult
import com.tribixbite.stoatally.api.api
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

@Serializable
data class RegistrationBody(
    val email: String,
    val password: String,
    val invite: String? = null,
    val captcha: String
)

suspend fun register(body: RegistrationBody): RsResult<Unit, StoatAPIError> {
    val response = StoatHttp.post("/auth/account/create".api()) {
        setBody(body)
        contentType(ContentType.Application.Json)
    }

    if (response.status.value !in 200..299) {
        val responseContent = response.bodyAsText()
        val error = try { StoatJson.decodeFromString(StoatAPIError.serializer(), responseContent) } catch (_: Exception) { null }
        return RsResult.err(error ?: StoatAPIError("HTTP ${response.status.value}"))
    }

    return RsResult.ok(Unit)
}
