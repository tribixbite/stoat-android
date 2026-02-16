package com.tribixbite.stoatally.composables.chat

import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import com.tribixbite.stoatally.api.STOAT_FILES
import com.tribixbite.stoatally.api.internals.BrushCompat
import com.tribixbite.stoatally.api.internals.Roles
import com.tribixbite.stoatally.api.internals.solidColor
import com.tribixbite.stoatally.core.model.schemas.Member
import com.tribixbite.stoatally.core.model.schemas.User
import com.tribixbite.stoatally.composables.generic.UserAvatar
import com.tribixbite.stoatally.composables.generic.presenceFromStatus
import com.tribixbite.stoatally.internals.extensions.TransparentListItemColours

@Composable
fun MemberListItem(
    member: Member?,
    user: User?,
    serverId: String?,
    userId: String,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    val highestColourRole = serverId?.let {
        user?.id?.let { userId ->
            Roles.resolveHighestRole(
                it,
                userId,
                true
            )
        }
    }

    val colour = highestColourRole?.colour?.let { BrushCompat.parseColour(it) }
        ?: Brush.solidColor(LocalContentColor.current)

    ListItem(
        colors = TransparentListItemColours,
        modifier = modifier,
        headlineContent = {
            Text(
                text = member?.nickname
                    ?: user?.displayName
                    ?: user?.username
                    ?: user?.id
                    ?: userId,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = LocalTextStyle.current.copy(brush = colour),
            )
        },
        supportingContent = {
            user?.status?.text?.let {
                if (user.online == true) {
                    Text(
                        text = it,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        leadingContent = {
            UserAvatar(
                username = member?.nickname
                    ?: user?.displayName
                    ?: user?.username
                    ?: user?.id
                    ?: userId,
                avatar = user?.avatar,
                rawUrl = member?.avatar?.let { "$STOAT_FILES/avatars/${it.id}" },
                userId = userId,
                presence = presenceFromStatus(
                    user?.status?.presence,
                    user?.online ?: false
                )
            )
        },
        trailingContent = trailingContent
    )
}