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
All from `chat.stoat.api.internals.PermissionBit`:
- `ManageServer` — edit server info
- `ManageRole` — create/edit/delete roles
- `ManageChannel` — create channels
- `BanMembers` — view bans, ban/unban
- `KickMembers` — kick members
- `AssignRoles` — assign roles to members
- `ManageNicknames` — edit other members' nicknames
- `ChangeNickname` — edit own nickname

## String Resources
53 new entries in `strings.xml` under sections:
- Server settings (9), Role management (13), Ban management (6),
  Channel creation (8), Member management (7)
