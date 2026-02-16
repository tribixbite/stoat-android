package com.tribixbite.stoatally.screens.search

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.tribixbite.stoatally.R
import com.tribixbite.stoatally.api.StoatAPI
import com.tribixbite.stoatally.api.internals.ULID
import com.tribixbite.stoatally.api.routes.channel.SearchResult
import com.tribixbite.stoatally.api.routes.channel.searchMessages
import com.tribixbite.stoatally.callbacks.Action
import com.tribixbite.stoatally.callbacks.ActionChannel
import com.tribixbite.stoatally.composables.chat.formatLongAsTime
import com.tribixbite.stoatally.composables.generic.UserAvatar
import com.tribixbite.stoatally.core.model.schemas.Message
import com.tribixbite.stoatally.core.model.schemas.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SearchSort(val apiValue: String) {
    Relevance("Relevance"),
    Latest("Latest"),
    Oldest("Oldest")
}

/** URL regex for detecting links in message content */
private val URL_REGEX = Regex(
    """https?://[^\s<>"{}|\\^`\[\]]+""",
    RegexOption.IGNORE_CASE
)

@HiltViewModel
class MessageSearchViewModel @Inject constructor() : ViewModel() {
    var channelId by mutableStateOf("")
    /** When set, searches across all text channels in this server */
    var serverId by mutableStateOf("")
    var query by mutableStateOf("")
    var isLoading by mutableStateOf(false)
    var hasSearched by mutableStateOf(false)
    var sort by mutableStateOf(SearchSort.Latest)
    var pinnedOnly by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    /** Progress indicator for server-wide search: "Searching channel 3 of 12..." */
    var searchProgress by mutableStateOf<String?>(null)

    // Client-side content filters
    var hasLink by mutableStateOf(false)
    var hasAttachment by mutableStateOf(false)
    var hasImage by mutableStateOf(false)
    var hasFile by mutableStateOf(false)
    var hasEmbed by mutableStateOf(false)
    var hasReply by mutableStateOf(false)
    var hasReaction by mutableStateOf(false)
    var hasMention by mutableStateOf(false)
    var fromUser by mutableStateOf("")

    val results = mutableStateListOf<Message>()
    private val rawResults = mutableListOf<Message>()
    val userCache = mutableMapOf<String, User>()
    /** Maps channelId -> channel name for server-wide search result display */
    val channelNameCache = mutableMapOf<String, String>()

    private var canLoadMore by mutableStateOf(true)
    private var searchJob: Job? = null

    /** True when searching across an entire server (vs single channel) */
    val isServerSearch: Boolean get() = serverId.isNotBlank()

    /** Whether any client-side filter is active (requires include_users for from:user) */
    private val needsUserData: Boolean
        get() = fromUser.isNotBlank()

    /** Count of active client-side content filters (shown in collapsed filter header) */
    val activeFilterCount: Int
        get() = listOf(hasLink, hasAttachment, hasImage, hasFile, hasEmbed,
            hasReply, hasReaction, hasMention).count { it } +
            (if (fromUser.isNotBlank()) 1 else 0)

    /** Reset all client-side content filters */
    fun clearAllFilters() {
        hasLink = false
        hasAttachment = false
        hasImage = false
        hasFile = false
        hasEmbed = false
        hasReply = false
        hasReaction = false
        hasMention = false
        fromUser = ""
        applyClientFilters()
    }

    /** Trigger search explicitly (submit button / keyboard action) */
    fun submitSearch() {
        // Allow search with just filters and no text query
        val hasFilters = fromUser.isNotBlank() || hasAttachment || hasLink ||
            hasImage || hasFile || hasEmbed || hasReply || hasReaction || hasMention
        if (query.isBlank() && !pinnedOnly && !hasFilters) return
        // Validate query length (API accepts 1-64 chars)
        if (query.length > 64) {
            errorMessage = "Query too long (max 64 characters)"
            return
        }
        performSearch(fresh = true)
    }

