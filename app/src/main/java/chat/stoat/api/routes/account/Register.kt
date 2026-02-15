package chat.stoat.api.routes.account

import chat.stoat.api.StoatAPIError
import chat.stoat.api.StoatHttp
import chat.stoat.api.StoatJson
import chat.stoat.core.model.util.RsResult
import chat.stoat.api.api
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
