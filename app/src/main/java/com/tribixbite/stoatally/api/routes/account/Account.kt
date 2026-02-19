package com.tribixbite.stoatally.api.routes.account

import com.tribixbite.stoatally.api.StoatHttp
import com.tribixbite.stoatally.api.StoatJson
import com.tribixbite.stoatally.api.api
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

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
    val res = StoatHttp.get("/auth/account/".api())
    val body = res.bodyAsText()

    if (res.status.value !in 200..299) {
        throw Exception("HTTP ${res.status.value}: $body")
    }

    return StoatJson.decodeFromString(AccountInfo.serializer(), body)
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

// --- Password Reset ---

/** Request a password reset email. No auth required. */
suspend fun sendPasswordReset(email: String, captcha: String? = null): String? {
    @Serializable
    data class Body(val email: String, val captcha: String? = null)

    val response = StoatHttp.post("/auth/account/reset_password".api()) {
        contentType(ContentType.Application.Json)
        setBody(StoatJson.encodeToString(Body.serializer(), Body(email, captcha)))
    }
    return if (response.status.isSuccess()) null
    else "HTTP ${response.status.value}: ${response.bodyAsText()}"
}

/** Confirm a password reset with the token from the email. No auth required. */
suspend fun confirmPasswordReset(token: String, password: String, removeSessions: Boolean = false): String? {
    @Serializable
    data class Body(val token: String, val password: String, val remove_sessions: Boolean = false)

    val response = StoatHttp.patch("/auth/account/reset_password".api()) {
        contentType(ContentType.Application.Json)
        setBody(StoatJson.encodeToString(Body.serializer(), Body(token, password, removeSessions)))
    }
    return if (response.status.isSuccess()) null
    else "HTTP ${response.status.value}: ${response.bodyAsText()}"
}

// --- Email Verification ---

/** Confirm email verification with the code from the email. No auth required. */
suspend fun confirmEmailVerification(code: String): String? {
    val response = StoatHttp.post("/auth/account/verify/$code".api())
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
    val res = StoatHttp.get("/auth/session/all".api())
    val body = res.bodyAsText()

    if (res.status.value !in 200..299) {
        throw Exception("HTTP ${res.status.value}: $body")
    }

    return StoatJson.decodeFromString(ListSerializer(SessionInfo.serializer()), body)
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

// --- MFA/TOTP Management ---

/** Response from POST /auth/mfa/totp — contains the TOTP secret for setup. */
@Serializable
data class TotpSecret(
    val secret: String
)

/** Response from POST/PATCH /auth/mfa/recovery — list of recovery codes. */
@Serializable
data class RecoveryCodes(
    val codes: List<String>? = null
)

/** Create an MFA ticket for re-authentication. Required for some MFA operations. */
suspend fun createMfaTicket(password: String): String? {
    @Serializable
    data class Body(val password: String)

    @Serializable
    data class TicketResponse(
        @SerialName("_id") val id: String,
        val token: String
    )

    val response = StoatHttp.put("/auth/mfa/ticket".api()) {
        contentType(ContentType.Application.Json)
        setBody(StoatJson.encodeToString(Body.serializer(), Body(password)))
    }
    return if (response.status.isSuccess()) {
        val ticket = StoatJson.decodeFromString(TicketResponse.serializer(), response.bodyAsText())
        ticket.token
    } else null
}

/** Generate a new TOTP secret for setup. Returns the base32 secret string.
 *  Requires x-mfa-ticket header. */
suspend fun generateTotpSecret(mfaTicket: String): TotpSecret? {
    val response = StoatHttp.post("/auth/mfa/totp".api()) {
        headers.append("x-mfa-ticket", mfaTicket)
    }
    return if (response.status.isSuccess()) {
        StoatJson.decodeFromString(TotpSecret.serializer(), response.bodyAsText())
    } else null
}

/** Enable TOTP by confirming with a valid code from the authenticator app. */
suspend fun enableTotp(totpCode: String): String? {
    @Serializable
    data class Body(val totp_code: String)

    val response = StoatHttp.put("/auth/mfa/totp".api()) {
        contentType(ContentType.Application.Json)
        setBody(StoatJson.encodeToString(Body.serializer(), Body(totpCode)))
    }
    return if (response.status.isSuccess()) null
    else "HTTP ${response.status.value}: ${response.bodyAsText()}"
}

/** Disable TOTP. Requires x-mfa-ticket header. */
suspend fun disableTotp(mfaTicket: String): String? {
    val response = StoatHttp.delete("/auth/mfa/totp".api()) {
        headers.append("x-mfa-ticket", mfaTicket)
    }
    return if (response.status.isSuccess()) null
    else "HTTP ${response.status.value}: ${response.bodyAsText()}"
}

/** Fetch existing recovery codes. Requires x-mfa-ticket header. */
suspend fun fetchRecoveryCodes(mfaTicket: String): List<String> {
    val response = StoatHttp.post("/auth/mfa/recovery".api()) {
        headers.append("x-mfa-ticket", mfaTicket)
    }
    return if (response.status.isSuccess()) {
        StoatJson.decodeFromString(ListSerializer(String.serializer()), response.bodyAsText())
    } else emptyList()
}

/** Generate new recovery codes. Requires x-mfa-ticket header. */
suspend fun generateRecoveryCodes(mfaTicket: String): List<String> {
    val response = StoatHttp.patch("/auth/mfa/recovery".api()) {
        headers.append("x-mfa-ticket", mfaTicket)
    }
    return if (response.status.isSuccess()) {
        StoatJson.decodeFromString(ListSerializer(String.serializer()), response.bodyAsText())
    } else emptyList()
}
