# Server Management — Architecture Spec

## Overview
Server management UI for Stoat Android: edit server info, manage roles/bans, create channels,
and member moderation (nickname, role assignment, kick, ban).

## Screens

### ServerSettingsScreen
- **Route**: `settings/server/{serverId}`
- **Entry point**: ServerContextSheet → "Server Settings" button (requires ManageServer or owner)
- **ViewModel**: `ServerSettingsViewModel` (Hilt-injected, `@ApplicationContext`)
- **Features**:
  - Edit server name and description
  - Icon upload via `InlineMediaPicker` + `uploadToAutumn()` with progress indicator
  - Remove icon via `editServer(remove=["Icon"])`
  - FAB save button with `AnimatedVisibility` on change detection (`derivedStateOf`)
  - Permission-gated sub-navigation to roles/bans/channel creation

### RoleManagementScreen
- **Route**: `settings/server/{serverId}/roles`
- **Permission**: ManageRole or server owner
- **Features**:
  - LazyColumn listing roles with colour dot, name, rank
  - Create role dialog (name only)
  - Edit role dialog (name + hex colour with live preview via `parseColour()`)
  - Delete role confirmation dialog
  - All operations update both local `mutableStateListOf` and `StoatAPI.serverCache`

### BanManagementScreen
- **Route**: `settings/server/{serverId}/bans`
- **Permission**: BanMembers or server owner
- **Features**:
  - `LaunchedEffect` loads bans via `fetchBans(serverId)`
  - Loading/error/empty states
  - Displays banned user name + ban reason
  - Unban confirmation dialog via `unbanMember()`

### CreateChannelScreen
- **Route**: `settings/server/{serverId}/create-channel`
- **Permission**: ManageChannel or server owner
- **Features**:
  - Type selection via `FilterChip` (Text/Voice)
  - Name + optional description fields
  - Error display + loading state
  - Navigates back on success with toast

## MemberContextSheet Enhancements
- **Nickname edit**: requires `ChangeNickname` (self) or `ManageNicknames` (others) or owner
  - Blank nickname → `editMember(remove=["Nickname"])` to clear
- **Role assignment**: requires `AssignRoles` or owner (not self)
  - Toggle UI with `SheetButton` per role, saves full role list via `editMember(roles=)`
- **Kick/Ban**: existing, requires `KickMembers`/`BanMembers` or owner

## API Routes Used
| Route | File |
|-------|------|
| `PATCH /servers/{id}` | `api/routes/server/Server.kt → editServer()` |
| `POST /servers/{id}/roles` | `→ createRole()` |
| `PATCH /servers/{id}/roles/{roleId}` | `→ editRole()` |
| `DELETE /servers/{id}/roles/{roleId}` | `→ deleteRole()` |
| `GET /servers/{id}/bans` | `→ fetchBans()` |
| `DELETE /servers/{id}/bans/{userId}` | `→ unbanMember()` |
| `DELETE /servers/{id}/members/{userId}` | `→ kickMember()` |
| `PUT /servers/{id}/bans/{userId}` | `→ banMember()` |
| `PATCH /servers/{id}/members/{userId}` | `→ editMember()` |
| `POST /servers/{id}/channels` | `→ createChannel()` |
| `POST /autumn/icons` | `→ uploadToAutumn()` |

## Permission Bits
All from `com.tribixbite.stoatally.api.internals.PermissionBit`:
- `ManageServer` — edit server info
- `ManageRole` — create/edit/delete roles
- `ManageChannel` — create channels
- `BanMembers` — view bans, ban/unban
- `KickMembers` — kick members
- `AssignRoles` — assign roles to members
- `ManageNicknames` — edit other members' nicknames
- `ChangeNickname` — edit own nickname

### PermissionsEditorScreen
- **DefaultPermissionsEditorScreen**
  - Route: `settings/server/{serverId}/permissions/default`
  - Permission: ManagePermissions or server owner
  - Checkbox toggle per permission bit against `server.defaultPermissions`
  - Save via `setDefaultPermissions(serverId, newPermissions)`
  - Updates `StoatAPI.serverCache` on save
- **RolePermissionsEditorScreen**
  - Route: `settings/server/{serverId}/roles/{roleId}/permissions`
  - Permission: ManagePermissions or server owner
  - Tri-state per permission: Allow / Neutral / Deny (segmented buttons)
  - Decomposes `PermissionDescription(a, d)` bitmask per-bit
  - Save via `setServerPermissions(serverId, roleId, allow, deny)`
  - Updates role in `StoatAPI.serverCache` on save
- **Categories**: Admin (5), Members (8), Channels (6), Messaging (6), Voice (7)

