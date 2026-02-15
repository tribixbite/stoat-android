package chat.stoat.c2dm

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import chat.stoat.BuildConfig
import chat.stoat.R
import chat.stoat.activities.MainActivity
import chat.stoat.api.STOAT_BASE
import chat.stoat.api.StoatJson
import chat.stoat.api.internals.ULID
import chat.stoat.api.routes.push.subscribePush
import chat.stoat.api.settings.NotificationSettingsProvider
import chat.stoat.core.model.schemas.Message
import chat.stoat.core.model.schemas.User
import chat.stoat.c2dm.ChannelRegistrator.Companion.CHANNEL_ID_GROUP_CONVERSATIONS_MESSAGES
import chat.stoat.persistence.Database
import chat.stoat.persistence.SqlStorage
import com.bumptech.glide.Glide
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.TimeUnit

object NotificationID {
    const val NEW_MESSAGE = 0
}

class HandlerService : FirebaseMessagingService() {
    // Service-scoped coroutine context to avoid runBlocking ANR on main thread
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("HandlerService", "FCM token refreshed, subscribing to push")
        serviceScope.launch {
            try {
                val error = subscribePush(auth = token)
                if (error != null) {
                    Log.w("HandlerService", "Push subscription failed during onNewToken: $error")
                } else {
                    Log.d("HandlerService", "Push subscription registered via onNewToken")
                }
            } catch (e: Exception) {
                Log.e("HandlerService", "Error in onNewToken push subscription", e)
            }
        }
    }

    override fun onMessageReceived(fcmMessage: RemoteMessage) {
        /// TEMPORARY CODE, SCHEMA TO BE REPLACED
        Log.d("HandlerService", "=== FCM MESSAGE RECEIVED ===")
        Log.d("HandlerService", "From: ${fcmMessage.from}")
        Log.d("HandlerService", "Data keys: ${fcmMessage.data.keys}")
        Log.d("HandlerService", "Notification body: ${fcmMessage.notification?.body}")
        Log.d("HandlerService", "Message ID: ${fcmMessage.messageId}")
        Log.d("HandlerService", "Priority: ${fcmMessage.priority}")

        val payloadString = fcmMessage.data["payload"]
        if (payloadString == null) {
            Log.e("HandlerService", "No payload in message (missing 'payload' key), abort. All data: ${fcmMessage.data}")
            return
        }

        Log.d("HandlerService", "Payload (first 500 chars): ${payloadString.take(500)}")

        val payload = try {
            StoatJson.parseToJsonElement(payloadString).jsonObject
        } catch (e: Exception) {
            Log.e("HandlerService", "Failed to parse payload JSON: ${e.message}")
            return
        }
        val keys = payload.keys.toList().toString()
        Log.d("HandlerService", "following keys: $keys")

        var authorIcon = payload["icon"]?.jsonPrimitive?.contentOrNull
        val message = payload["message"]?.jsonObject?.let {
            try {
                StoatJson.decodeFromJsonElement(Message.serializer(), it)
            } catch (e: Exception) {
                Log.e("HandlerService", "Failed to decode 'message' field: ${e.message}")
                null
            }
        } ?: run {
            Log.e("HandlerService", "No valid 'message' in payload, abort")
            return
        }

        val user = payload["message"]?.jsonObject?.get("user")?.jsonObject?.let {
            try {
                StoatJson.decodeFromJsonElement(User.serializer(), it)
            } catch (e: Exception) {
                Log.e("HandlerService", "Failed to decode 'message.user' field: ${e.message}")
                null
            }
        } ?: run {
            Log.e("HandlerService", "No valid 'message.user' in payload, abort")
            return
        }

        val messageChannelId = message.channel
        if (messageChannelId == null) {
            Log.e("HandlerService", "No channel in message, abort")
            return
        }

        // Check if channel/server is muted before proceeding
        val db = Database(SqlStorage.driver)
        val channelRecord = db.channelQueries.findById(messageChannelId).executeAsOneOrNull()
        val serverId = channelRecord?.server
        if (NotificationSettingsProvider.isChannelMuted(messageChannelId, serverId)) {
            Log.d("HandlerService", "Channel $messageChannelId is muted, suppressing notification")
            return
        }

        Log.d("HandlerService", "Message from ${message.author} in channel $messageChannelId (server=$serverId)")
        Log.d("HandlerService", "Content: ${message.content?.take(100)}")
        Log.d("HandlerService", "Mentions: ${message.mentions}")

        if (authorIcon == null) {
            authorIcon =
                "$STOAT_BASE/users/${message.author?.ifBlank { "0".repeat(26) }}/default_avatar"
        }

        val channelName = channelRecord?.let {
            when (it.channelType) {
                "DirectMessage" -> {
                    user.displayName ?: user.username
                }

                "TextChannel" -> {
                    "#${it.name}"
                }

                else -> {
                    it.name ?: getString(R.string.unknown)
                }
            }
        } ?: getString(
            R.string.unknown
        )

        val messageTimestamp = message.id?.let { ULID.asTimestamp(it) } ?: run {
            Log.e("HandlerService", "No message id in message, abort")
            return
        }

        // Load avatar bitmap with timeout and fallback to default icon
        val bitmap: Bitmap? = try {
            Glide.with(this)
                .asBitmap()
                .load(authorIcon)
                .circleCrop()
                .submit()
                .get(10, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.w("HandlerService", "Failed to load author avatar, using null: ${e.message}")
            null
        }

        val authorBuilder = Person.Builder()
            .setBot(user.bot != null)
            .setKey(message.author)
            .setName(user.displayName ?: user.username)
        if (bitmap != null) {
            authorBuilder.setIcon(IconCompat.createWithBitmap(bitmap))
        }
        val author = authorBuilder.build()

        val shortcutId = "${BuildConfig.APPLICATION_ID}.channel.${messageChannelId}"

        val conversationIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra("channelId", messageChannelId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val shortcutIcon = if (bitmap != null) {
            IconCompat.createWithBitmap(bitmap)
        } else {
            IconCompat.createWithResource(this, R.drawable.ic_notification_monochrome)
        }

        val shortcut = ShortcutInfoCompat.Builder(this, shortcutId)
            .setShortLabel(channelName)
            .setLongLabel(channelName)
            .setIcon(shortcutIcon)
            .setIntent(conversationIntent)
            .setLongLived(true)
            .setPerson(author)
            .build()

        ShortcutManagerCompat.pushDynamicShortcut(this, shortcut)

        val remoteInput = RemoteInput.Builder("content").run {
            setLabel(getString(R.string.message_context_sheet_actions_reply))
            build()
        }

        val action: NotificationCompat.Action =
            NotificationCompat.Action.Builder(
                R.drawable.icn_reply_24dp,
                getString(R.string.message_context_sheet_actions_reply),
                PendingIntent.getActivity(
                    this,
                    0,
                    conversationIntent,
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
                .addRemoteInput(remoteInput)
                .build()

        val contentIntent = PendingIntent.getActivity(
            this,
            messageChannelId.hashCode(),
            conversationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID_GROUP_CONVERSATIONS_MESSAGES)
            .setSmallIcon(R.drawable.icn_chat_24dp)
            .setContentTitle(user.displayName ?: user.username)
            .setContentText(message.content)
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setStyle(
                NotificationCompat.MessagingStyle(author)
                    .setConversationTitle(channelName)
                    .addMessage(
                        message.content ?: getString(R.string.reply_message_empty_has_attachments),
                        messageTimestamp,
                        author
                    )
            )
            .addAction(action)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        // Android 11 bubbles
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setShortcutId(shortcutId)
            builder.setLocusId(LocusIdCompat(shortcutId))

            val bubbleIntent = PendingIntent.getActivity(
                this,
                messageChannelId.hashCode(),
                conversationIntent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val bubbleMetadata = NotificationCompat.BubbleMetadata.Builder(
                bubbleIntent,
                shortcutIcon
            )
                .setDesiredHeight(600)
                .setAutoExpandBubble(false)
                .setSuppressNotification(false)
                .build()

            builder.setBubbleMetadata(bubbleMetadata)
        }

        NotificationManagerCompat.from(this).apply {
            if (ActivityCompat.checkSelfPermission(
                    this@HandlerService,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w("HandlerService", "POST_NOTIFICATIONS permission not granted, cannot show notification")
                return
            }
            Log.d("HandlerService", "=== DISPLAYING NOTIFICATION for channel $messageChannelId ===")
            notify(messageChannelId, NotificationID.NEW_MESSAGE, builder.build())
        }
        /// END TEMPORARY CODE
    }
}