    fun onSortChanged(newSort: SearchSort) {
        sort = newSort
        if (hasSearched) performSearch(fresh = true)
    }

    fun onPinnedToggled() {
        pinnedOnly = !pinnedOnly
        if (hasSearched) performSearch(fresh = true)
    }

    /** Reapply client-side filters without refetching */
    fun onFilterChanged() {
        applyClientFilters()
    }

    fun loadMore() {
        if (!canLoadMore || isLoading) return
        performSearch(fresh = false)
    }

    /**
     * Apply client-side filters to raw API results.
     * Filters: has:link, has:attachment, has:image, has:file, has:embed,
     * has:reply, has:reaction, from:user
     */
    private fun applyClientFilters() {
        val filtered = rawResults.filter { msg ->
            val passLink = !hasLink || (msg.content?.contains(URL_REGEX) == true)
            val passAttachment = !hasAttachment || !msg.attachments.isNullOrEmpty()
            val passImage = !hasImage || msg.attachments?.any {
                it.contentType?.startsWith("image/", ignoreCase = true) == true
            } == true
            val passFile = !hasFile || msg.attachments?.any {
                it.contentType?.startsWith("image/", ignoreCase = true) != true
            } == true
            val passEmbed = !hasEmbed || !msg.embeds.isNullOrEmpty()
            val passReply = !hasReply || !msg.replies.isNullOrEmpty()
            val passReaction = !hasReaction || !msg.reactions.isNullOrEmpty()
            val passMention = !hasMention || !msg.mentions.isNullOrEmpty()

            val passFrom = if (fromUser.isBlank()) {
                true
            } else {
                val authorId = msg.author
                val user = authorId?.let { userCache[it] ?: StoatAPI.userCache[it] }
                val name = user?.displayName ?: user?.username ?: ""
                name.contains(fromUser, ignoreCase = true)
            }

            passLink && passAttachment && passImage && passFile &&
                passEmbed && passReply && passReaction && passMention && passFrom
        }

        results.clear()
        results.addAll(filtered)
    }

    private fun performSearch(fresh: Boolean) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            isLoading = true
            hasSearched = true
            errorMessage = null
            searchProgress = null

            if (fresh) {
                rawResults.clear()
                results.clear()
                canLoadMore = true
            }

            if (isServerSearch) {
                performServerSearch()
            } else {
                performChannelSearch(channelId, fresh)
            }

