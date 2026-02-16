package com.tribixbite.stoatally.sheets

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tribixbite.stoatally.R

/**
 * Bottom sheet shown when tapping on a webhook user in chat.
 * Displays webhook illustration and informational text about what webhooks are.
 */
@Composable
fun WebHookUserSheet(
    modifier: Modifier = Modifier,
    webhookName: String? = null,
    webhookId: String? = null
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.padding(16.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.ux_webhooks),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        )

        Text(
            text = stringResource(R.string.user_info_sheet_webhook),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = stringResource(R.string.user_info_sheet_webhook_body),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )

        // Show webhook details if available
        if (webhookName != null || webhookId != null) {
            Spacer(Modifier.height(4.dp))

            webhookName?.let { name ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painter = painterResource(R.drawable.icn_link_24dp),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            webhookId?.let { id ->
                Text(
                    text = id,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 26.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WebHookUserSheetPreview() {
    Column {
        WebHookUserSheet(
            webhookName = "GitHub Notifications",
            webhookId = "01ABC123DEF456"
        )
    }
}
