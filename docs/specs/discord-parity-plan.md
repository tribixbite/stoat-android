# Discord Feature Parity Plan — Stoat Android

Full implementation plan to bring Stoat Android to feature parity with Discord mobile, within the constraints of the Revolt API v0.11.0.

## Current State

- **121 API endpoints** available (OpenAPI v0.11.0)
- **65 implemented** in Android (54%)
- **56 remaining** implementable without backend changes
- **39 features** impossible without backend changes (see [backend-required-features.md](backend-required-features.md))

## Phase 1: Account & Security (High Priority)

### 1.1 Account Settings Screen
**New file**: `screens/settings/AccountSettingsScreen.kt`

| Task | Endpoint | Status |
|------|----------|--------|
| Fetch account info | `GET /auth/account/` | TODO |
| Change email | `PATCH /auth/account/change/email` | TODO |
| Change password | `PATCH /auth/account/change/password` | TODO |
| Change username | `PATCH /users/@me/username` | TODO |
| Delete account | `POST /auth/account/delete` + `PUT /auth/account/delete` | TODO |
| Disable account | `POST /auth/account/disable` | TODO |

### 1.2 MFA Management
**New file**: `screens/settings/MFASettingsScreen.kt`

| Task | Endpoint | Status |
|------|----------|--------|
| Check MFA status | `GET /auth/mfa/` | TODO |
| Get MFA methods | `GET /auth/mfa/methods` | TODO |
| Enable TOTP | `POST /auth/mfa/totp` → `PUT /auth/mfa/totp` | TODO |
| Disable TOTP | `DELETE /auth/mfa/totp` | TODO |
| Recovery codes | `PATCH /auth/mfa/recovery` + `POST /auth/mfa/recovery` | TODO |
| MFA ticket | `PUT /auth/mfa/ticket` | TODO |

### 1.3 Session Management Enhancement
**Modify**: `screens/settings/sessions/`

| Task | Endpoint | Status |
|------|----------|--------|
| Rename session | `PATCH /auth/session/{id}` | TODO |
| Proper logout | `POST /auth/session/logout` | TODO |
| Push unsubscribe on logout | `POST /push/unsubscribe` | TODO |

## Phase 2: Server Administration UI (High Priority)

### 2.1 Server Settings Screen
**New file**: `screens/settings/server/ServerSettingsScreen.kt`

| Task | API Route | Status |
|------|-----------|--------|
| Edit server name/description | `PATCH /servers/{id}` | API done, UI TODO |
| Edit server icon/banner | `PATCH /servers/{id}` + Autumn upload | API done, UI TODO |
| Set default permissions | `PUT /servers/{target}/permissions/default` | TODO |
| View/manage invites | `GET /servers/{target}/invites` + `DELETE /invites/{target}` | TODO |

### 2.2 Role Management Screen
**New file**: `screens/settings/server/RoleManagementScreen.kt`

| Task | API Route | Status |
|------|-----------|--------|
| List roles | From server cache | Available |
| Create role | `POST /servers/{id}/roles` | API done, UI TODO |
| Edit role (name, color) | `PATCH /servers/{id}/roles/{roleId}` | API done, UI TODO |
| Edit role permissions | `PUT /servers/{id}/permissions/{roleId}` | API done, UI TODO |
| Delete role | `DELETE /servers/{id}/roles/{roleId}` | API done, UI TODO |
| Reorder role ranks | `PATCH /servers/{target}/roles/ranks` | TODO |
| Fetch single role | `GET /servers/{target}/roles/{role_id}` | TODO |

### 2.3 Ban Management Screen
**New file**: `screens/settings/server/BanManagementScreen.kt`

| Task | API Route | Status |
|------|-----------|--------|
| List bans | `GET /servers/{id}/bans` | API done, UI TODO |
| Unban member | `DELETE /servers/{id}/bans/{userId}` | API done, UI TODO |

### 2.4 Channel Creation Dialog
**New file**: `sheets/CreateChannelSheet.kt`

