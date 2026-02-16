package com.tribixbite.stoatally.api.internals

import com.tribixbite.stoatally.api.STOAT_FILES
import com.tribixbite.stoatally.api.api
import com.tribixbite.stoatally.core.model.schemas.User

object ResourceLocations {
    fun userAvatarUrl(user: User?): String {
        if (user?.avatar != null) {
            return "$STOAT_FILES/avatars/${user.avatar!!.id}"
        }
        return "/users/${(user?.id ?: "").ifBlank { "0".repeat(26) }}/default_avatar".api()
    }
}