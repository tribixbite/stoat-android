package chat.stoat.api.routes.invites

import chat.stoat.api.StoatAPIError
import chat.stoat.api.StoatHttp
import chat.stoat.api.StoatJson
import chat.stoat.api.api
import chat.stoat.core.model.schemas.Invite
import chat.stoat.core.model.schemas.InviteJoined
import chat.stoat.core.model.util.RsResult
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText

suspend fun fetchInviteByCode(code: String): RsResult<Invite, StoatAPIError> {
    val res = StoatHttp.get("/invites/$code".api())
    val body = res.bodyAsText()

    if (res.status.value !in 200..299) {
        val error = try { StoatJson.decodeFromString(StoatAPIError.serializer(), body) } catch (_: Exception) { null }
        return RsResult.err(error ?: StoatAPIError("HTTP ${res.status.value}"))
    }

    val invite = StoatJson.decodeFromString(Invite.serializer(), body)
    return RsResult.ok(invite)
}

suspend fun joinInviteByCode(code: String): RsResult<InviteJoined, StoatAPIError> {
    val res = StoatHttp.post("/invites/$code".api())
    val body = res.bodyAsText()

    if (res.status.value !in 200..299) {
        val error = try { StoatJson.decodeFromString(StoatAPIError.serializer(), body) } catch (_: Exception) { null }
        return RsResult.err(error ?: StoatAPIError("HTTP ${res.status.value}"))
    }

    val invite = StoatJson.decodeFromString(InviteJoined.serializer(), body)
    return RsResult.ok(invite)
}
