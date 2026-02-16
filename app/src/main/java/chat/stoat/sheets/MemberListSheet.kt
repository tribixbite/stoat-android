package chat.stoat.sheets

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import chat.stoat.R
import chat.stoat.api.StoatAPI
import chat.stoat.api.internals.PermissionBit
import chat.stoat.api.internals.Roles
import chat.stoat.api.internals.hasPermission
import chat.stoat.api.routes.channel.fetchGroupParticipants
import chat.stoat.api.routes.server.fetchMembers
import chat.stoat.composables.chat.MemberListItem
import chat.stoat.composables.generic.CountableListHeader
import chat.stoat.composables.generic.Presence
import chat.stoat.composables.generic.SheetHeaderPadding
import chat.stoat.composables.generic.presenceFromStatus
import chat.stoat.core.model.schemas.Member
import chat.stoat.core.model.schemas.User
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

val DO_NOT_FETCH_OFFLINE_MEMBERS_SERVERS = listOf(
    "01F7ZSBSFHQ8TA81725KQCSDDP" // Lounge
)

sealed class MemberListSheetItem {
    data class MemberItem(val member: Member) : MemberListSheetItem()
    data class UserItem(val user: User) : MemberListSheetItem()
    data class CategoryItem(val category: String, val count: Int) : MemberListSheetItem()
}

