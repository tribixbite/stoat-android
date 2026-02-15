package chat.stoat.api.routes.account

import android.os.Build
import android.util.Log
import chat.stoat.api.StoatAPIError
import chat.stoat.api.StoatHttp
import chat.stoat.api.StoatJson
import chat.stoat.api.api
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginNegotiation(
    val email: String,
    val password: String,

    @SerialName("friendly_name")
    val friendlyName: String,
    val captcha: String? = null
)

@Serializable
data class LoginMfaAmendmentTotpCode(
    @SerialName("mfa_ticket")
    val mfaTicket: String,

    @SerialName("mfa_response")
    val mfaResponse: MfaResponseTotpCode,

    @SerialName("friendly_name")
    val friendlyName: String
)

@Serializable
data class LoginMfaAmendmentRecoveryCode(
    @SerialName("mfa_ticket")
    val mfaTicket: String,

    @SerialName("mfa_response")
    val mfaResponse: MfaResponseRecoveryCode,

    @SerialName("friendly_name")
    val friendlyName: String
)

@Serializable
data class MfaResponseRecoveryCode(
    @SerialName("recovery_code")
    val recoveryCode: String
)

@Serializable
data class MfaResponseTotpCode(
    @SerialName("totp_code")
    val totpCode: String
)

@Serializable
data class MfaLoginSpec(
    val result: String,
    val ticket: String,

    @SerialName("allowed_methods")
    val allowedMethods: List<String>
)

@Serializable
data class MfaCheck(
    val result: String
)

@Serializable
data class WebPushData(
    val endpoint: String,

    @SerialName("p256dh")
    val p256diffieHellman: String,
    val auth: String
)

@Serializable
data class UserHints(
    val result: String,

    @SerialName("_id")
    val id: String,

    @SerialName("user_id")
    val userId: String,
    val token: String,
    val name: String,
    val subscription: WebPushData? = null
)

data class EmailPasswordAssessment(
    val proceedMfa: Boolean = false,
    val mfaSpec: MfaLoginSpec? = null,
    val firstUserHints: UserHints? = null,
    val error: StoatAPIError? = null
)

suspend fun negotiateAuthentication(email: String, password: String): EmailPasswordAssessment {
    val sessionName = friendlySessionName()

    val response: HttpResponse = StoatHttp.post("/auth/session/login".api()) {
        contentType(ContentType.Application.Json)
        setBody(LoginNegotiation(email, password, sessionName, null))
    }

    val responseContent = response.bodyAsText()
    Log.d("Stoat", "negotiateAuthentication: $responseContent")

    // Check for error responses via HTTP status code first
    if (response.status == HttpStatusCode.InternalServerError) {
        return EmailPasswordAssessment(
            error = StoatAPIError("InternalServerError")
        )
    }

    if (response.status.value !in 200..299) {
        val error = try { StoatJson.decodeFromString(StoatAPIError.serializer(), responseContent) } catch (_: Exception) { null }
        return EmailPasswordAssessment(error = error ?: StoatAPIError("HTTP ${response.status.value}"))
    }

    val responseJson = StoatJson.decodeFromString(MfaCheck.serializer(), responseContent)

    return when (responseJson.result) {
        "Success" -> EmailPasswordAssessment(
            firstUserHints = StoatJson.decodeFromString(UserHints.serializer(), responseContent)
        )

        "MFA" -> EmailPasswordAssessment(
            proceedMfa = true,
            mfaSpec = StoatJson.decodeFromString(MfaLoginSpec.serializer(), responseContent)
        )

        else -> throw Exception("Unknown result: ${responseJson.result}")
    }
}

suspend fun authenticateWithMfaTotpCode(
    mfaTicket: String,
    mfaResponse: MfaResponseTotpCode
): EmailPasswordAssessment {
    val response: HttpResponse = StoatHttp.post("/auth/session/login".api()) {
        contentType(ContentType.Application.Json)
        setBody(LoginMfaAmendmentTotpCode(mfaTicket, mfaResponse, friendlySessionName()))
    }

    val responseContent = response.bodyAsText()
    Log.d("Stoat", "authenticateWithMfaTotpCode: $responseContent")

    if (response.status.value !in 200..299) {
        val error = try { StoatJson.decodeFromString(StoatAPIError.serializer(), responseContent) } catch (_: Exception) { null }
        return EmailPasswordAssessment(error = error ?: StoatAPIError("HTTP ${response.status.value}"))
    }

    return EmailPasswordAssessment(
        firstUserHints = StoatJson.decodeFromString(UserHints.serializer(), responseContent)
    )
}

suspend fun authenticateWithMfaRecoveryCode(
    mfaTicket: String,
    mfaResponse: MfaResponseRecoveryCode
): EmailPasswordAssessment {
    val response: HttpResponse = StoatHttp.post("/auth/session/login".api()) {
        contentType(ContentType.Application.Json)
        setBody(LoginMfaAmendmentRecoveryCode(mfaTicket, mfaResponse, friendlySessionName()))
    }

    val responseContent = response.bodyAsText()
    Log.d("Stoat", "authenticateWithMfaRecoveryCode: $responseContent")

    if (response.status.value !in 200..299) {
        val error = try { StoatJson.decodeFromString(StoatAPIError.serializer(), responseContent) } catch (_: Exception) { null }
        return EmailPasswordAssessment(error = error ?: StoatAPIError("HTTP ${response.status.value}"))
    }

    return EmailPasswordAssessment(
        firstUserHints = StoatJson.decodeFromString(UserHints.serializer(), responseContent)
    )
}

fun friendlySessionName(): String {
    return "Stoat for Android on ${Build.MANUFACTURER} ${Build.MODEL}"
}
