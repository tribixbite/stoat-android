package chat.stoat.push

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
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import chat.stoat.BuildConfig
import chat.stoat.R
import chat.stoat.activities.MainActivity
import chat.stoat.api.STOAT_BASE
import chat.stoat.api.StoatAPI
import chat.stoat.api.StoatJson
import chat.stoat.api.internals.ULID
import chat.stoat.api.settings.NotificationSettingsProvider
import chat.stoat.c2dm.ChannelRegistrator.Companion.CHANNEL_ID_GROUP_CONVERSATIONS_MESSAGES
import chat.stoat.c2dm.NotificationID
import chat.stoat.core.model.schemas.Message
import chat.stoat.core.model.schemas.User
import chat.stoat.persistence.Database
import chat.stoat.persistence.KVStorage
import chat.stoat.persistence.SqlStorage
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * UnifiedPush receiver service.
 * Handles push messages from UP distributors (e.g., ntfy) and registers
 * endpoints with the stoatcord-bot relay.
 *
 * Message format from bot: same JSON payload as FCM data messages:
 * {"icon":"...","message":{"_id":"...","channel":"...","author":"...","content":"...","user":{...}}}
 */
class StoatPushService : PushService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Called when the UP distributor assigns a new endpoint URL.
     * Registers the endpoint with the stoatcord-bot relay server.
     */
    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        Log.d(TAG, "New UnifiedPush endpoint: ${endpoint.url}")

        serviceScope.launch {
            val kvStorage = KVStorage(applicationContext)
            kvStorage.set("upEndpoint", endpoint.url)
            kvStorage.remove("upRegistrationFailed")
            kvStorage.remove("upRegistrationError")

            val botUrl = kvStorage.get("pushBotUrl") ?: PushManager.DEFAULT_BOT_URL
            val userId = StoatAPI.selfId ?: run {
                Log.w(TAG, "Not logged in, cannot register UP endpoint")
                kvStorage.set("upRegistrationError", "Not logged in")
                return@launch
            }
            var deviceId = kvStorage.get("pushDeviceId")
            if (deviceId == null) {
                deviceId = UUID.randomUUID().toString()
                kvStorage.set("pushDeviceId", deviceId)
            }

            // Register endpoint with bot — pass WebPush keys if the distributor provides them
            val error = PushManager.registerUnifiedPush(
                botUrl = botUrl,
                userId = userId,
                deviceId = deviceId,
                endpoint = endpoint.url,
                p256dh = endpoint.pubKeySet?.pubKey ?: "",
                auth = endpoint.pubKeySet?.auth ?: "",
            )
            if (error != null) {
                Log.e(TAG, "Failed to register UP endpoint with bot: $error")
                kvStorage.set("upRegistrationFailed", true)
                kvStorage.set("upRegistrationError", error)
            } else {
                Log.d(TAG, "UP endpoint registered with bot successfully")
                kvStorage.set("upRegistrationFailed", false)
            }
        }
    }

    /**
     * Called when a push message arrives via the UP distributor.
     * Parses the JSON payload and displays a rich notification.
     */
    override fun onMessage(message: PushMessage, instance: String) {
        Log.d(TAG, "UnifiedPush message received (${message.content.size} bytes, decrypted=${message.decrypted})")

        val payloadString = String(message.content, Charsets.UTF_8)
        Log.d(TAG, "Payload (first 500 chars): ${payloadString.take(500)}")

        // Run on IO thread — Glide's blocking .submit().get() requires a background thread
        serviceScope.launch {
            showNotificationFromPayload(payloadString)
        }
    }

    /**
     * Called when the UP distributor unregisters this instance.
     * Clears local state and unregisters from the bot relay.
     */
    override fun onUnregistered(instance: String) {
        Log.d(TAG, "UnifiedPush unregistered for instance: $instance")
        serviceScope.launch {
            val kvStorage = KVStorage(applicationContext)
            kvStorage.remove("upEndpoint")

            // Unregister from bot
            val botUrl = kvStorage.get("pushBotUrl") ?: PushManager.DEFAULT_BOT_URL
            val deviceId = kvStorage.get("pushDeviceId") ?: return@launch
            PushManager.unregister(botUrl, deviceId)
        }
    }

    /**
     * Called when UP registration fails (no distributor available, network error, etc.).
     */
    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        Log.e(TAG, "UnifiedPush registration failed: $reason (instance=$instance)")
        serviceScope.launch {
            val kvStorage = KVStorage(applicationContext)
            kvStorage.set("upRegistrationFailed", true)
            kvStorage.set("upRegistrationError", reason.name)
        }
    }

    /**
     * Parse the notification payload JSON and display a rich notification.
     * Uses the same payload format as HandlerService's FCM data path,
     * so notification quality (conversation style, avatars, bubbles) is identical.
     */
    private fun showNotificationFromPayload(payloadString: String) {
        val payload = try {
            StoatJson.parseToJsonElement(payloadString).jsonObject
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse payload JSON: ${e.message}")
            return
        }

        var authorIcon = payload["icon"]?.jsonPrimitive?.contentOrNull
        val message = payload["message"]?.jsonObject?.let {
            try {
                StoatJson.decodeFromJsonElement(Message.serializer(), it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode message: ${e.message}")
                null
            }
        } ?: run {
            Log.e(TAG, "No valid 'message' in payload")
            return
        }

        val user = payload["message"]?.jsonObject?.get("user")?.jsonObject?.let {
            try {
                StoatJson.decodeFromJsonElement(User.serializer(), it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode user: ${e.message}")
                null
            }
        } ?: run {
            Log.e(TAG, "No valid 'message.user' in payload")
            return
        }

        val messageChannelId = message.channel ?: run {
            Log.e(TAG, "No channel in message")
            return
        }

        // Check if channel/server is muted
        val db = Database(SqlStorage.driver)
        val channelRecord = db.channelQueries.findById(messageChannelId).executeAsOneOrNull()
        val serverId = channelRecord?.server
        if (NotificationSettingsProvider.isChannelMuted(messageChannelId, serverId)) {
            Log.d(TAG, "Channel $messageChannelId is muted, suppressing notification")
            return
        }

        if (authorIcon == null) {
            authorIcon =
                "$STOAT_BASE/users/${message.author?.ifBlank { "0".repeat(26) }}/default_avatar"
        }

        val channelName = channelRecord?.let {
            when (it.channelType) {
                "DirectMessage" -> user.displayName ?: user.username
                "TextChannel" -> "#${it.name}"
                else -> it.name ?: getString(R.string.unknown)
            }
        } ?: getString(R.string.unknown)

        val messageTimestamp = try {
            message.id?.let { ULID.asTimestamp(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Invalid message ID for ULID timestamp: ${message.id}")
            null
        } ?: run {
            Log.e(TAG, "No valid message id")
            return
        }

        // Load avatar bitmap with timeout and fallback
        val bitmap: Bitmap? = try {
            Glide.with(this)
                .asBitmap()
                .load(authorIcon)
                .circleCrop()
                .submit()
                .get(10, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load avatar: ${e.message}")
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
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        // Android 11+ conversation bubbles
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

        if (ActivityCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "POST_NOTIFICATIONS permission not granted")
            return
        }

        Log.d(TAG, "=== DISPLAYING UP NOTIFICATION for channel $messageChannelId ===")
        NotificationManagerCompat.from(this)
            .notify(messageChannelId, NotificationID.NEW_MESSAGE, builder.build())
    }

    companion object {
        private const val TAG = "StoatPushService"
    }
}