@HiltViewModel
@SuppressLint("StaticFieldLeak")
class MemberListSheetViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    val fullItemList = mutableStateListOf<MemberListSheetItem>()

    fun fetchServerMemberList(serverId: String, channelId: String) {
        viewModelScope.launch {
            val memberList = fetchMembers(
                serverId = serverId,
                includeOffline = serverId !in DO_NOT_FETCH_OFFLINE_MEMBERS_SERVERS
            ).members
            val channel = StoatAPI.channelCache[channelId] ?: return@launch

            // Compute categorization off main thread — large servers have 1000+ members
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                val categories = mutableMapOf<String, List<Member>>()

                val offlineCategoryName = context.getString(R.string.status_offline)
                val defaultCategoryName = context.getString(R.string.status_online)

                memberList.forEach { member ->
                    val memberId = member.id?.user ?: return@forEach
                    val user = StoatAPI.userCache[memberId] ?: run {
                        Log.w(
                            "MemberListSheet",
                            "User $memberId found in member list of server $serverId but not in user cache"
                        )
                        return@forEach
                    }

                    if (user.online == false) {
                        categories[offlineCategoryName] =
                            (categories[offlineCategoryName] ?: listOf()) + member
                        return@forEach
                    }

                    val highestHoistedRole =
                        Roles.resolveHighestRole(serverId, memberId, hoisted = true)

                    val category = if (highestHoistedRole != null) {
                        highestHoistedRole.name ?: context.getString(R.string.unknown)
                    } else {
                        defaultCategoryName
                    }

                    if (!Roles.permissionFor(channel, user, member)
                            .hasPermission(PermissionBit.ViewChannel)
                    ) {
                        return@forEach
                    }

                    categories[category] = (categories[category] ?: listOf()) + member
                }

                // Build flat item list
                val items = mutableListOf<MemberListSheetItem>()

                // Hoisted roles
                Roles.inOrder(serverId) { it.hoist == true }.forEach { role ->
                    val members = categories[role.name] ?: return@forEach
                    items.add(MemberListSheetItem.CategoryItem(role.name ?: "", members.size))
                    members.forEach { member ->
                        items.add(MemberListSheetItem.MemberItem(member))
                    }
                }

                // Online
                if (!categories[defaultCategoryName].isNullOrEmpty()) {
                    items.add(
                        MemberListSheetItem.CategoryItem(
                            defaultCategoryName,
                            categories[defaultCategoryName]?.size ?: 0
                        )
                    )
                    categories[defaultCategoryName]?.forEach { member ->
                        items.add(MemberListSheetItem.MemberItem(member))
                    }
                }

                // Offline
                if (!categories[offlineCategoryName].isNullOrEmpty()) {
                    items.add(
                        MemberListSheetItem.CategoryItem(
                            offlineCategoryName,
                            categories[offlineCategoryName]?.size ?: 0
                        )
                    )
                    categories[offlineCategoryName]?.forEach { member ->
                        items.add(MemberListSheetItem.MemberItem(member))
                    }
                }

                items
            }

            // Update compose state on main thread
            fullItemList.clear()
            fullItemList.addAll(result)
        }
    }

    fun fetchGroupMemberList(channelId: String) {
        viewModelScope.launch {
            val userList = fetchGroupParticipants(channelId)

            val onlinePredicate = { user: User ->
                presenceFromStatus(
                    user.status?.presence,
                    user.online ?: false
                ) != Presence.Offline
            }
            val offlinePredicate = { user: User ->
                presenceFromStatus(
                    user.status?.presence,
                    user.online ?: false
                ) == Presence.Offline
            }

            fullItemList.clear()

            if (userList.count(onlinePredicate) > 0) {
                fullItemList.add(
                    MemberListSheetItem.CategoryItem(
                        context.getString(R.string.status_online),
                        userList.count(onlinePredicate)
                    )
                )

                userList.filter(onlinePredicate).forEach { user ->
                    fullItemList.add(MemberListSheetItem.UserItem(user))
                }
            }

            if (userList.count(offlinePredicate) > 0) {
                fullItemList.add(
                    MemberListSheetItem.CategoryItem(
                        context.getString(R.string.status_offline),
                        userList.count(offlinePredicate)
                    )
                )

                userList.filter(offlinePredicate).forEach { user ->
                    fullItemList.add(MemberListSheetItem.UserItem(user))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MemberListSheet(
    channelId: String,
    serverId: String? = null,
    viewModel: MemberListSheetViewModel = hiltViewModel()
) {
    var showUserInfoSheet by remember { mutableStateOf(false) }
    var userInfoSheetTarget by remember { mutableStateOf("") }
    var showMemberContextSheet by remember { mutableStateOf(false) }
    var memberContextSheetTarget by remember { mutableStateOf("") }

    // We use LaunchedEffect to make sure that this is called every time any of the users status changes
    LaunchedEffect(StoatAPI.userCache) {
        snapshotFlow { StoatAPI.userCache }.distinctUntilChanged().collect {
            if (serverId != null) {
                viewModel.fetchServerMemberList(serverId, channelId)
            } else {
                viewModel.fetchGroupMemberList(channelId)
            }
        }
    }

    if (showUserInfoSheet) {
        val userContextSheetState = rememberModalBottomSheetState()

        ModalBottomSheet(
            sheetState = userContextSheetState,
            onDismissRequest = {
                showUserInfoSheet = false
            }
        ) {
            UserInfoSheet(
                userId = userInfoSheetTarget,
                serverId = serverId,
                dismissSheet = {
                    userContextSheetState.hide()
                    showUserInfoSheet = false
                }
            )
        }
    }

    if (showMemberContextSheet) {
        val memberContextSheetState = rememberModalBottomSheetState()

        ModalBottomSheet(
            sheetState = memberContextSheetState,
            onDismissRequest = {
                showMemberContextSheet = false
            }
        ) {
            if (serverId != null) {
                ServerMemberContextSheet(
                    userId = memberContextSheetTarget,
                    serverId = serverId,
                    channelId = channelId,
                    onRequestUpdateMembers = {
                        viewModel.fetchServerMemberList(serverId, channelId)
                    },
                    dismissSheet = {
                        memberContextSheetState.hide()
                        showMemberContextSheet = false
                    }
                )
            } else {
                GroupDMMemberContextSheet(
                    userId = memberContextSheetTarget,
                    channelId = channelId,
                    onRequestUpdateMembers = {
                        viewModel.fetchGroupMemberList(channelId)
                    },
                    dismissSheet = {
                        memberContextSheetState.hide()
                        showMemberContextSheet = false
                    }
                )
            }

        }
    }

    var searchQuery by remember { mutableStateOf("") }

    // Filter items based on search query
    val filteredItems by remember(searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) {
                viewModel.fullItemList.toList()
            } else {
                val query = searchQuery.lowercase()
                viewModel.fullItemList.filter { item ->
                    when (item) {
                        is MemberListSheetItem.MemberItem -> {
                            val user = item.member.id?.user?.let { StoatAPI.userCache[it] }
                            val nickname = item.member.nickname?.lowercase()
                            val username = user?.username?.lowercase()
                            val displayName = user?.displayName?.lowercase()
                            nickname?.contains(query) == true ||
                                    username?.contains(query) == true ||
                                    displayName?.contains(query) == true
                        }
                        is MemberListSheetItem.UserItem -> {
                            val username = item.user.username?.lowercase()
                            val displayName = item.user.displayName?.lowercase()
                            username?.contains(query) == true ||
                                    displayName?.contains(query) == true
                        }
                        is MemberListSheetItem.CategoryItem -> true // keep category headers
                    }
                }
            }
        }
    }

    Column(Modifier.animateContentSize()) {
        if (viewModel.fullItemList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            return@Column
        }

        SheetHeaderPadding {
            Text(
                text = stringResource(R.string.channel_info_sheet_options_members),
                style = MaterialTheme.typography.headlineSmall
            )
        }

        // Search field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(stringResource(R.string.member_search_hint)) },
            singleLine = true,
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            painter = painterResource(R.drawable.icn_close_24dp),
                            contentDescription = null
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )

        LazyColumn {
            filteredItems.forEachIndexed { index, item ->
                when (item) {
                    is MemberListSheetItem.CategoryItem -> stickyHeader(
                        key = "${item.category}-$index"
                    ) {
                        CountableListHeader(
                            text = item.category,
                            count = item.count,
                            backgroundColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    }

                    is MemberListSheetItem.MemberItem -> {
                        val uid = item.member.id?.user ?: return@forEachIndexed
                        item(key = uid) {
                            MemberListItem(
                                user = StoatAPI.userCache[uid],
                                member = item.member,
                                serverId = serverId,
                                userId = uid,
                                modifier = Modifier
                                    .combinedClickable(
                                        onClick = {
                                            userInfoSheetTarget = uid
                                            showUserInfoSheet = true
                                        },
                                        onClickLabel = stringResource(R.string.user_info_sheet_open),
                                        onLongClick = {
                                            memberContextSheetTarget = uid
                                            showMemberContextSheet = true
                                        },
                                        onLongClickLabel = stringResource(R.string.member_context_sheet_open)
                                    )
                            )
                        }
                    }

                    is MemberListSheetItem.UserItem -> {
                        val uid = item.user.id ?: return@forEachIndexed
                        item(key = uid) {
                            MemberListItem(
                                user = item.user,
                                member = null,
                                serverId = serverId,
                                userId = uid,
                                modifier = Modifier.combinedClickable(
                                    onClick = {
                                        userInfoSheetTarget = uid
                                        showUserInfoSheet = true
                                    },
                                    onClickLabel = stringResource(R.string.user_info_sheet_open),
                                    onLongClick = {
                                        memberContextSheetTarget = uid
                                        showMemberContextSheet = true
                                    },
                                    onLongClickLabel = stringResource(R.string.member_context_sheet_open)
                                )
                        )
                    }
                    }
                }
            }
        }
    }

}