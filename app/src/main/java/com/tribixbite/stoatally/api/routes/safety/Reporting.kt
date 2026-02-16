package com.tribixbite.stoatally.api.routes.safety

import com.tribixbite.stoatally.api.StoatAPIError
import com.tribixbite.stoatally.api.StoatHttp
import com.tribixbite.stoatally.api.StoatJson
import com.tribixbite.stoatally.core.model.schemas.ContentReportReason
import com.tribixbite.stoatally.core.model.schemas.FullMessageReport
import com.tribixbite.stoatally.core.model.schemas.FullServerReport
import com.tribixbite.stoatally.core.model.schemas.FullUserReport
import com.tribixbite.stoatally.core.model.schemas.MessageReport
import com.tribixbite.stoatally.core.model.schemas.ServerReport
import com.tribixbite.stoatally.core.model.schemas.UserReport
import com.tribixbite.stoatally.core.model.schemas.UserReportReason
import com.tribixbite.stoatally.api.api
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
