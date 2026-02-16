package com.tribixbite.stoatally.api.routes.microservices.january

import com.tribixbite.stoatally.api.STOAT_PROXY
import java.net.URLEncoder

fun asJanuaryProxyUrl(url: String): String {
    return "$STOAT_PROXY/proxy?url=${URLEncoder.encode(url, "utf-8")}"
}
