package com.tribixbite.stoatally.api.routes.microservices.autumn

import com.tribixbite.stoatally.api.HitRateLimitException
import com.tribixbite.stoatally.api.STOAT_FILES
import com.tribixbite.stoatally.api.StoatAPI
import com.tribixbite.stoatally.api.StoatHttp
import com.tribixbite.stoatally.api.StoatJson
import com.tribixbite.stoatally.core.model.schemas.AutumnError
import com.tribixbite.stoatally.core.model.schemas.AutumnId
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import java.io.File

const val MAX_ATTACHMENTS_PER_MESSAGE = 5

data class FileArgs(
    val file: File,
    val filename: String,
    val contentType: String,
    val spoiler: Boolean = false,
    val pickerIdentifier: String? = null,
)

suspend fun uploadToAutumn(
    file: File,
    name: String,
    tag: String,
    contentType: ContentType,
    onProgress: (Long, Long) -> Unit = { _, _ -> }
): String {
    val uploadUrl = "$STOAT_FILES/$tag"

    val response = StoatHttp.post(uploadUrl) {
        setBody(
            MultiPartFormDataContent(
                formData {
                    append(
                        "file",
                        file.readBytes(),
                        Headers.build {
                            append(HttpHeaders.ContentType, contentType.toString())
                            append(HttpHeaders.ContentDisposition, "filename=\"$name\"")
                        }
                    )
                }
            )
        )
        header(StoatAPI.TOKEN_HEADER_NAME, StoatAPI.sessionToken)
        onUpload { bytesSentTotal, contentLength ->
            contentLength?.let { onProgress(bytesSentTotal, it) }
        }
    }

    val body = response.bodyAsText()

    if (response.status == HttpStatusCode.TooManyRequests) {
        throw HitRateLimitException()
    }
    if (response.status == HttpStatusCode.PayloadTooLarge) {
        throw Exception("File too large")
    }
    if (response.status.value !in 200..299) {
        val error = try { StoatJson.decodeFromString(AutumnError.serializer(), body) } catch (_: Exception) { null }
        throw Exception(error?.type ?: "HTTP ${response.status.value}")
    }

    return StoatJson.decodeFromString(AutumnId.serializer(), body).id
}
