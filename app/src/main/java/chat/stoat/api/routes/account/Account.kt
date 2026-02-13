package chat.stoat.api.routes.account

import chat.stoat.api.StoatHttp
import chat.stoat.api.StoatJson
import chat.stoat.api.api
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
data class AccountInfo(
    @SerialName("_id")
    val id: String,
    val email: String,
    val mfa: MfaInfo? = null
)

@Serializable
data class MfaInfo(
    @SerialName("totp_mfa")
    val totpEnabled: Boolean? = null,
    @SerialName("security_key_mfa")
    val securityKeyEnabled: Boolean? = null,
    @SerialName("trusted_handover")
    val trustedHandover: Boolean? = null,
    @SerialName("recovery_active")
    val recoveryActive: Boolean? = null
)

/** Fetch current account info (email, MFA status). */
suspend fun fetchAccountInfo(): AccountInfo {
    val response = StoatHttp.get("/auth/account/".api()).bodyAsText()
    return StoatJson.decodeFromString(AccountInfo.serializer(), response)
}

/** Change account email. Requires current password. */
suspend fun changeEmail(email: String, currentPassword: String): String? {
    @Serializable
    data class Body(val email: String, val current_password: String)

    val response = StoatHttp.patch("/auth/account/change/email".api()) {
        contentType(ContentType.Application.Json)
        setBody(StoatJson.encodeToString(Body.serializer(), Body(email, currentPassword)))
    }
    return if (response.status.isSuccess()) null
    else "HTTP ${response.status.value}: ${response.bodyAsText()}"
}

/** Change account password. Requires current password. */
suspend fun changePassword(password: String, currentPassword: String): String? {
    @Serializable
    data class Body(val password: String, val current_password: String)

    val response = StoatHttp.patch("/auth/account/change/password".api()) {
        contentType(ContentType.Application.Json)
        setBody(StoatJson.encodeToString(Body.serializer(), Body(password, currentPassword)))
    }
    return if (response.status.isSuccess()) null
    else "HTTP ${response.status.value}: ${response.bodyAsText()}"
}

/** Disable account (reversible). Requires session token. */
suspend fun disableAccount(): String? {
    val response = StoatHttp.post("/auth/account/disable".api())
    return if (response.status.isSuccess()) null
    else "HTTP ${response.status.value}: ${response.bodyAsText()}"
}

/** Delete account (irreversible). Requires session token. */
suspend fun deleteAccount(): String? {
    val response = StoatHttp.post("/auth/account/delete".api())
    return if (response.status.isSuccess()) null
    else "HTTP ${response.status.value}: ${response.bodyAsText()}"
}

/** Request email verification re-send. */
suspend fun resendVerification(email: String, captcha: String? = null): String? {
    @Serializable
    data class Body(val email: String, val captcha: String? = null)

    val response = StoatHttp.post("/auth/account/reverify".api()) {
        contentType(ContentType.Application.Json)
        setBody(StoatJson.encodeToString(Body.serializer(), Body(email, captcha)))
    }
    return if (response.status.isSuccess()) null
    else "HTTP ${response.status.value}: ${response.bodyAsText()}"
}

// --- Session Management ---

@Serializable
data class SessionInfo(
    @SerialName("_id")
    val id: String,
    val name: String? = null
)

/** Fetch all active sessions. */
suspend fun fetchSessions(): List<SessionInfo> {
    val response = StoatHttp.get("/auth/session/all".api()).bodyAsText()
    return StoatJson.decodeFromString(ListSerializer(SessionInfo.serializer()), response)
}

/** Revoke (delete) a specific session. */
suspend fun revokeSession(sessionId: String): String? {
    val response = StoatHttp.delete("/auth/session/$sessionId".api())
    return if (response.status.isSuccess()) null
    else "HTTP ${response.status.value}: ${response.bodyAsText()}"
}

/** Revoke all other sessions (except current). */
suspend fun revokeAllSessions(): String? {
    val response = StoatHttp.delete("/auth/session/all".api())
    return if (response.status.isSuccess()) null
    else "HTTP ${response.status.value}: ${response.bodyAsText()}"
}

/** Rename a session. */
suspend fun renameSession(sessionId: String, name: String): String? {
    @Serializable
    data class Body(val friendly_name: String)

    val response = StoatHttp.patch("/auth/session/$sessionId".api()) {
        contentType(ContentType.Application.Json)
        setBody(StoatJson.encodeToString(Body.serializer(), Body(name)))
    }
    return if (response.status.isSuccess()) null
    else "HTTP ${response.status.value}: ${response.bodyAsText()}"
}