## Notification Settings

### NotificationSettingsScreen
- **Route**: `settings/notifications`
- **Entry**: SettingsScreen → Notifications
- **ViewModel**: `NotificationSettingsViewModel` (Hilt, KVStorage)
- **Features**:
  - System notification permission status with link to system settings
  - FCM push registration status with retry button
  - Muted servers list showing server names from cache (raw ID as subtitle)
  - Muted channels list showing channel names + parent server from cache
  - Unmute button per entry
  - Reset all notification settings (danger action)

### Mute Toggles
- **Server mute**: ServerContextSheet → Mute/Unmute Server
- **Channel mute**: ChannelContextSheet → Mute/Unmute Channel
- **Notification filtering**: HandlerService checks `NotificationSettingsProvider.isChannelMuted()` before displaying

### EmojiManagementScreen
- **Route**: `settings/server/{serverId}/emojis`
- **Entry**: ServerSettingsScreen → Emoji (gated by ManageCustomisation)
- **Features**:
  - List all custom emoji for server from emojiCache, sorted by name
  - Emoji preview via Glide (from `$STOAT_FILES/emojis/{id}`)
  - Creator name from userCache, NSFW badge
  - Delete emoji with confirmation dialog
  - Add emoji dialog: name input (alphanumeric/underscore only), image picker, upload to `autumn/emojis`, create via `PUT /custom/emoji/{id}`
  - Upload progress indicator

### InviteManagementScreen
- **Route**: `settings/server/{serverId}/invites`
- **Entry**: ServerSettingsScreen → Invites (gated by ManageServer)
- **Features**:
  - List all server invites with creator name and channel name
  - Tap to copy invite link to clipboard
  - Delete invite with confirmation dialog
  - Empty state when no invites

### Mutual Friends/Servers (MemberContextSheet)
- **Location**: ServerMemberContextSheet, between moderation actions and Copy ID
- **API**: `GET /users/{id}/mutual` → `MutualInfo(users, servers)`
- **Features**:
  - Shows mutual friends count with resolved names from userCache
  - Shows mutual servers count with server names from serverCache
  - Non-blocking load (failure silently ignored)
  - Only shown for other users (not self)

## API Routes Used
| Route | File |
|-------|------|
| `PATCH /servers/{id}` | `api/routes/server/Server.kt → editServer()` |
| `POST /servers/{id}/roles` | `→ createRole()` |
| `PATCH /servers/{id}/roles/{roleId}` | `→ editRole()` |
| `DELETE /servers/{id}/roles/{roleId}` | `→ deleteRole()` |
| `GET /servers/{id}/bans` | `→ fetchBans()` |
| `DELETE /servers/{id}/bans/{userId}` | `→ unbanMember()` |
| `DELETE /servers/{id}/members/{userId}` | `→ kickMember()` |
| `PUT /servers/{id}/bans/{userId}` | `→ banMember()` |
| `PATCH /servers/{id}/members/{userId}` | `→ editMember()` |
| `POST /servers/{id}/channels` | `→ createChannel()` |
| `POST /autumn/icons` | `→ uploadToAutumn()` |
| `PUT /servers/{id}/permissions/default` | `→ setDefaultPermissions()` |
| `PUT /servers/{id}/permissions/{roleId}` | `→ setServerPermissions()` |
| `GET /servers/{id}/invites` | `→ fetchServerInvites()` |
| `DELETE /invites/{code}` | `→ deleteInvite()` |
| `POST /channels/{id}/invites` | `→ createChannelInvite()` |
| `PUT /custom/emoji/{id}` | `api/routes/custom/Emoji.kt → createEmoji()` |
| `DELETE /custom/emoji/{id}` | `→ deleteEmoji()` |
| `GET /users/{id}/mutual` | `api/routes/user/User.kt → fetchMutualFriendsAndServers()` |

## Permission Bits
All from `com.tribixbite.stoatally.api.internals.PermissionBit`:
- `ManageServer` — edit server info, manage invites
- `ManageRole` — create/edit/delete roles
- `ManageChannel` — create channels
- `ManagePermissions` — edit default/role permissions
- `ManageCustomisation` — manage custom emoji
- `BanMembers` — view bans, ban/unban
- `KickMembers` — kick members
- `AssignRoles` — assign roles to members
- `ManageNicknames` — edit other members' nicknames
- `ChangeNickname` — edit own nickname

## String Resources
180+ entries in `strings.xml`:
- Server settings (9), Role management (13), Ban management (6),
  Channel creation (8), Member management (7)
- Permission names and descriptions (75)
- Account settings (28), Invite management (12), Emoji management (14)
- Mutual friends/servers (2)
