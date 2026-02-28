package com.tribixbite.stoatally.c2dm

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.ContextCompat.getSystemService
import com.tribixbite.stoatally.R

/**
 * Registers Android notification channels and groups.
 *
 * Channel hierarchy:
 *  - Conversations: Messages, Mentions
 *  - Social: Friend Requests
 *  - Voice: Voice Calls
 *
 * Per-conversation channels (individual DM/channel granularity) are not implemented —
 * all messages share a single "Messages" channel. Users control muting per-channel
 * within the app's notification settings instead.
 */
class ChannelRegistrator(val context: Context) {
    companion object {
        // Groups
        const val CHANNEL_ID_GROUP_CONVERSATIONS = "com.tribixbite.stoatally.c2dm.conversations"
        const val CHANNEL_ID_GROUP_SOCIAL = "com.tribixbite.stoatally.c2dm.social"
        const val CHANNEL_ID_GROUP_VOICE = "com.tribixbite.stoatally.c2dm.voice"

        // Channels
        const val CHANNEL_ID_GROUP_CONVERSATIONS_MESSAGES =
            "com.tribixbite.stoatally.c2dm.conversations.messages"
        const val CHANNEL_ID_GROUP_CONVERSATIONS_MENTIONS =
            "com.tribixbite.stoatally.c2dm.conversations.mentions"
        const val CHANNEL_ID_GROUP_SOCIAL_FRIENDREQUESTS =
            "com.tribixbite.stoatally.c2dm.social.friendrequests"
        const val CHANNEL_ID_GROUP_VOICE_CALLS =
            "com.tribixbite.stoatally.c2dm.voice.calls"
    }

    private val notificationManager =
        getSystemService(context, NotificationManager::class.java) as NotificationManager

    private fun registerGroups() {
        notificationManager.createNotificationChannelGroups(
            listOf(
                NotificationChannelGroup(
                    CHANNEL_ID_GROUP_CONVERSATIONS,
                    context.getString(R.string.notification_channel_group_conversations)
                ),
                NotificationChannelGroup(
                    CHANNEL_ID_GROUP_SOCIAL,
                    context.getString(R.string.notification_channel_group_social)
                ),
                NotificationChannelGroup(
                    CHANNEL_ID_GROUP_VOICE,
                    context.getString(R.string.notification_channel_group_voice)
                ),
            )
        )
    }

    private fun registerChannels() {
        notificationManager.createNotificationChannels(
            listOf(
                // Messages — DM and channel messages (high priority for heads-up display)
                NotificationChannel(
                    CHANNEL_ID_GROUP_CONVERSATIONS_MESSAGES,
                    context.getString(R.string.notification_channel_messages),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    group = CHANNEL_ID_GROUP_CONVERSATIONS
                    description = context.getString(R.string.notification_channel_messages_description)
                },
                // Mentions — @user, @everyone, @here (high priority)
                NotificationChannel(
                    CHANNEL_ID_GROUP_CONVERSATIONS_MENTIONS,
                    context.getString(R.string.notification_channel_mentions),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    group = CHANNEL_ID_GROUP_CONVERSATIONS
                    description = context.getString(R.string.notification_channel_mentions_description)
                },
                // Friend requests (default priority)
                NotificationChannel(
                    CHANNEL_ID_GROUP_SOCIAL_FRIENDREQUESTS,
                    context.getString(R.string.notification_channel_friend_requests),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    group = CHANNEL_ID_GROUP_SOCIAL
                    description = context.getString(R.string.notification_channel_friend_requests_description)
                },
                // Voice calls (high priority — needs to alert user immediately)
                NotificationChannel(
                    CHANNEL_ID_GROUP_VOICE_CALLS,
                    context.getString(R.string.notification_channel_voice_calls),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    group = CHANNEL_ID_GROUP_VOICE
                    description = context.getString(R.string.notification_channel_voice_calls_description)
                },
            )
        )
    }

    fun register() {
        registerGroups()
        registerChannels()
    }
}
