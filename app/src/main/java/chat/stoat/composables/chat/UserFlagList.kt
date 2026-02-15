package chat.stoat.composables.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import chat.stoat.R
import chat.stoat.core.model.schemas.UserFlags
import chat.stoat.core.model.schemas.hasFlag

/**
 * Displays user flags as a vertical list of labeled entries.
 * Only shown for moderation-relevant flags (Suspended, Deleted, Banned, Spam).
 */
@Composable
fun UserFlagList(flags: Long) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        UserFlags.entries
            .filter { flags hasFlag it }
            .forEach { flag ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.icn_gavel_24dp),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = flagLabel(flag),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
    }
}

/**
 * Displays user flags as a horizontal row of warning chips.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UserFlagRow(flags: Long) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        UserFlags.entries
            .filter { flags hasFlag it }
            .forEach { flag ->
                SuggestionChip(
                    onClick = {},
                    label = { Text(flagLabel(flag)) },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.icn_gavel_24dp),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        labelColor = MaterialTheme.colorScheme.onErrorContainer,
                        iconContentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                )
            }
    }
}

@Composable
private fun flagLabel(flag: UserFlags): String {
    return when (flag) {
        UserFlags.Suspended -> stringResource(R.string.user_flag_suspended)
        UserFlags.Deleted -> stringResource(R.string.user_flag_deleted)
        UserFlags.Banned -> stringResource(R.string.user_flag_banned)
        UserFlags.Spam -> stringResource(R.string.user_flag_spam)
    }
}
