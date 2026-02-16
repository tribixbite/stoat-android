package com.tribixbite.stoatally.api.routes.auth

import com.tribixbite.stoatally.api.StoatHttp
import com.tribixbite.stoatally.api.StoatJson
import com.tribixbite.stoatally.api.api
import com.tribixbite.stoatally.core.model.schemas.Session
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.builtins.ListSerializer

suspend fun fetchAllSessions(): List<Session> {
    val response = StoatHttp.get("/auth/session/all".api())
        .bodyAsText()

    return StoatJson.decodeFromString(
        ListSerializer(Session.serializer()),
        response
    )
}

suspend fun logoutSessionById(id: String) {
    StoatHttp.delete("/auth/session/$id".api())
}

/** Logout the current session (revokes token server-side) */
suspend fun logoutCurrentSession() {
    StoatHttp.post("/auth/session/logout".api())
}

suspend fun logoutAllSessions(includingSelf: Boolean = false) {
    StoatHttp.delete("/auth/session/all".api()) {
        parameter("revoke_self", includingSelf)
    }
}
