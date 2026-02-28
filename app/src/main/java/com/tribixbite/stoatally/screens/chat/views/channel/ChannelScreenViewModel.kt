package com.tribixbite.stoatally.screens.chat.views.channel

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.util.fastDistinctBy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tribixbite.stoatally.R
import com.tribixbite.stoatally.api.StoatAPI
import com.tribixbite.stoatally.api.StoatJson
import com.tribixbite.stoatally.api.internals.ChannelUtils
import com.tribixbite.stoatally.api.internals.PermissionBit
import com.tribixbite.stoatally.api.internals.Roles
import com.tribixbite.stoatally.api.internals.SpecialUsers
import com.tribixbite.stoatally.api.internals.ULID
import com.tribixbite.stoatally.api.internals.has
import com.tribixbite.stoatally.api.realtime.RealtimeSocket
import com.tribixbite.stoatally.api.realtime.RealtimeSocketFrames
import com.tribixbite.stoatally.api.realtime.frames.receivable.ChannelDeleteFrame
import com.tribixbite.stoatally.api.realtime.frames.receivable.ChannelStartTypingFrame
import com.tribixbite.stoatally.api.realtime.frames.receivable.ChannelStopTypingFrame
import com.tribixbite.stoatally.api.realtime.frames.receivable.MessageAppendFrame
import com.tribixbite.stoatally.api.realtime.frames.receivable.MessageDeleteFrame
import com.tribixbite.stoatally.api.realtime.frames.receivable.MessageFrame
import com.tribixbite.stoatally.api.realtime.frames.receivable.MessageReactFrame
import com.tribixbite.stoatally.api.realtime.frames.receivable.MessageUnreactFrame
import com.tribixbite.stoatally.api.realtime.frames.receivable.MessageUpdateFrame
import com.tribixbite.stoatally.api.routes.channel.SendMessageReply
import com.tribixbite.stoatally.api.routes.channel.ackChannel
import com.tribixbite.stoatally.api.routes.channel.editMessage
import com.tribixbite.stoatally.api.routes.channel.fetchMessagesFromChannel
import com.tribixbite.stoatally.api.routes.channel.sendMessage
import com.tribixbite.stoatally.api.routes.microservices.autumn.FileArgs
import com.tribixbite.stoatally.api.routes.microservices.autumn.MAX_ATTACHMENTS_PER_MESSAGE
import com.tribixbite.stoatally.api.routes.microservices.autumn.uploadToAutumn
import com.tribixbite.stoatally.api.routes.server.fetchMember
import com.tribixbite.stoatally.api.routes.user.addUserIfUnknown
import com.tribixbite.stoatally.api.routes.user.fetchUser
import com.tribixbite.stoatally.core.model.schemas.Channel
import com.tribixbite.stoatally.core.model.schemas.Message
import com.tribixbite.stoatally.api.settings.GeoStateProvider
import com.tribixbite.stoatally.callbacks.Action
import com.tribixbite.stoatally.callbacks.ActionChannel
import com.tribixbite.stoatally.callbacks.UiCallback
import com.tribixbite.stoatally.callbacks.UiCallbacks
import com.tribixbite.stoatally.internals.text.MessageProcessor
import com.tribixbite.stoatally.persistence.KVStorage
import com.tribixbite.stoatally.screens.chat.ChatRouterDestination
import com.tribixbite.stoatally.settings.providers.AgeGateUnlockedStorageProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.http.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class ChannelScreenViewModel @Inject constructor(
    private val kvStorage: KVStorage,
) : ViewModel() {
    var items = mutableStateListOf<ChannelScreenItem>()
    var typingUsers = mutableStateListOf<String>()

    var channel by mutableStateOf<Channel?>(null)
    var activePane by mutableStateOf<ChannelScreenActivePane>(ChannelScreenActivePane.None)
    var keyboardHeight by mutableIntStateOf(0)

    // Setting [initialTextFieldValue] causes the text field to switch value.
    // However [initialTextFieldValue] gets de-synced with the actual text field value when the user types.
    // For a field that keeps track of the actual text field value, see [draftContent].
    // Setting [draftContent] does not cause the text field to switch value.
    // Note that if [initialTextFieldValue] is set to the same value it has now, the text field will not switch value.
    // Set [initialTextFieldValueDirtyMarker] to a new value to force the text field to switch value.
    var initialTextFieldValue by mutableStateOf("")
    var initialTextFieldValueDirtyMarker by mutableStateOf("")
    var draftContent by mutableStateOf("")
    var draftAttachments = mutableStateListOf<FileArgs>()
    var draftReplyTo = mutableStateListOf<SendMessageReply>()
    var attachmentUploadProgress by mutableStateOf(0f)

    var endOfChannel by mutableStateOf(false)
    var didInitialChannelFetch by mutableStateOf(false)

    /** True when viewing history (after loadMessagesAround) instead of the latest messages. */
    var isViewingHistory by mutableStateOf(false)

    var ensuredSelfMember by mutableStateOf(false)

    var denyMessageField by mutableStateOf(false)
    var denyMessageFieldReasonResource by mutableIntStateOf(R.string.typing_blank)

    var editingMessage by mutableStateOf<String?>(null)

    // Transient error message shown when attachment upload fails
    var uploadError by mutableStateOf<String?>(null)

    // Prevents duplicate sends from rapid tapping (upstream #33/#30)
    var isSendingMessage by mutableStateOf(false)
        private set


    var ageGateUnlocked by mutableStateOf<Boolean?>(null)
    var showGeoGate by mutableStateOf(false)

    init {
        viewModelScope.launch {
            keyboardHeight = kvStorage.getInt("keyboardHeight") ?: 900 // reasonable default for now
        }
    }

    private var loadMessagesJob: Job? = null

    // Channel ID we're waiting for in cache (upstream #41/#21)
    private var pendingChannelId: String? = null
    private var cacheWatchJob: Job? = null

    fun switchChannel(id: String) {
        // Reset state
        this.loadMessagesJob?.cancel()
        this.cacheWatchJob?.cancel()
        this.pendingChannelId = null
        this.channel = StoatAPI.channelCache[id]
        this.items = mutableStateListOf(ChannelScreenItem.Loading)
        this.activePane = ChannelScreenActivePane.None
        this.typingUsers = mutableStateListOf()
        this.endOfChannel = false
        this.didInitialChannelFetch = false
        this.isViewingHistory = false
        this.ensuredSelfMember = false
        this.denyMessageField = false
        this.denyMessageFieldReasonResource = R.string.typing_blank
        this.editingMessage = null
        this.ageGateUnlocked = channel?.nsfw != true
        this.showGeoGate = when {
            channel?.nsfw == true && GeoStateProvider.geoState?.isAgeRestrictedGeo == true -> true
            else -> false
        }
        viewModelScope.launch {
            if (ageGateUnlocked != true) {
                ageGateUnlocked = AgeGateUnlockedStorageProvider.getAgeGateUnlocked()
            }
        }

        viewModelScope.launch {
            putDraftContent(kvStorage.get("draftContent/$id") ?: "", true)
        }
        this.draftAttachments = mutableStateListOf()
        this.draftReplyTo = mutableStateListOf()
        this.attachmentUploadProgress = 0f

        if (channel != null) {
            // Channel is in cache — proceed normally
            // denyMessageFieldIfNeeded() already fetches the member if missing,
            // so ensureSelfHasMember() is redundant — skip it to avoid a duplicate API call
            viewModelScope.launch {
                denyMessageFieldIfNeeded()
                ensuredSelfMember = true
            }
            this.loadMessages(50, markLastAsRead = true)
        } else {
            // Channel not in cache yet (app restart before WebSocket Ready).
            // Watch the cache and retry when it appears (upstream #41/#21).
            pendingChannelId = id
            cacheWatchJob = viewModelScope.launch {
                val cachedChannel = withTimeoutOrNull(30_000L) {
                    snapshotFlow { StoatAPI.channelCache[id] }
                        .filterNotNull()
                        .first()
                }
                if (cachedChannel != null) {
                    channel = cachedChannel
                    pendingChannelId = null
                    ageGateUnlocked = cachedChannel.nsfw != true
                    showGeoGate = cachedChannel.nsfw == true &&
                            GeoStateProvider.geoState?.isAgeRestrictedGeo == true
                    denyMessageFieldIfNeeded()
                    ensuredSelfMember = true
                    loadMessages(50, markLastAsRead = true)
                } else {
                    // Channel never appeared — show error so user can navigate away
                    Log.e("ChannelScreenViewModel", "Timed out waiting for channel $id in cache")
                    pendingChannelId = null
                    items = mutableStateListOf(
                        ChannelScreenItem.FetchError("Channel not available. Try switching to another channel.")
                    )
                }
            }
        }
    }

    suspend fun unlockAgeGate() {
        AgeGateUnlockedStorageProvider.setAgeGateUnlocked(true)
        ageGateUnlocked = true
    }

    private suspend fun ensureSelfHasMember() {
        channel?.server?.let { serverId ->
            StoatAPI.selfId?.let { selfId ->
                if (!StoatAPI.members.hasMember(serverId, selfId)) {
                    try {
                        fetchMember(serverId, selfId)
                    } catch (e: Exception) {
                        Log.e("ChannelScreenViewModel", "Failed to fetch member", e)
                    }
                }

                ensuredSelfMember = true
            }
        }
    }

    private suspend fun denyMessageFieldIfNeeded() {
        if (channel == null) return

        val selfUser = StoatAPI.userCache[StoatAPI.selfId] ?: return
        val selfMember = if (channel!!.server == null) {
            null
        } else {
            channel?.server?.let { serverId ->
                try {
                    StoatAPI.members.getMember(serverId, selfUser.id!!) ?: fetchMember(
                        serverId,
                        selfUser.id!!
                    )
                } catch (e: Exception) {
                    Log.e("ChannelScreenViewModel", "Failed to fetch member", e)
                    null
                }
            }
        }

        val permission = Roles.permissionFor(channel!!, selfUser, selfMember)
        val canSend = permission has PermissionBit.SendMessage

        val partnerId = ChannelUtils.resolveDMPartner(channel!!)

        // Explicit member timeout check — covers the startup race where the server
        // isn't cached yet so permissionFor grants full access (upstream #70)
        val isTimedOut = selfMember?.timeoutTimestamp()?.let { it > Clock.System.now() } == true

        denyMessageField = when {
            isTimedOut -> true
            partnerId == SpecialUsers.PLATFORM_MODERATION_USER -> true
            !canSend -> true
            else -> false
        }

        denyMessageFieldReasonResource = when {
            isTimedOut -> R.string.message_field_denied_timeout
            partnerId == SpecialUsers.PLATFORM_MODERATION_USER -> R.string.message_field_denied_platform_moderation
            !canSend -> R.string.message_field_denied_no_permission
            else -> R.string.message_field_denied_generic
        }
    }

    fun putAtCursorPosition(text: String) {
        putDraftContent(draftContent + text, true)
    }

    private var lastSentBeginTyping: Instant? = null

    private fun startTyping() {
        if (editingMessage != null) return
        if (lastSentBeginTyping != null) {
            val diff = Clock.System.now() - lastSentBeginTyping!!
            if (diff.inWholeSeconds < 1) return
        }

        viewModelScope.launch {
            withContext(StoatAPI.realtimeContext) {
                channel?.id?.let {
                    RealtimeSocket.beginTyping(it)
                }
            }
        }

        lastSentBeginTyping = Clock.System.now()
    }

    private var stopTypingJob: Job? = null

    private fun queueStopTyping() {
        stopTypingJob = viewModelScope.launch {
            delay(5000)
            stopTyping()
        }
    }

    private fun stopTyping() {
        if (editingMessage != null) return
        viewModelScope.launch {
            withContext(StoatAPI.realtimeContext) {
                channel?.id?.let {
                    RealtimeSocket.endTyping(it)
                }
            }
        }
    }

    /**
     * Puts the draft content in the KV storage, the in-memory state of the message content,
     * and, if [setInitial] is true, updates the text field to say the new [content].
     */
    fun putDraftContent(content: String, setInitial: Boolean = false) {
        viewModelScope.launch {
            kvStorage.set("draftContent/${channel?.id}", content)
        }

        if (editingMessage == null) {
            if (content.isNotBlank()) {
                startTyping()
                stopTypingJob?.cancel()
                queueStopTyping()
            } else {
                stopTyping()
            }
        }

        draftContent = content
        if (setInitial) {
            initialTextFieldValue = content
            initialTextFieldValueDirtyMarker = ULID.makeNext()
        }
    }

    suspend fun addReplyTo(messageId: String) {
        if (draftReplyTo.size >= 5) return
        if (draftReplyTo.any { it.id == messageId }) return

        val shouldMention = kvStorage.getBoolean("mentionOnReply") ?: false
        draftReplyTo.add(SendMessageReply(messageId, shouldMention))
    }

    suspend fun toggleMentionOnReply(messageId: String) {
        val shouldMention = draftReplyTo.find { it.id == messageId }?.mention ?: false
        val newItems = draftReplyTo.map {
            if (it.id == messageId) {
                it.copy(mention = !shouldMention)
            } else {
                it
            }
        }
        draftReplyTo.clear()
        draftReplyTo.addAll(newItems)
        kvStorage.set("mentionOnReply", !shouldMention)
    }

    fun updateSaveKeyboardHeight(height: Int) {
        viewModelScope.launch {
            kvStorage.set("keyboardHeight", height)
        }
        keyboardHeight = height
    }

    private suspend fun applyMessageEdit() {
        try {
            editMessage(
                channelId = channel?.id ?: return,
                messageId = editingMessage ?: return,
                newContent = draftContent,
            )
            putDraftContent("", true)
        } catch (e: Exception) {
            Log.e("ChannelScreenViewModel", "Failed to edit message", e)
        }
    }

    fun sendPendingMessage() {
        if (editingMessage != null) {
            viewModelScope.launch {
                applyMessageEdit()
                editingMessage = null
            }
            return
        }

        // Prevent duplicate sends from rapid tapping (upstream #33/#30)
        if (isSendingMessage) return
        isSendingMessage = true
        uploadError = null

        // Immediately, make copies of the draft content and replyTo list, as
        // 1. they will be cleared
        // 2. if the user changes the content while the message is being sent we want to persist
        //    the original content
        val content = MessageProcessor.processOutgoing(draftContent, channel?.server)
        val replyTo = draftReplyTo.toList()
        // Capture channel ID now to prevent race condition if user switches
        // channels during upload (upstream issue #17)
        val sendChannelId = channel?.id ?: run {
            isSendingMessage = false
            return
        }

        // First we upload (the next 5) attachments...
        viewModelScope.launch {
            try {
                val attachmentIds = arrayListOf<String>()
                val takenAttachments =
                    this@ChannelScreenViewModel.draftAttachments.take(MAX_ATTACHMENTS_PER_MESSAGE)
                val totalTaken = takenAttachments.size

                takenAttachments.forEachIndexed { index, it ->
                    try {
                        val id = uploadToAutumn(
                            it.file,
                            if (it.spoiler) "SPOILER_${it.filename}" else it.filename,
                            "attachments",
                            ContentType.parse(it.contentType),
                            onProgress = { current, total ->
                                attachmentUploadProgress =
                                    ((current.toFloat() / total.toFloat()) / totalTaken.toFloat()) + (index.toFloat() / totalTaken.toFloat())
                            }
                        )
                        attachmentIds.add(id)
                    } catch (e: Exception) {
                        Log.e("ChannelScreenViewModel", "Failed to upload attachment", e)
                        attachmentUploadProgress = 0f
                        uploadError = e.message ?: "Upload failed"
                        return@launch
                    }
                }

                val nonce = ULID.makeNext()
                val prospectiveMessage = Message(
                    id = nonce,
                    channel = sendChannelId,
                    author = StoatAPI.selfId,
                    content = content,
                    nonce = nonce,
                    attachments = listOf(),
                    replies = listOf(),
                    tail = items.firstOrNull()?.let {
                        if (it is ChannelScreenItem.RegularMessage) {
                            it.message.author == StoatAPI.selfId
                        } else if (it is ChannelScreenItem.ProspectiveMessage) {
                            it.message.author == StoatAPI.selfId
                        } else {
                            false
                        }
                    } ?: false
                )

                updateItems(listOf(ChannelScreenItem.ProspectiveMessage(prospectiveMessage)) + items)

                kvStorage.remove("draftContent/${sendChannelId}")
                putDraftContent("", true)
                draftReplyTo.clear()
                attachmentUploadProgress = 0f

                this@ChannelScreenViewModel.draftAttachments.removeAll(takenAttachments)

                try {
                    sendMessage(
                        channelId = sendChannelId,
                        content = content,
                        nonce = nonce,
                        replies = replyTo,
                        attachments = attachmentIds,
                        idempotencyKey = ULID.makeNext()
                    )
                } catch (e: Exception) {
                    Log.e("ChannelScreenViewModel", "Failed to send message", e)
                    updateItems(listOf(ChannelScreenItem.FailedMessage(prospectiveMessage)) + items.filter { it !is ChannelScreenItem.ProspectiveMessage })
                }
            } finally {
                isSendingMessage = false
            }
        }
    }

    /** Remove a prospective or failed message from the list by its nonce/id. */
    fun dismissPendingMessage(nonce: String) {
        viewModelScope.launch {
            updateItems(items.filter {
                when (it) {
                    is ChannelScreenItem.ProspectiveMessage -> it.message.id != nonce
                    is ChannelScreenItem.FailedMessage -> it.message.id != nonce
                    else -> true
                }
            })
        }
    }

    /** Retry sending a failed message: moves it back to prospective and re-sends. */
    fun retryFailedMessage(nonce: String) {
        val failed = items.firstOrNull {
            it is ChannelScreenItem.FailedMessage && it.message.id == nonce
        } as? ChannelScreenItem.FailedMessage ?: return

        val msg = failed.message
        val sendChannelId = msg.channel ?: return

        viewModelScope.launch {
            // Swap FailedMessage → ProspectiveMessage
            updateItems(items.map {
                if (it is ChannelScreenItem.FailedMessage && it.message.id == nonce) {
                    ChannelScreenItem.ProspectiveMessage(msg)
                } else it
            })

            try {
                sendMessage(
                    channelId = sendChannelId,
                    content = msg.content ?: "",
                    nonce = nonce,
                    // Reconstruct reply list from stored string IDs (mention=false safe default)
                    replies = msg.replies?.map { SendMessageReply(it, mention = false) } ?: listOf(),
                    attachments = msg.attachments?.mapNotNull { it.id } ?: listOf(),
                    idempotencyKey = ULID.makeNext()
                )
            } catch (e: Exception) {
                Log.e("ChannelScreenViewModel", "Retry failed: ${e.message}", e)
                updateItems(items.map {
                    if (it is ChannelScreenItem.ProspectiveMessage && it.message.id == nonce) {
                        ChannelScreenItem.FailedMessage(msg)
                    } else it
                })
            }
        }
    }

    /**
     * Load messages from the channel. If the channel is switched, the job will be cancelled.
     *
     * @param amount The amount of messages to load.
     * @param before Load [amount] messages before this message ID. Do not use with [around] or [after].
     * @param after Load [amount] messages after this message ID. Do not use with [around] or [before].
     * @param around Load [amount] messages around this message ID. Do not use with [before] or [after].
     * @param ignoreExisting If true, messages that are already in the list will not be added again. Possible performance degradation.
     */
    fun loadMessages(
        amount: Int,
        before: String? = null,
        after: String? = null,
        around: String? = null,
        ignoreExisting: Boolean = false,
        markLastAsRead: Boolean = false
    ) {
        channel?.id?.let { channelId ->
            loadMessagesJob = viewModelScope.launch {
                try {
                    val messages = arrayListOf<Message>()

                    fetchMessagesFromChannel(channelId, amount, true, before, after, around).let {
                        if (it.messages.isNullOrEmpty() || it.messages!!.size < 50) {
                            endOfChannel = true
                        }

                        it.users?.forEach { user ->
                            if (!StoatAPI.userCache.containsKey(user.id)) {
                                StoatAPI.userCache[user.id!!] = user
                            }
                        }

                        it.messages?.forEach { message ->
                            addUserIfUnknown(message.author ?: return@forEach)
                            val msgId = message.id ?: return@forEach
                            if (!StoatAPI.messageCache.containsKey(msgId)) {
                                StoatAPI.messageCache[msgId] = message
                            }
                            messages.add(message)
                        }

                        it.members?.forEach { member ->
                            val server = member.id?.server ?: return@forEach
                            val user = member.id?.user ?: return@forEach
                            if (!StoatAPI.members.hasMember(server, user)) {
                                StoatAPI.members.setMember(server, member)
                            }
                        }

                        if (markLastAsRead) {
                            // Ack the most recent message, or use a current ULID if no
                            // messages exist (e.g. last message was deleted — upstream #62)
                            val ackId = messages.firstOrNull()?.id ?: ULID.makeNext()
                            ackMessage(ackId)
                        }
                    }

                    val newItems = messages.filter {
                        if (ignoreExisting) {
                            items.none { m ->
                                when (m) {
                                    is ChannelScreenItem.RegularMessage -> m.message.id == it.id
                                    is ChannelScreenItem.ProspectiveMessage -> m.message.id == it.id
                                    is ChannelScreenItem.SystemMessage -> m.message.id == it.id
                                    is ChannelScreenItem.FailedMessage -> m.message.id == it.id
                                    else -> false
                                }
                            }
                        } else {
                            true
                        }
                    }.map {
                        when {
                            it.system != null -> ChannelScreenItem.SystemMessage(it)
                            else -> ChannelScreenItem.RegularMessage(it)
                        }
                    }

                    // Place items according to whether above/below/around was specified.
                    val newItemsWithPosition = when {
                        before != null -> items + newItems
                        after != null -> newItems + items
                        // around or initial load — replace entire list with new items centered on target
                        else -> newItems
                    }

                    updateItems(newItemsWithPosition)

                    if (!didInitialChannelFetch) {
                        didInitialChannelFetch = true
                    }
                } catch (e: Exception) {
                    Log.e("ChannelScreenViewModel", "Failed to fetch messages", e)
                    // Show error with retry only on initial load (shimmer state)
                    if (!didInitialChannelFetch) {
                        val errorMsg = when (e) {
                            is java.net.SocketTimeoutException -> "Connection timed out"
                            is java.io.IOException -> "Network error"
                            else -> e.message ?: "Failed to load messages"
                        }
                        items = mutableStateListOf(ChannelScreenItem.FetchError(errorMsg))
                    }
                }
            }
        }
    }

    /** Retry loading messages after a fetch error */
    fun retryLoadMessages() {
        val id = channel?.id ?: pendingChannelId
        if (id != null) {
            // Re-run the full switchChannel flow which handles both
            // cached and pending channel states
            switchChannel(id)
        }
    }

    /**
     * Replace the current message list with messages centered around the given message ID.
     * Used by search result navigation and jump-to-message when the target isn't loaded.
     */
    fun loadMessagesAround(messageId: String) {
        endOfChannel = false
        didInitialChannelFetch = false
        isViewingHistory = true
        items = mutableStateListOf(ChannelScreenItem.Loading)
        loadMessages(50, around = messageId)
    }

    /** Return to the latest messages after viewing history. */
    fun jumpToPresent() {
        if (!isViewingHistory) return
        isViewingHistory = false
        endOfChannel = false
        didInitialChannelFetch = false
        items = mutableStateListOf(ChannelScreenItem.Loading)
        loadMessages(50)
    }

    suspend fun ackMessage(messageId: String) {
        ackChannel(channel?.id ?: return, messageId)
    }

    suspend fun listenToWsEvents() {
        withContext(StoatAPI.realtimeContext) {
            StoatAPI.wsFrameChannel.onEach {
                when (it) {
                    is MessageFrame -> {
                        if (it.channel != channel?.id) return@onEach
                        // If we already have the message we are just catching up on the WebSocket connection. Skip
                        if (items.any { m -> (m is ChannelScreenItem.RegularMessage && m.message.id == it.id) || (m is ChannelScreenItem.SystemMessage && m.message.id == it.id) }) return@onEach

                        it.author?.let { userId ->
                            if (StoatAPI.userCache[userId] == null) {
                                StoatAPI.userCache[userId] = fetchUser(userId)
                            }
                        }
                        channel?.server?.let { serverId ->
                            try {
                                it.author?.let { userId ->
                                    // Only fetch if not already cached — avoids redundant
                                    // API call on every incoming message
                                    if (!StoatAPI.members.hasMember(serverId, userId)) {
                                        fetchMember(serverId, userId)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("ChannelScreenViewModel", "Failed to fetch member", e)
                            }
                        }

                        if (didInitialChannelFetch) { // this check is so that we don't end up with a message that arrives at the same time as the initial fetch in front of the loading indicator
                            val newItem = when {
                                it.system != null -> ChannelScreenItem.SystemMessage(it)
                                else -> ChannelScreenItem.RegularMessage(it)
                            }
                            val nonce = it.nonce
                            prependNewMessage(newItem) { m ->
                                // Filter out prospective messages matching this nonce
                                if (m is ChannelScreenItem.ProspectiveMessage) {
                                    m.message.id != nonce
                                } else {
                                    true
                                }
                            }
                        }

                        it.id?.let { mid -> ackMessage(mid) }
                    }

                    is MessageDeleteFrame -> {
                        if (it.channel != channel?.id) return@onEach

                        val newRenderableMessages =
                            items.filter { m ->
                                if (m is ChannelScreenItem.RegularMessage) {
                                    m.message.id != it.id
                                } else {
                                    true
                                }
                            }

                        updateItems(newRenderableMessages)
                    }


                    is MessageUpdateFrame -> {
                        if (it.channel != channel?.id) return@onEach

                        val messageFrame =
                            StoatJson.decodeFromJsonElement(MessageFrame.serializer(), it.data)

                        val hasMessage = items.any { m ->
                            m is ChannelScreenItem.RegularMessage && m.message.id == it.id
                        }
                        if (!hasMessage) return@onEach

                        if (messageFrame.author != null) {
                            addUserIfUnknown(messageFrame.author!!)
                        }

                        // Fast path: edits don't change grouping (author/timestamp unchanged)
                        editItemInPlace(it.id!!) { item ->
                            ChannelScreenItem.RegularMessage(
                                item.message.mergeWithPartial(messageFrame)
                            )
                        }
                    }

                    is MessageAppendFrame -> {
                        if (it.channel != channel?.id) return@onEach

                        // Fast path: appends (embeds loading) don't change grouping
                        editItemInPlace(it.id!!) { item ->
                            StoatAPI.messageCache[it.id]?.let { m ->
                                ChannelScreenItem.RegularMessage(m)
                            } ?: item
                        }
                    }

                    is MessageReactFrame -> {
                        if (it.channel_id != channel?.id) return@onEach

                        // Fast path: reactions don't change grouping
                        editItemInPlace(it.id!!) { item ->
                            StoatAPI.messageCache[it.id]?.let { m ->
                                ChannelScreenItem.RegularMessage(m)
                            } ?: item
                        }
                    }

                    is MessageUnreactFrame -> {
                        if (it.channel_id != channel?.id) return@onEach

                        // Fast path: reactions don't change grouping
                        editItemInPlace(it.id!!) { item ->
                            StoatAPI.messageCache[it.id]?.let { m ->
                                ChannelScreenItem.RegularMessage(m)
                            } ?: item
                        }
                    }

                    is ChannelStartTypingFrame -> {
                        if (it.id != channel?.id) return@onEach
                        if (typingUsers.contains(it.user)) return@onEach
                        if (it.user == StoatAPI.selfId) return@onEach

                        addUserIfUnknown(it.user)
                        typingUsers.add(it.user)
                    }

                    is ChannelStopTypingFrame -> {
                        if (it.id != channel?.id) return@onEach
                        if (!typingUsers.contains(it.user)) return@onEach

                        typingUsers.remove(it.user)
                    }

                    is ChannelDeleteFrame -> {
                        if (it.id != channel?.id) return@onEach
                        // FIXME This is UI logic from the view model. Too bad!
                        ActionChannel.send(
                            Action.ChatNavigate(
                                ChatRouterDestination.NoCurrentChannel(
                                    channel?.server ?: return@onEach
                                )
                            )
                        )
                    }

                    is RealtimeSocketFrames.Reconnected -> {
                        Log.d("ChannelScreen", "Reconnected to WS.")
                        loadMessages(50, ignoreExisting = true)
                        typingUsers.clear()
                        listenToWsEvents()
                    }
                }
            }.catch {
                Log.e("ChannelScreen", "Failed to receive WS frame", it)
            }.launchIn(this)
        }
    }

    suspend fun listenToUiCallbacks() {
        withContext(Dispatchers.Main) {
            UiCallbacks.uiCallbackFlow.onEach {
                Log.d("ChannelScreen", "Received UI callback: $it")

                when (it) {
                    is UiCallback.ReplyToMessage -> {
                        // Use addReplyTo() which enforces max-5 limit and
                        // deduplication (upstream #57: duplicate reply banners)
                        addReplyTo(it.messageId)
                    }

                    is UiCallback.EditMessage -> {
                        editingMessage = it.messageId
                        val message = items.find { m ->
                            m is ChannelScreenItem.RegularMessage && m.message.id == it.messageId
                        } as? ChannelScreenItem.RegularMessage ?: return@onEach

                        putDraftContent(message.message.content ?: "", true)
                        this@ChannelScreenViewModel.draftAttachments.clear()
                        draftReplyTo.clear()
                    }

                    is UiCallback.ReplyToMessageWithContent -> {
                        // Use addReplyTo() for deduplication + max-5 limit (#57)
                        addReplyTo(it.messageId)
                        putDraftContent(it.content, true)
                    }
                }
            }.catch {
                Log.e("ChannelScreen", "Failed to receive UI callback", it)
            }.launchIn(this)
        }
    }

    private suspend fun updateItems(newItems: List<ChannelScreenItem>) {
        // Spec https://wiki.rvlt.gg/index.php/Text_Channel_(UI)#Message_Grouping_Algorithm
        val innerItems = newItems.toMutableStateList()
        // Let L be the list of messages ordered from newest to oldest
        val allItemsThatAreMessages =
            innerItems.filterIsInstance<ChannelScreenItem.RegularMessage>()
        // Let E be the list of elements to be rendered
        val allItems = innerItems

        val groupedItems = mutableListOf<ChannelScreenItem>()

        // For each message M in L:
        allItems.forEachIndexed { index, m ->
            // [Deviation from spec: if M is not a [Regular/System]Message we just put it in the list...]
            if (m !is ChannelScreenItem.RegularMessage && m !is ChannelScreenItem.SystemMessage) {
                groupedItems.add(m)
                Log.d("ChannelScreenViewModel", "Non-regular message: $m. Skipping grouping.")
                return@forEachIndexed
            }

            val message = when (m) {
                is ChannelScreenItem.RegularMessage -> m.message
                is ChannelScreenItem.SystemMessage -> m.message
                else -> null
            }

            // Let tail be true
            var tail = true
            // Let date be null
            var date: Instant? = null
            // Let next be the next item in list L
            val next = allItems.getOrNull(index + 1)
            // If next is not null:
            if (next != null) {
                // Let adate and bdate be the times the message M and the next message were created respectively
                val adate = message?.id?.let { ULID.asTimestamp(it) }?.let {
                    Instant.fromEpochMilliseconds(it)
                }
                val bdate = (next as? ChannelScreenItem.RegularMessage)?.message?.id?.let {
                    ULID.asTimestamp(it)
                }?.let {
                    Instant.fromEpochMilliseconds(it)
                }

                // [Deviation from spec: if either adate or bdate is null but next is a RegularMessage we skip this message]
                if ((adate == null || bdate == null) && next is ChannelScreenItem.RegularMessage) {
                    return@forEachIndexed
                }

                if (adate != null && bdate != null) {
                    // If adate and bdate are not the same day:
                    val adateLocal =
                        adate.toJavaInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                    val bdateLocal =
                        bdate.toJavaInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                    if (!adateLocal.isEqual(bdateLocal)) {
                        // Let date be adate
                        date = adate
                    }
                }

                val minuteDifference = adate?.let {
                    bdate?.let { bdate -> it.minus(bdate) }
                }?.inWholeMinutes

                // [Conditions, which we have extracted into variables for readability]
                // Message M and last [spec should say next] have the same author
                val authorsMatch =
                    message?.author == (next as? ChannelScreenItem.RegularMessage)?.message?.author
                // The difference between bdate and adate is equal to or over 7 minutes
                val closeEnough = minuteDifference != null && minuteDifference >= 7
                // The masquerades for message M and last [spec should say next] do not match
                val masqueradesMatch =
                    message?.masquerade == (next as? ChannelScreenItem.RegularMessage)?.message?.masquerade
                // [Possible optimisation: in theory we should not need to check for system messages here as they are a separate type of renderable item]
                // The message M or last [spec should say next] is a system message
                val eitherIsSystem =
                    message?.system != null || (next as? ChannelScreenItem.RegularMessage)?.message?.system != null
                // Message M replies to one or more messages
                val messageHasReplies = message?.replies?.isNotEmpty() == true

                // Let tail be false if one of the following conditions is satisfied:
                if (!authorsMatch || closeEnough || !masqueradesMatch || eitherIsSystem || messageHasReplies) {
                    tail = false
                }
            }
            // Else if next is null:
            else {
                // Let tail be false
                tail = false
            }

            // Push the message
            groupedItems.add(
                when (m) {
                    is ChannelScreenItem.RegularMessage -> ChannelScreenItem.RegularMessage(
                        m.message.copy(
                            tail = tail
                        )
                    )

                    is ChannelScreenItem.SystemMessage -> ChannelScreenItem.SystemMessage(
                        m.message.copy(
                            tail = tail
                        )
                    )

                    else -> m
                }
            )
            // [Deviation from spec: we first push the message, then the date, to preserve UI order]
            // If date is not null:
            if (date != null) {
                // Push the date
                groupedItems.add(ChannelScreenItem.DateDivider(date))
            }
        }

        withContext(Dispatchers.Main) {
            items.clear()
            items.addAll(groupedItems.fastDistinctBy {
                when (it) {
                    is ChannelScreenItem.RegularMessage -> it.message.id
                    is ChannelScreenItem.SystemMessage -> it.message.id
                    is ChannelScreenItem.DateDivider -> it.instant.toString()
                    else -> it.toString() // Fallback for other item types
                }
            })
        }
    }

    /**
     * Fast path for in-place message edits (reactions, content updates).
     * Skips the full grouping algorithm since author/timestamp/order don't change.
     * Only triggers a single snapshot mutation instead of clear()+addAll().
     */
    private fun editItemInPlace(
        messageId: String,
        transform: (ChannelScreenItem.RegularMessage) -> ChannelScreenItem
    ) {
        val index = items.indexOfFirst {
            it is ChannelScreenItem.RegularMessage && it.message.id == messageId
        }
        if (index >= 0) {
            items[index] = transform(items[index] as ChannelScreenItem.RegularMessage)
        }
    }

    /**
     * Fast path for prepending a new incoming message.
     * Only computes tail grouping for the new item against the current first message,
     * and inserts a date divider if the day changed. Avoids full list regrouping.
     */
    private fun prependNewMessage(
        newItem: ChannelScreenItem,
        filterPredicate: ((ChannelScreenItem) -> Boolean)? = null
    ) {
        // Remove matching prospective/nonce messages if needed
        if (filterPredicate != null) {
            items.removeAll { !filterPredicate(it) }
        }

        val message = when (newItem) {
            is ChannelScreenItem.RegularMessage -> newItem.message
            is ChannelScreenItem.SystemMessage -> newItem.message
            else -> null
        }

        // Find the first real message in the current list for grouping comparison
        val nextItem = items.firstOrNull {
            it is ChannelScreenItem.RegularMessage || it is ChannelScreenItem.SystemMessage
        }
        val nextMessage = when (nextItem) {
            is ChannelScreenItem.RegularMessage -> nextItem.message
            is ChannelScreenItem.SystemMessage -> nextItem.message
            else -> null
        }

        var tail = true
        var dateDivider: ChannelScreenItem.DateDivider? = null

        if (nextMessage != null && message != null) {
            val adate = message.id?.let { ULID.asTimestamp(it) }?.let {
                kotlinx.datetime.Instant.fromEpochMilliseconds(it)
            }
            val bdate = nextMessage.id?.let { ULID.asTimestamp(it) }?.let {
                kotlinx.datetime.Instant.fromEpochMilliseconds(it)
            }

            if (adate != null && bdate != null) {
                val adateLocal = adate.toJavaInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                val bdateLocal = bdate.toJavaInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                if (!adateLocal.isEqual(bdateLocal)) {
                    dateDivider = ChannelScreenItem.DateDivider(adate)
                }

                val minuteDifference = adate.minus(bdate).inWholeMinutes
                val authorsMatch = message.author == nextMessage.author
                val closeEnough = minuteDifference >= 7
                val masqueradesMatch = message.masquerade == nextMessage.masquerade
                val eitherIsSystem = message.system != null || nextMessage.system != null
                val messageHasReplies = message.replies?.isNotEmpty() == true

                if (!authorsMatch || closeEnough || !masqueradesMatch || eitherIsSystem || messageHasReplies) {
                    tail = false
                }
            } else {
                tail = false
            }
        } else {
            tail = false
        }

        val itemWithTail = when (newItem) {
            is ChannelScreenItem.RegularMessage -> ChannelScreenItem.RegularMessage(
                newItem.message.copy(tail = tail)
            )
            is ChannelScreenItem.SystemMessage -> ChannelScreenItem.SystemMessage(
                newItem.message.copy(tail = tail)
            )
            else -> newItem
        }

        // Insert date divider first (after new message, before old messages)
        if (dateDivider != null) {
            items.add(0, dateDivider)
        }
        // Then insert the new message at position 0 (newest first)
        items.add(0, itemWithTail)
    }

    var showPhysicalKeyboardSpark by mutableStateOf(false)
    fun usesPhysicalKeyboard() {
        viewModelScope.launch {
            if (kvStorage.getBoolean("spark/physicalKeyboard/dismissed") != true) {
                showPhysicalKeyboardSpark = true
            }
        }
    }

    fun dismissPhysicalKeyboardSpark() {
        viewModelScope.launch {
            kvStorage.set("spark/physicalKeyboard/dismissed", true)
            showPhysicalKeyboardSpark = false
        }
    }
}