| Task | API Route | Status |
|------|-----------|--------|
| Create text channel | `POST /servers/{id}/channels` | API done, UI TODO |
| Create voice channel | `POST /servers/{id}/channels` | API done, UI TODO |
| Set channel permissions | `PUT /channels/{id}/permissions/{roleId}` | API done, UI TODO |
| Set default channel perms | `PUT /channels/{target}/permissions/default` | TODO |

### 2.5 Member Management
**Enhance**: `sheets/MemberContextSheet.kt`

| Task | API Route | Status |
|------|-----------|--------|
| Edit nickname | `PATCH /servers/{id}/members/{userId}` | API done, UI TODO |
| Edit member avatar | `PATCH /servers/{id}/members/{userId}` + upload | API done, UI TODO |
| Assign roles | `PATCH /servers/{id}/members/{userId}` | API done, UI TODO |
| Timeout member | `PATCH /servers/{id}/members/{userId}` | API done, UI TODO |
| Search members | `GET /servers/{target}/members_experimental_query` | TODO |

## Phase 3: Social Features (Medium Priority)

### 3.1 Mutual Friends/Servers
**Enhance**: `screens/profile/UserProfileScreen.kt`

| Task | API Route | Status |
|------|-----------|--------|
| Show mutual friends | `GET /users/{target}/mutual` | TODO |
| Show mutual servers | `GET /users/{target}/mutual` | TODO |
| Show mutual groups | `GET /users/{target}/mutual` | TODO |

### 3.2 DM Channel List
**Enhance**: `screens/chat/` sidebar

| Task | API Route | Status |
|------|-----------|--------|
| Fetch all DM channels | `GET /users/dms` | TODO |

### 3.3 GIF Picker
**New file**: `composables/chat/GifPicker.kt`

| Task | Endpoint | Status |
|------|----------|--------|
| GIF categories | `GET /categories` (GifBox) | TODO |
| Search GIFs | `GET /search?q=` (GifBox) | TODO |
| Trending GIFs | `GET /trending` (GifBox) | TODO |
| Insert GIF as message | Send as embed URL | TODO |

### 3.4 Embed Generation
| Task | Endpoint | Status |
|------|----------|--------|
| Generate URL embeds | `GET /embed?url=` (January) | TODO |

## Phase 4: Content Management (Medium Priority)

### 4.1 Custom Emoji Management
**New file**: `screens/settings/server/EmojiManagementScreen.kt`

| Task | API Route | Status |
|------|-----------|--------|
| List server emojis | `GET /servers/{target}/emojis` | TODO |
| Upload new emoji | Upload to Autumn `emojis` tag + `PUT /custom/emoji/{id}` | TODO |
| Delete emoji | `DELETE /custom/emoji/{emoji_id}` | TODO |

### 4.2 Bulk Message Actions
**Enhance**: `screens/chat/ChatScreen.kt`

| Task | API Route | Status |
|------|-----------|--------|
| Multi-select messages | Client-side UI | TODO |
| Bulk delete selected | `DELETE /channels/{id}/messages/bulk` | API done, UI TODO |
| Remove all reactions | `DELETE /channels/{id}/messages/{msg}/reactions` | TODO |

### 4.3 Invite Management
| Task | API Route | Status |
|------|-----------|--------|
| List server invites | `GET /servers/{target}/invites` | TODO |
| Delete invite | `DELETE /invites/{target}` | TODO |

## Phase 5: Bot & Webhook Management (Low Priority)

### 5.1 Bot Management
**New file**: `screens/settings/BotManagementScreen.kt`

| Task | API Route | Status |
|------|-----------|--------|
| List owned bots | `GET /bots/@me` | TODO |
| Create bot | `POST /bots/create` | TODO |
| Fetch bot details | `GET /bots/{bot}` | TODO |
| Edit bot | `PATCH /bots/{target}` | TODO |
| Delete bot | `DELETE /bots/{target}` | TODO |
| Fetch public bot | `GET /bots/{target}/invite` | TODO |
| Invite bot to server | `POST /bots/{target}/invite` | TODO |

### 5.2 Webhook Management
**New file**: `screens/settings/server/WebhookManagementScreen.kt`