            isLoading = false
            searchProgress = null
        }
    }

    /** Search a single channel (original behavior) */
    private suspend fun performChannelSearch(targetChannelId: String, fresh: Boolean) {
        val beforeId = if (!fresh && rawResults.isNotEmpty()) {
            rawResults.lastOrNull()?.id
        } else {
            null
        }

        val apiQuery = when {
            pinnedOnly -> null
            query.isNotBlank() -> query
            else -> null
        }

        val useIncludeUsers = needsUserData || (rawResults.isEmpty() && userCache.isEmpty())

        try {
            val result = searchMessages(
                channelId = targetChannelId,
                query = apiQuery,
                limit = 25,
                before = beforeId,
                sort = sort.apiValue,
                includeUsers = if (useIncludeUsers) true else null,
                pinned = if (pinnedOnly) true else null
            )

            when (result) {
                is SearchResult.Success -> {
                    result.data.users?.forEach { user ->
                        user.id?.let { userCache[it] = user }
                    }

                    val messages = result.data.messages ?: emptyList()
                    if (messages.isEmpty()) {
                        canLoadMore = false
                    } else {
                        rawResults.addAll(messages)
                    }

                    applyClientFilters()
                }
                is SearchResult.Error -> {
                    errorMessage = result.message
                    canLoadMore = false
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            errorMessage = "${e.javaClass.simpleName}: ${e.message}"
            canLoadMore = false
            Log.e("MessageSearch", "Search failed", e)
        }
    }

    /**
     * Search across all text channels in a server.
     * No server-wide search API exists, so we iterate channels.
     * Results are aggregated and sorted by message timestamp.
     */
    private suspend fun performServerSearch() {
        val server = StoatAPI.serverCache[serverId]
        if (server == null) {
            errorMessage = "Server not found"
            return
        }

        // Get all text channel IDs from the server
        val channelIds = server.channels?.mapNotNull { cId ->
            val channel = StoatAPI.channelCache[cId]
            // Only search text channels (not voice, not categories)
            if (channel?.channelType?.let {
                it == com.tribixbite.stoatally.core.model.schemas.ChannelType.TextChannel ||
                it == com.tribixbite.stoatally.core.model.schemas.ChannelType.Group
            } != false) {
                // Cache channel names for display in results
                channel?.name?.let { name -> channelNameCache[cId] = name }
                cId
            } else null
        } ?: emptyList()

        if (channelIds.isEmpty()) {
            errorMessage = "No searchable channels found"
            return
        }

        val apiQuery = when {
            pinnedOnly -> null
            query.isNotBlank() -> query
            else -> null
        }

        val useIncludeUsers = needsUserData || userCache.isEmpty()
        var errorCount = 0

        // Search each channel, collecting results
        channelIds.forEachIndexed { index, cId ->
            val channelName = channelNameCache[cId] ?: "#$cId"
            searchProgress = "Searching $channelName (${index + 1}/${channelIds.size})..."

            try {
                val result = searchMessages(
                    channelId = cId,
                    query = apiQuery,
                    limit = 25,
                    sort = sort.apiValue,
                    includeUsers = if (useIncludeUsers) true else null,
                    pinned = if (pinnedOnly) true else null
                )

                when (result) {
                    is SearchResult.Success -> {
                        result.data.users?.forEach { user ->
                            user.id?.let { userCache[it] = user }
                        }
                        val messages = result.data.messages ?: emptyList()
                        rawResults.addAll(messages)
                        // Update results progressively so user sees them appear
                        applyClientFilters()
                    }
                    is SearchResult.Error -> {
                        errorCount++
                        Log.w("ServerSearch", "Channel $cId search failed: ${result.message}")
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                errorCount++
                Log.w("ServerSearch", "Channel $cId search exception: ${e.message}")
            }
        }

        // Sort all aggregated results by timestamp (newest first by default)
        rawResults.sortWith(compareByDescending { it.id ?: "" })
        applyClientFilters()

        canLoadMore = false // Server search fetches all at once
        if (errorCount > 0 && rawResults.isEmpty()) {
            errorMessage = "Search failed on all $errorCount channels"
        } else if (errorCount > 0) {
            // Partial success — note it but don't block results
            searchProgress = null
            errorMessage = "$errorCount channel(s) could not be searched"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MessageSearchScreen(
    channelId: String,
    serverId: String = "",
    navController: NavController,
    viewModel: MessageSearchViewModel = hiltViewModel()
) {
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(channelId, serverId) {
        viewModel.channelId = channelId
        viewModel.serverId = serverId
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Pagination trigger
    val isNearBottom by remember(listState) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisible = (layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) + 1
            lastVisible > (totalItems - 3) && totalItems > 0
        }
    }

    LaunchedEffect(isNearBottom) {
        snapshotFlow { isNearBottom }
            .distinctUntilChanged()
            .collect { near ->
                if (near) viewModel.loadMore()
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.statusBars,
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            painter = painterResource(R.drawable.icn_arrow_back_24dp),
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                title = {
                    BasicTextField(
                        value = viewModel.query,
                        onValueChange = { viewModel.query = it },
                        textStyle = LocalTextStyle.current.copy(
                            color = LocalContentColor.current,
                            fontSize = 16.sp
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = { viewModel.submitSearch() }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (viewModel.query.isEmpty()) {
                                    Text(
                                        text = if (viewModel.isServerSearch) {
                                            stringResource(R.string.search_server_hint)
                                        } else {
                                            stringResource(R.string.search_messages_hint)
                                        },
                                        style = LocalTextStyle.current.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            fontSize = 16.sp
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                },
                actions = {
                    if (viewModel.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 4.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = { viewModel.submitSearch() }) {
                            Icon(
                                painter = painterResource(R.drawable.icn_search_24dp),
                                contentDescription = stringResource(R.string.search_messages)
                            )
                        }
                    }
                }
            )
        }
    ) { pv ->
        // Filters collapse after first search to maximize result space
        var filtersExpanded by remember { mutableStateOf(true) }

        Column(
            modifier = Modifier
                .padding(pv)
                .fillMaxSize()
        ) {
            // Sort chips + pinned toggle (always visible)
            FlowRow(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = viewModel.pinnedOnly,
                    onClick = { viewModel.onPinnedToggled() },
                    label = { Text(stringResource(R.string.search_messages_filter_pinned)) }
                )

                SearchSort.entries.forEach { sortOption ->
                    FilterChip(
                        selected = viewModel.sort == sortOption,
                        onClick = { viewModel.onSortChanged(sortOption) },
                        label = {
                            Text(
                                when (sortOption) {
                                    SearchSort.Relevance -> stringResource(R.string.search_messages_sort_relevance)
                                    SearchSort.Latest -> stringResource(R.string.search_messages_sort_latest)
                                    SearchSort.Oldest -> stringResource(R.string.search_messages_sort_oldest)
                                }
                            )
                        }
                    )
                }
            }

            // Collapsible filter header — shows active count when collapsed
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { filtersExpanded = !filtersExpanded }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (filtersExpanded) "Filters ▾" else "Filters ▸",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!filtersExpanded && viewModel.activeFilterCount > 0) {
                        Text(
                            text = " (${viewModel.activeFilterCount} active)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (viewModel.activeFilterCount > 0) {
                    TextButton(onClick = { viewModel.clearAllFilters() }) {
                        Text(
                            text = stringResource(R.string.search_filter_clear_all),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            // Collapsible content filters
            AnimatedVisibility(
                visible = filtersExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    // Content filter chips (client-side has: filters)
                    FlowRow(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            selected = viewModel.hasLink,
                            onClick = {
                                viewModel.hasLink = !viewModel.hasLink
                                viewModel.onFilterChanged()
                            },
                            label = { Text(stringResource(R.string.search_filter_has_link)) }
                        )
                        FilterChip(
                            selected = viewModel.hasAttachment,
                            onClick = {
                                viewModel.hasAttachment = !viewModel.hasAttachment
                                viewModel.onFilterChanged()
                            },
                            label = { Text(stringResource(R.string.search_filter_has_attachment)) }
                        )
                        FilterChip(
                            selected = viewModel.hasImage,
                            onClick = {
                                viewModel.hasImage = !viewModel.hasImage
                                viewModel.onFilterChanged()
                            },
                            label = { Text(stringResource(R.string.search_filter_has_image)) }
                        )
                        FilterChip(
                            selected = viewModel.hasFile,
                            onClick = {
                                viewModel.hasFile = !viewModel.hasFile
                                viewModel.onFilterChanged()
                            },
                            label = { Text(stringResource(R.string.search_filter_has_file)) }
                        )
                        FilterChip(
                            selected = viewModel.hasEmbed,
                            onClick = {
                                viewModel.hasEmbed = !viewModel.hasEmbed
                                viewModel.onFilterChanged()
                            },
                            label = { Text(stringResource(R.string.search_filter_has_embed)) }
                        )
                        FilterChip(
                            selected = viewModel.hasReply,
                            onClick = {
                                viewModel.hasReply = !viewModel.hasReply
                                viewModel.onFilterChanged()
                            },
                            label = { Text(stringResource(R.string.search_filter_has_reply)) }
                        )
                        FilterChip(
                            selected = viewModel.hasReaction,
                            onClick = {
                                viewModel.hasReaction = !viewModel.hasReaction
                                viewModel.onFilterChanged()
                            },
                            label = { Text(stringResource(R.string.search_filter_has_reaction)) }
                        )
                        FilterChip(
                            selected = viewModel.hasMention,
                            onClick = {
                                viewModel.hasMention = !viewModel.hasMention
                                viewModel.onFilterChanged()
                            },
                            label = { Text(stringResource(R.string.search_filter_has_mention)) }
                        )
                    }

                    // From user filter
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.search_filter_from),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        BasicTextField(
                            value = viewModel.fromUser,
                            onValueChange = {
                                viewModel.fromUser = it
                                viewModel.onFilterChanged()
                            },
                            textStyle = LocalTextStyle.current.copy(
                                color = LocalContentColor.current,
                                fontSize = 14.sp
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = { viewModel.submitSearch() }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (viewModel.fromUser.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.search_filter_from_hint),
                                            style = LocalTextStyle.current.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                fontSize = 14.sp
                                            )
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }
                }
            }

            HorizontalDivider()

            // Result count when results are available
            if (viewModel.hasSearched && viewModel.results.isNotEmpty()) {
                Text(
                    text = "${viewModel.results.size} result(s)" +
                        if (viewModel.activeFilterCount > 0) " (filtered)" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // Server-wide search progress indicator
            if (viewModel.searchProgress != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = viewModel.searchProgress!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            // Error display
            if (viewModel.errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(12.dp)
                ) {
                    Text(
                        text = viewModel.errorMessage!!,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (viewModel.hasSearched && viewModel.results.isEmpty() && !viewModel.isLoading && viewModel.errorMessage == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.search_messages_no_results),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = viewModel.results,
                        key = { it.id ?: it.hashCode().toString() }
                    ) { message ->
                        SearchResultItem(
                            message = message,
                            user = message.author?.let {
                                viewModel.userCache[it] ?: StoatAPI.userCache[it]
                            },
                            channelName = if (viewModel.isServerSearch) {
                                message.channel?.let { cId ->
                                    viewModel.channelNameCache[cId]
                                        ?: StoatAPI.channelCache[cId]?.name
                                }
                            } else null,
                            onClick = {
                                // Navigate to message in channel and scroll to it
                                val targetChannelId = message.channel ?: channelId
                                val targetMessageId = message.id ?: return@SearchResultItem
                                scope.launch {
                                    ActionChannel.send(
                                        Action.SwitchChannelAndScrollToMessage(
                                            channelId = targetChannelId,
                                            messageId = targetMessageId
                                        )
                                    )
                                }
                                navController.popBackStack()
                            }
                        )
                        HorizontalDivider(modifier = Modifier.alpha(0.3f))
                    }

                    if (viewModel.isLoading && viewModel.results.isNotEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultItem(
    message: Message,
    user: User?,
    channelName: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        UserAvatar(
            username = user?.displayName ?: user?.username ?: "",
            userId = message.author ?: "",
            size = 36.dp,
            avatar = user?.avatar
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = user?.displayName ?: user?.username ?: stringResource(R.string.unknown),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                message.id?.let { id ->
                    Text(
                        text = formatLongAsTime(ULID.asTimestamp(id)),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        maxLines = 1
                    )
                }
            }

            // Show channel name for server-wide search results
            if (channelName != null) {
                Text(
                    text = "#$channelName",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = message.content ?: "",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )

            // Metadata indicators row
            val indicators = buildList {
                if (!message.attachments.isNullOrEmpty()) {
                    add("${message.attachments!!.size} attachment(s)")
                }
                if (!message.embeds.isNullOrEmpty()) {
                    add("${message.embeds!!.size} embed(s)")
                }
                if (!message.replies.isNullOrEmpty()) {
                    add("reply")
                }
                if (!message.reactions.isNullOrEmpty()) {
                    add("${message.reactions!!.size} reaction(s)")
                }
                if (!message.mentions.isNullOrEmpty()) {
                    add("${message.mentions!!.size} mention(s)")
                }
                if (message.pinned == true) {
                    add("pinned")
                }
            }
            if (indicators.isNotEmpty()) {
                Text(
                    text = indicators.joinToString(" · "),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
