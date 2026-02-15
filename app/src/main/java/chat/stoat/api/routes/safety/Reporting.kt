package chat.stoat.api.routes.safety

import chat.stoat.api.StoatAPIError
import chat.stoat.api.StoatHttp
import chat.stoat.api.StoatJson
import chat.stoat.core.model.schemas.ContentReportReason
import chat.stoat.core.model.schemas.FullMessageReport
import chat.stoat.core.model.schemas.FullServerReport
import chat.stoat.core.model.schemas.FullUserReport
import chat.stoat.core.model.schemas.MessageReport
import chat.stoat.core.model.schemas.ServerReport
import chat.stoat.core.model.schemas.UserReport
import chat.stoat.core.model.schemas.UserReportReason
import chat.stoat.api.api
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText

suspend fun putMessageReport(
    messageId: String,
    reason: ContentReportReason,
    additionalContext: String? = null
) {
    val fullMessageReport = FullMessageReport(
        content = MessageReport(
            type = "Message",
            report_reason = reason,
            id = messageId
        ),
        additional_context = additionalContext
    )

    val res = StoatHttp.post("/safety/report".api()) {
        setBody(
            StoatJson.encodeToString(
                FullMessageReport.serializer(),
                fullMessageReport
            )
        )
    }

    if (res.status.value !in 200..299) {
        val body = res.bodyAsText()
        val error = try { StoatJson.decodeFromString(StoatAPIError.serializer(), body) } catch (_: Exception) { null }
        throw Error(error?.type ?: "HTTP ${res.status.value}")
    }
}

suspend fun putServerReport(
    serverId: String,
    reason: ContentReportReason,
    additionalContext: String? = null
) {
    val fullServerReport = FullServerReport(
        content = ServerReport(
            type = "Server",
            report_reason = reason,
            id = serverId
        ),
        additional_context = additionalContext
    )

    val res = StoatHttp.post("/safety/report".api()) {
        setBody(
            StoatJson.encodeToString(
                FullServerReport.serializer(),
                fullServerReport
            )
        )
    }

    if (res.status.value !in 200..299) {
        val body = res.bodyAsText()
        val error = try { StoatJson.decodeFromString(StoatAPIError.serializer(), body) } catch (_: Exception) { null }
        throw Error(error?.type ?: "HTTP ${res.status.value}")
    }
}

suspend fun putUserReport(
    userId: String,
    reason: UserReportReason,
    additionalContext: String? = null
) {
    val fullUserReport = FullUserReport(
        content = UserReport(
            type = "User",
            report_reason = reason,
            id = userId
        ),
        additional_context = additionalContext
    )

    val res = StoatHttp.post("/safety/report".api()) {
        setBody(
            StoatJson.encodeToString(
                FullUserReport.serializer(),
                fullUserReport
            )
        )
    }

    if (res.status.value !in 200..299) {
        val body = res.bodyAsText()
        val error = try { StoatJson.decodeFromString(StoatAPIError.serializer(), body) } catch (_: Exception) { null }
        throw Error(error?.type ?: "HTTP ${res.status.value}")
    }
}
