package com.tribixbite.stoatally.sheets

import androidx.compose.runtime.Composable
import com.tribixbite.stoatally.api.StoatAPI
import com.tribixbite.stoatally.composables.emoji.EmojiPicker

@Composable
fun ReactSheet(messageId: String, onSelect: (String?) -> Unit) {
    val message = StoatAPI.messageCache[messageId]

    if (message == null) {
        onSelect(null)
        return
    }

    EmojiPicker {
        onSelect(it.removeSurrounding(":"))
    }

}