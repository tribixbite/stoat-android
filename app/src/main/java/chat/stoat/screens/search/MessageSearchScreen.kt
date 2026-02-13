package chat.stoat.screens.search

import android.util.Log
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import chat.stoat.R
import chat.stoat.api.StoatAPI
import chat.stoat.api.internals.ULID
import chat.stoat.api.routes.channel.searchMessages
import chat.stoat.composables.chat.formatLongAsTime
import chat.stoat.composables.generic.UserAvatar
import chat.stoat.core.model.schemas.Message
import chat.stoat.core.model.schemas.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
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
    var query by mutableStateOf("")
    var isLoading by mutableStateOf(false)
    var hasSearched by mutableStateOf(false)
    var sort by mutableStateOf(SearchSort.Relevance)
    var pinnedOnly by mutableStateOf(false)

    // Discord-style content filters (applied client-side)
    var hasLink by mutableStateOf(false)
    var hasAttachment by mutableStateOf(false)
    var hasImage by mutableStateOf(false)
    var hasFile by mutableStateOf(false)
    var fromUser by mutableStateOf("")

    val results = mutableStateListOf<Message>()
    // Raw results before client-side filtering
    private val rawResults = mutableListOf<Message>()
    val userCache = mutableMapOf<String, User>()

    private var canLoadMore by mutableStateOf(true)
    private var searchJob: Job? = null

    private val queryFlow = MutableStateFlow("")

    @OptIn(FlowPreview::class)
    fun startListening() {
        viewModelScope.launch {
            queryFlow
                .debounce(400)
                .distinctUntilChanged()
                .filter { it.isNotBlank() }
                .collectLatest { q ->
                    performSearch(fresh = true)
                }
        }
    }

    fun onQueryChanged(newQuery: String) {
        query = newQuery
        queryFlow.value = newQuery
    }

    fun onSortChanged(newSort: SearchSort) {
        sort = newSort
        if (query.isNotBlank()) {
            performSearch(fresh = true)
        }
    }

    fun onPinnedToggled() {
        pinnedOnly = !pinnedOnly
        if (query.isNotBlank()) {
            performSearch(fresh = true)
        }
    }

    /** Toggle a client-side filter and reapply to current results */
    fun onFilterChanged() {
        applyClientFilters()
    }

    fun loadMore() {
        if (!canLoadMore || isLoading) return
        performSearch(fresh = false)
    }

    /**
     * Apply client-side filters (has:link, has:attachment, has:image, has:file, from:user)
     * to the raw results from the API.
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

            val passFrom = if (fromUser.isBlank()) {
                true
            } else {
                val authorId = msg.author
                val user = authorId?.let { userCache[it] ?: StoatAPI.userCache[it] }
                val name = user?.displayName ?: user?.username ?: ""
                name.contains(fromUser, ignoreCase = true)
            }

            passLink && passAttachment && passImage && passFile && passFrom
        }

        results.clear()
        results.addAll(filtered)
    }

    private fun performSearch(fresh: Boolean) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            isLoading = true
            hasSearched = true

            val beforeId = if (!fresh && rawResults.isNotEmpty()) {
                rawResults.lastOrNull()?.id
            } else {
                null
            }

            if (fresh) {
                rawResults.clear()
                results.clear()
                canLoadMore = true
            }

            try {
                val response = searchMessages(
                    channelId = channelId,
                    query = query,
                    limit = 25,
                    before = beforeId,
                    sort = sort.apiValue,
                    includeUsers = true,
                    pinned = if (pinnedOnly) true else null
                )

                response.users?.forEach { user ->
                    user.id?.let { userCache[it] = user }
                }

                val messages = response.messages ?: emptyList()
                if (messages.isEmpty()) {
                    canLoadMore = false
                } else {
                    rawResults.addAll(messages)
                }

                // Apply client-side filters
                applyClientFilters()
            } catch (e: Exception) {
                Log.e("MessageSearch", "Search failed: ${e.message}", e)
                canLoadMore = false
            }

            isLoading = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MessageSearchScreen(
    channelId: String,
    navController: NavController,
    viewModel: MessageSearchViewModel = hiltViewModel()
) {
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    LaunchedEffect(channelId) {
        viewModel.channelId = channelId
        viewModel.startListening()
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
                        onValueChange = { viewModel.onQueryChanged(it) },
                        textStyle = LocalTextStyle.current.copy(
                            color = LocalContentColor.current,
                            fontSize = 16.sp
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        singleLine = true,
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
                                        text = stringResource(R.string.search_messages_hint),
                                        style = LocalTextStyle.current.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            fontSize = 16.sp
                                        )
                                    )
                                }
                                innerTextField()

                                if (viewModel.query.isNotEmpty()) {
                                    Icon(
                                        painter = painterResource(R.drawable.icn_close_24dp),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .size(20.dp)
                                            .clickable {
                                                viewModel.onQueryChanged("")
                                            }
                                            .alpha(0.6f)
                                    )
                                }
                            }
                        }
                    )
                },
                actions = {
                    if (viewModel.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            )
        }
    ) { pv ->
        Column(
            modifier = Modifier
                .padding(pv)
                .fillMaxSize()
        ) {
            // Sort + pinned filter row
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

            // Content filters row (Discord-style has: filters)
            FlowRow(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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

            HorizontalDivider()

            if (viewModel.hasSearched && viewModel.results.isEmpty() && !viewModel.isLoading) {
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
                            onClick = {
                                // Navigate back to channel — future: scroll to message via nearby
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

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = message.content ?: "",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )

            // Show attachment indicator if message has attachments
            if (!message.attachments.isNullOrEmpty()) {
                Text(
                    text = "${message.attachments!!.size} attachment(s)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
