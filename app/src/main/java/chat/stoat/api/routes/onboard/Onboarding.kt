package chat.stoat.api.routes.onboard

import chat.stoat.api.RateLimitResponse
import chat.stoat.api.StoatAPI
import chat.stoat.api.StoatAPIError
import chat.stoat.api.StoatHttp
import chat.stoat.api.StoatJson
import chat.stoat.api.api
import chat.stoat.core.model.util.RsResult
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

@Serializable
data class OnboardingResponse(
    val onboarding: Boolean
)

suspend fun needsOnboarding(sessionToken: String = StoatAPI.sessionToken): Boolean {
    val response = StoatHttp.get("/onboard/hello".api()) {
        header(StoatAPI.TOKEN_HEADER_NAME, sessionToken)
    }

    // Rate limit responses use 429 status code
    if (response.status.value == 429) {
        val responseContent = response.bodyAsText()
        val rateLimitResponse = try {
            StoatJson.decodeFromString(RateLimitResponse.serializer(), responseContent)
        } catch (_: Exception) { null }
        throw rateLimitResponse?.toException()
            ?: chat.stoat.api.HitRateLimitException()
    }

    val responseContent = response.bodyAsText()
    return StoatJson.decodeFromString(OnboardingResponse.serializer(), responseContent).onboarding
}

@Serializable
data class OnboardingCompletionBody(
    val username: String
)

suspend fun completeOnboarding(
    body: OnboardingCompletionBody,
    sessionToken: String = StoatAPI.sessionToken
): RsResult<Unit, StoatAPIError> {
    val response = StoatHttp.post("/onboard/complete".api()) {
        setBody(body)
        contentType(ContentType.Application.Json)
        header(StoatAPI.TOKEN_HEADER_NAME, sessionToken)
    }

    if (response.status == HttpStatusCode.Conflict) {
        return RsResult.err(StoatAPIError("UsernameTaken"))
    }

    if (response.status == HttpStatusCode.BadRequest) {
        return RsResult.err(StoatAPIError("InvalidUsername"))
    }

    if (response.status.value !in 200..299) {
        val responseContent = response.bodyAsText()
        val error = try { StoatJson.decodeFromString(StoatAPIError.serializer(), responseContent) } catch (_: Exception) { null }
        return RsResult.err(error ?: StoatAPIError("HTTP ${response.status.value}"))
    }

    return RsResult.ok(Unit)
}