| Task | API Route | Status |
|------|-----------|--------|
| List webhooks | `GET /channels/{id}/webhooks` | TODO |
| Create webhook | `POST /channels/{id}/webhooks` | TODO |
| Edit webhook | `PATCH /webhooks/{id}` | TODO |
| Delete webhook | `DELETE /webhooks/{id}` | TODO |

## Phase 6: Polish & Enhancement (Low Priority)

### 6.1 Miscellaneous
| Task | API Route | Status |
|------|-----------|--------|
| User flags display | `GET /users/{target}/flags` | TODO |
| Default avatar fallback | `GET /users/{target}/default_avatar` | TODO |
| Policy acknowledgement | `POST /policy/acknowledge` | TODO |
| Stop voice ring | `PUT /channels/{target}/end_ring/{user}` | TODO |
| Password reset flow | `POST /auth/account/reset_password` | TODO |
| Email reverification | `POST /auth/account/reverify` | TODO |

### 6.2 UI Enhancements (no new endpoints)
| Task | Notes |
|------|-------|
| Server identity (own nick/avatar per server) | Use existing edit member API |
| Mark as unread | Ack to previous message ID |
| Message selection mode | For bulk operations |
| Server folders (client-side) | Use sync/settings for grouping |
| User notes (client-side) | Use sync/settings as KV store |
| Notification channel muting UI | Exists in API, needs UI |
| Voice channel UI improvements | LiveKit integration exists |

## Implementation Timeline Estimate

| Phase | Endpoints | New Screens | Priority |
|-------|-----------|-------------|----------|
| Phase 1: Account & Security | 15 | 2 | High |
| Phase 2: Server Admin UI | 10 | 4 | High |
| Phase 3: Social Features | 5 | 1 + 1 component | Medium |
| Phase 4: Content Management | 5 | 1 | Medium |
| Phase 5: Bot & Webhook | 11 | 2 | Low |
| Phase 6: Polish | 6 | 0 | Low |
| **Total** | **52** | **10+** | — |

After all phases: **117/121 endpoints** implemented (97%). The remaining 4 are webhook-token variants and GitHub webhook execution, which are server-to-server only.

## Files to Create

| File | Phase | Purpose |
|------|-------|---------|
| `screens/settings/AccountSettingsScreen.kt` | 1 | Account management |
| `screens/settings/MFASettingsScreen.kt` | 1 | MFA TOTP management |
| `screens/settings/server/ServerSettingsScreen.kt` | 2 | Server settings hub |
| `screens/settings/server/RoleManagementScreen.kt` | 2 | Role CRUD + permissions |
| `screens/settings/server/BanManagementScreen.kt` | 2 | Ban list management |
| `sheets/CreateChannelSheet.kt` | 2 | Create channel dialog |
| `composables/chat/GifPicker.kt` | 3 | GIF search/browse |
| `screens/settings/server/EmojiManagementScreen.kt` | 4 | Emoji CRUD |
| `screens/settings/BotManagementScreen.kt` | 5 | Bot management |
| `screens/settings/server/WebhookManagementScreen.kt` | 5 | Webhook management |

## API Route Files to Create/Modify

| File | Phase | New Endpoints |
|------|-------|---------------|
| `api/routes/auth/Account.kt` | 1 | 6 account management |
| `api/routes/auth/MFA.kt` | 1 | 7 MFA endpoints |
| `api/routes/auth/Sessions.kt` | 1 | 2 (edit, logout) |
| `api/routes/bots/Bots.kt` | 5 | 7 bot endpoints |
| `api/routes/channel/Webhooks.kt` | 5 | 4 webhook endpoints |
| `api/routes/microservices/gifbox/GifBox.kt` | 3 | 3 GIF endpoints |
| `api/routes/server/Server.kt` | 2 | 5 (invites, member query, role fetch, ranks, default perms) |
| `api/routes/channel/Channel.kt` | 4 | 2 (default perms, remove all reactions) |
| `api/routes/user/User.kt` | 3 | 3 (mutual, flags, default avatar) |
| `api/routes/push/Push.kt` | 1 | 1 (unsubscribe) |
