# Revolt Backend API Reference (Exhaustive)

Source: `/data/data/com.termux/files/home/git/revolt-backend/`
Generated from Rust source code analysis of all route handlers, model structs, and WebSocket events.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Authentication](#authentication)
3. [Rate Limits](#rate-limits)
4. [Core / Root](#core--root)
5. [Users](#users)
6. [Channels](#channels)
7. [Servers](#servers)
8. [Server Members](#server-members)
9. [Server Bans](#server-bans)
10. [Server Invites (from server)](#server-invites-from-server)
11. [Server Roles](#server-roles)
12. [Server Permissions](#server-permissions)
13. [Server Emoji](#server-emoji)
14. [Bots](#bots)
15. [Invites](#invites)
16. [Customisation (Emoji)](#customisation-emoji)
17. [Safety](#safety)
18. [Onboarding](#onboarding)
19. [Policy](#policy)
20. [Push Notifications](#push-notifications)
21. [Sync / Settings](#sync--settings)
22. [Webhooks](#webhooks)
23. [Auth (Authifier)](#auth-authifier)
24. [Autumn (File Upload Service)](#autumn-file-upload-service)
25. [January (Proxy / Embed Service)](#january-proxy--embed-service)
26. [GifBox (GIF Search Service)](#gifbox-gif-search-service)
27. [WebSocket (Bonfire)](#websocket-bonfire)
28. [Enums & Constants](#enums--constants)

---

## Architecture Overview

The Revolt backend consists of separate services:

| Service | Framework | Purpose |
|---------|-----------|---------|
| **Delta** | Rocket (Rust) | Main REST API |
| **Bonfire** | async-tungstenite | WebSocket gateway |
| **Autumn** | Axum (Rust) | File upload / CDN |
| **January** | Axum (Rust) | Media proxy / embed generation |
| **GifBox** | Axum (Rust) | GIF search (Tenor backend) |

All Delta routes are mounted at both `/` and `/0.8` prefix.
Source: `crates/delta/src/routes/mod.rs`

---

## Authentication

Delta uses session-based auth via the `rocket_authifier` crate.

**Headers:**
- `x-session-token: <session_token>` -- for user sessions
- `x-bot-token: <bot_token>` -- for bot accounts

Autumn/GifBox also accept these headers (extracted by User guard).

Auth routes are provided by `rocket_authifier::routes::{account, session, mfa}` mounted at `/auth/account`, `/auth/session`, `/auth/mfa`.

---

## Rate Limits

Source: `crates/delta/src/util/ratelimits.rs` (lines 1-81)

Rate limits use a 10-second sliding window per bucket per user. When exceeded, returns HTTP 429 with `retry_after` in milliseconds.

**Response Headers** (Autumn/GifBox expose these; Delta uses Rocket guard):
- `X-RateLimit-Limit`
- `X-RateLimit-Bucket`
- `X-RateLimit-Remaining`
- `X-RateLimit-Reset-After`

### Bucket Configuration

| Bucket | Limit (per 10s) | Applies To |
|--------|-----------------|------------|
| `user_edit` | 2 | `PATCH /users/<target>` |
| `users` | 20 | All other `/users/*` |
| `bots` | 10 | All `/bots/*` |
| `messaging` | 10 | `POST /channels/<id>/messages` (per channel) |
| `channels` | 15 | All other `/channels/<id>/*` (per channel) |
| `servers` | 5 | All `/servers/<id>/*` (per server) |
| `auth` | 15 | All `/auth/*` (non-DELETE) |
| `auth_delete` | 255 | `DELETE /auth/*` |
| `default_avatar` | 255 | `GET /users/<id>/default_avatar` |
| `swagger` | 100 | `/swagger/*` |
| `safety` | 15 | All `/safety/*` (non-report) |
| `safety_report` | 3 | `POST /safety/report` |
| `any` (fallback) | 20 | Everything else |

---

## Core / Root

### GET /
**File:** `crates/delta/src/routes/root.rs:96`
**Auth:** None
**Response:** `RevoltConfig`
```json
{
  "revolt": "0.7.16",
  "features": {
    "captcha": { "enabled": bool, "key": "hcaptcha_sitekey" },
    "email": bool,
    "invite_only": bool,
    "autumn": { "enabled": bool, "url": "https://autumn.revolt.chat" },
    "january": { "enabled": bool, "url": "https://jan.revolt.chat" },
    "livekit": {
      "enabled": bool,
      "nodes": [{ "name": "...", "lat": 0.0, "lon": 0.0, "public_url": "..." }]
    }
  },
  "ws": "wss://ws.revolt.chat",
  "app": "https://app.revolt.chat",
  "vapid": "<public_key>",
  "build": {
    "commit_sha": "...",
    "commit_timestamp": "...",
    "semver": "...",
    "origin_url": "...",
    "timestamp": "..."
  }
}
```

---

## Users

### GET /users/@me
**File:** `crates/delta/src/routes/users/fetch_self.rs`
**Auth:** Required
**Response:** `User` (with relationship=`User` for self)

### GET /users/{target}
**File:** `crates/delta/src/routes/users/fetch_user.rs`
**Auth:** Required
**Permission:** `UserPermission::Access`
**Response:** `User`

### GET /users/{target}/flags
**File:** `crates/delta/src/routes/users/fetch_user_flags.rs`
**Auth:** Required
**Response:** `FlagResponse`
```json
{ "flags": 0 }
```

### PATCH /users/{target}
**File:** `crates/delta/src/routes/users/edit_user.rs`
**Auth:** Required (self, or privileged for badges/flags)
**Body:** `DataEditUser`
```json
{
  "display_name": "string (2-32)",      // optional
  "avatar": "autumn_attachment_id",      // optional (1-128)
  "status": {                            // optional
    "text": "string (0-128)",
    "presence": "Online|Idle|Focus|Busy|Invisible"
  },
  "profile": {                           // optional
    "content": "string (0-2000)",
    "background": "autumn_attachment_id"
  },
  "badges": 0,                           // optional (privileged only)
  "flags": 0,                            // optional (privileged only)
  "remove": ["Avatar", "StatusText", "StatusPresence", "ProfileContent", "ProfileBackground", "DisplayName"]
}
```
**Response:** `User`

### PATCH /users/@me/username
**File:** `crates/delta/src/routes/users/change_username.rs:32`
**Auth:** Required + Account password
**Body:** `DataChangeUsername`
```json
{
  "username": "string (2-32, regex: ^(\\p{L}|[\\d_.-])+$)",
  "password": "string (8-1024)"
}
```
**Response:** `User`

### GET /users/{target}/default_avatar
**File:** `crates/delta/src/routes/users/get_default_avatar.rs`
**Auth:** None
**Response:** PNG image binary (Cache-Control: max-age=31536000)

### GET /users/{target}/profile
**File:** `crates/delta/src/routes/users/fetch_profile.rs`
**Auth:** Required
**Permission:** `UserPermission::ViewProfile`
**Response:** `UserProfile`
```json
{
  "content": "string or null",
  "background": { /* File object */ }
}
```

### GET /users/dms
**File:** `crates/delta/src/routes/users/fetch_dms.rs`
**Auth:** Required
**Response:** `Vec<Channel>` (all open DMs and groups)

### GET /users/{target}/dm
**File:** `crates/delta/src/routes/users/open_dm.rs`
**Auth:** Required
**Permission:** `UserPermission::SendMessage`
**Response:** `Channel` (creates DM if not exists)

### GET /users/{target}/mutual
**File:** `crates/delta/src/routes/users/find_mutual.rs`
**Auth:** Required
**Response:** `MutualResponse`
```json
{
  "users": ["user_id", ...],
  "servers": ["server_id", ...],
  "channels": ["channel_id", ...]
}
```

### PUT /users/{target}/friend
**File:** `crates/delta/src/routes/users/add_friend.rs`
**Auth:** Required
**Note:** Bots blocked. Accepts/sends friend request depending on current state.
**Response:** `User`

### DELETE /users/{target}/friend
**File:** `crates/delta/src/routes/users/remove_friend.rs`
**Auth:** Required
**Response:** `User`

### PUT /users/{target}/block
**File:** `crates/delta/src/routes/users/block_user.rs`
**Auth:** Required
**Response:** `User`

### DELETE /users/{target}/block
**File:** `crates/delta/src/routes/users/unblock_user.rs`
**Auth:** Required
**Response:** `User`

### POST /users/friend
**File:** `crates/delta/src/routes/users/send_friend_request.rs`
**Auth:** Required
**Note:** Bots blocked.
**Body:** `DataSendFriendRequest`
```json
{
  "username": "user#0000"
}
```
**Response:** `User`

---

## Channels

### GET /channels/{target}
**File:** `crates/delta/src/routes/channels/channel_fetch.rs`
**Auth:** Required
**Permission:** `ChannelPermission::ViewChannel`
**Response:** `Channel`

### PATCH /channels/{target}
**File:** `crates/delta/src/routes/channels/channel_edit.rs`
**Auth:** Required
**Permission:** `ChannelPermission::ManageChannel` (or group owner for owner transfer)
**Body:** `DataEditChannel`
```json
{
  "name": "string (1-32)",           // optional
  "description": "string (0-1024)",  // optional
  "owner": "user_id",                // optional (group owner transfer)
  "icon": "autumn_attachment_id",    // optional (1-128)
  "nsfw": bool,                      // optional
  "archived": bool,                  // optional
  "voice": {                         // optional
    "max_users": 0
  },
  "remove": ["Description", "Icon", "DefaultPermissions", "Voice"]
}
```
**Response:** `Channel`

### DELETE /channels/{target}
**File:** `crates/delta/src/routes/channels/channel_delete.rs`
**Auth:** Required
**Query:** `OptionsChannelDelete`
| Param | Type | Description |
|-------|------|-------------|
| `leave_silently` | bool? | Don't send leave message |
**Response:** Empty (204)

### PUT /channels/{target}/ack/{message}
**File:** `crates/delta/src/routes/channels/channel_ack.rs`
**Auth:** Required
**Note:** Bots blocked.
**Response:** Empty

### POST /channels/{target}/messages
**File:** `crates/delta/src/routes/channels/message_send.rs`
**Auth:** Required
**Permission:** `SendMessage` (+ `UploadFiles` for attachments, `SendEmbeds` for embeds, `Masquerade` + `ManageRole` for masquerade colour, `React` for interactions)
**Header:** `Idempotency-Key: <string (1-64)>` (optional, replaces deprecated `nonce` field)
**Body:** `DataMessageSend`
```json
{
  "nonce": "string (1-64)",         // deprecated, use Idempotency-Key header
  "content": "string (0-2000)",     // optional (at least one of content/attachments/embeds required)
  "attachments": ["attachment_id"], // optional
  "replies": [{                     // optional
    "id": "message_id",
    "mention": bool,
    "fail_if_not_exists": bool      // default: true
  }],
  "embeds": [{                      // optional (max 10 per message)
    "type": "Text",
    "icon_url": "...",
    "url": "...",
    "title": "...",
    "description": "...",
    "media": "autumn_attachment_id",
    "colour": "..."
  }],
  "masquerade": {                   // optional
    "name": "string (1-32)",
    "avatar": "url",
    "colour": "css_colour"          // requires ManageRole
  },
  "interactions": {                 // optional
    "reactions": ["emoji_id"],
    "restrict_reactions": bool
  },
  "flags": 0                        // optional (bitfield: 1=SuppressNotifications)
}
```
**Note:** TRUST-0 restriction: users with accounts <12hrs old in public servers cannot @mention.
**Response:** `Message`

### GET /channels/{target}/messages
**File:** `crates/delta/src/routes/channels/message_query.rs`
**Auth:** Required
**Permission:** `ReadMessageHistory`
**Query:** `OptionsQueryMessages`
| Param | Type | Description |
|-------|------|-------------|
| `limit` | i64? | 1-100 (default varies) |
| `before` | string? | Message ID (26 chars) |
| `after` | string? | Message ID (26 chars) |
| `sort` | string? | `Latest` or `Oldest` (`Relevance` rejected) |
| `nearby` | string? | Message ID (ignores before/after/sort, takes limit/2 each side) |
| `include_users` | bool? | Include User and Member objects |
**Response:** `BulkMessageResponse` (if `include_users`=true, includes `users` and `members` arrays alongside `messages`)

### POST /channels/{target}/search
**File:** `crates/delta/src/routes/channels/message_search.rs`
**Auth:** Required
**Permission:** `ReadMessageHistory`
**Note:** Bots blocked. Cannot combine `query` + `pinned`.
**Body:** `DataMessageSearch`
```json
{
  "query": "string (1-64)",         // optional (MongoDB text search)
  "pinned": bool,                   // optional (cannot use with query)
  "limit": 100,                     // optional (1-100)
  "before": "message_id",           // optional (26 chars)
  "after": "message_id",            // optional (26 chars)
  "sort": "Latest|Oldest|Relevance", // default: Latest
  "include_users": bool             // optional
}
```
**Response:** `BulkMessageResponse`

### GET /channels/{target}/messages/{msg}
**File:** `crates/delta/src/routes/channels/message_fetch.rs`
**Auth:** Required
**Response:** `Message`

### PATCH /channels/{target}/messages/{msg}
**File:** `crates/delta/src/routes/channels/message_edit.rs`
**Auth:** Required (must be message author)
**Body:** `DataEditMessage`
```json
{
  "content": "string (1-2000)",     // optional
  "embeds": [{...}]                 // optional (max 10)
}
```
**Response:** `Message`

### DELETE /channels/{target}/messages/{msg}
**File:** `crates/delta/src/routes/channels/message_delete.rs`
**Auth:** Required (own messages, or `ManageMessages` for others)
**Response:** Empty

### DELETE /channels/{target}/messages/bulk
**File:** `crates/delta/src/routes/channels/message_bulk_delete.rs`
**Auth:** Required
**Permission:** `ManageMessages` (always required)
**Note:** Messages must be <7 days old.
**Body:** `OptionsBulkDelete`
```json
{
  "ids": ["message_id", ...]        // 1-100 IDs
}
```
**Response:** Empty

### POST /channels/{target}/messages/{msg}/pin
**File:** `crates/delta/src/routes/channels/message_pin.rs`
**Auth:** Required
**Permission:** `ManageMessages` (except in DMs)
**Response:** Empty

### DELETE /channels/{target}/messages/{msg}/pin
**File:** `crates/delta/src/routes/channels/message_unpin.rs`
**Auth:** Required
**Permission:** `ManageMessages` (except in DMs)
**Response:** Empty

### PUT /channels/{target}/messages/{msg}/reactions/{emoji}
**File:** `crates/delta/src/routes/channels/message_react.rs`
**Auth:** Required
**Permission:** `React`
**Response:** Empty

### DELETE /channels/{target}/messages/{msg}/reactions/{emoji}
**File:** `crates/delta/src/routes/channels/message_unreact.rs`
**Auth:** Required
**Query:** `OptionsUnreact`
| Param | Type | Description |
|-------|------|-------------|
| `user_id` | string? | Remove specific user's reaction (requires `ManageMessages`) |
| `remove_all` | bool? | Remove all of this emoji (requires `ManageMessages`) |
**Response:** Empty

### DELETE /channels/{target}/messages/{msg}/reactions
**File:** `crates/delta/src/routes/channels/message_clear_reactions.rs`
**Auth:** Required
**Permission:** `ManageMessages`
**Response:** Empty

### POST /channels/create
**File:** `crates/delta/src/routes/channels/group_create.rs`
**Auth:** Required
**Note:** Bots blocked. Users must be friends.
**Body:** `DataCreateGroup`
```json
{
  "name": "string (1-32)",
  "description": "string (0-1024)", // optional
  "icon": "autumn_attachment_id",   // optional
  "users": ["user_id", ...],        // 0-49 users, must be friends
  "nsfw": bool                      // optional
}
```
**Response:** `Channel`

### PUT /channels/{group_id}/recipients/{member_id}
**File:** `crates/delta/src/routes/channels/group_add_member.rs`
**Auth:** Required
**Permission:** `InviteOthers`
**Note:** Must be friends with the user being added.
**Response:** Empty

### DELETE /channels/{target}/recipients/{member}
**File:** `crates/delta/src/routes/channels/group_remove_member.rs`
**Auth:** Required (must be group owner)
**Response:** Empty

### GET /channels/{target}/members
**File:** `crates/delta/src/routes/channels/members_fetch.rs`
**Auth:** Required
**Note:** Groups only.
**Response:** `Vec<User>`

### POST /channels/{target}/invites
**File:** `crates/delta/src/routes/channels/invite_create.rs`
**Auth:** Required
**Permission:** `InviteOthers`
**Note:** TextChannel only.
**Response:** `Invite`

### POST /channels/{target}/join_call
**File:** `crates/delta/src/routes/channels/voice_join.rs`
**Auth:** Required
**Permission:** `Connect`
**Body:** `DataJoinCall`
```json
{
  "node": "string",                 // optional (voice server node name)
  "force_disconnect": bool,         // optional (disconnect other sessions first)
  "recipients": ["user_id", ...]    // optional (notify these users of call start)
}
```
**Response:** `CreateVoiceUserResponse`
```json
{
  "token": "livekit_token",
  "url": "wss://livekit.example.com"
}
```

### PUT /channels/{target}/end_ring/{target_user}
**File:** `crates/delta/src/routes/channels/voice_stop_ring.rs`
**Auth:** Required
**Note:** DM/Group only.
**Response:** Empty

### PUT /channels/{target}/permissions/{role_id}
**File:** `crates/delta/src/routes/channels/permissions_set.rs`
**Auth:** Required
**Permission:** `ManagePermissions`
**Note:** TextChannel only.
**Body:** `DataSetRolePermissions`
```json
{
  "permissions": {
    "allow": 0,
    "deny": 0
  }
}
```
**Response:** `Channel`

### PUT /channels/{target}/permissions/default
**File:** `crates/delta/src/routes/channels/permissions_set_default.rs`
**Auth:** Required
**Permission:** `ManagePermissions`
**Body:** `DataDefaultChannelPermissions`
```json
// For Groups:
{ "permissions": 0 }
// For TextChannels:
{ "permissions": { "allow": 0, "deny": 0 } }
```
**Response:** `Channel`

### POST /channels/{target}/webhooks
**File:** `crates/delta/src/routes/channels/webhook_create.rs`
**Auth:** Required
**Permission:** `ManageWebhooks`
**Body:** `CreateWebhookBody`
```json
{
  "name": "string (1-32)",
  "avatar": "autumn_attachment_id"  // optional (1-128)
}
```
**Response:** `Webhook`

### GET /channels/{channel_id}/webhooks
**File:** `crates/delta/src/routes/channels/webhook_fetch_all.rs`
**Auth:** Required
**Permission:** `ManageWebhooks`
**Response:** `Vec<Webhook>`

---

## Servers

### POST /servers/create
**File:** `crates/delta/src/routes/servers/server_create.rs`
**Auth:** Required
**Body:** `DataCreateServer`
```json
{
  "name": "string (1-32)",
  "description": "string (0-1024)", // optional
  "nsfw": bool                      // optional
}
```
**Response:** `CreateServerLegacyResponse`
```json
{
  "server": { /* Server */ },
  "channels": [{ /* Channel */ }]
}
```

### GET /servers/{target}
**File:** `crates/delta/src/routes/servers/server_fetch.rs`
**Auth:** Required
**Query:** `OptionsFetchServer`
| Param | Type | Description |
|-------|------|-------------|
| `include_channels` | bool? | Include channels in response |
**Response:** `FetchServerResponse` (untagged: either `Server` or `{...server, channels: [...]}`)

### PATCH /servers/{target}
**File:** `crates/delta/src/routes/servers/server_edit.rs`
**Auth:** Required
**Permission:** `ManageServer` (or `ManageChannel` for categories)
**Note:** `flags` and `discoverable` require privileged access.
**Body:** `DataEditServer`
```json
{
  "name": "string (1-32)",              // optional
  "description": "string (0-1024)",     // optional
  "icon": "autumn_attachment_id",       // optional
  "banner": "autumn_attachment_id",     // optional
  "categories": [{                      // optional
    "id": "string (1-32)",
    "title": "string (1-32)",
    "channels": ["channel_id"]
  }],
  "system_messages": {                  // optional
    "user_joined": "channel_id",
    "user_left": "channel_id",
    "user_kicked": "channel_id",
    "user_banned": "channel_id"
  },
  "flags": 0,                          // optional (privileged only)
  "discoverable": bool,                // optional (privileged only)
  "analytics": bool,                   // optional
  "remove": ["Description", "Categories", "SystemMessages", "Icon", "Banner"]
}
```
**Response:** `Server`

### DELETE /servers/{target}
**File:** `crates/delta/src/routes/servers/server_delete.rs`
**Auth:** Required
**Query:** `OptionsServerDelete`
| Param | Type | Description |
|-------|------|-------------|
| `leave_silently` | bool? | Don't send leave message |
**Note:** If owner, deletes server. Otherwise, leaves server.
**Response:** Empty

### PUT /servers/{target}/ack
**File:** `crates/delta/src/routes/servers/server_ack.rs`
**Auth:** Required
**Note:** Bots blocked.
**Response:** Empty

### POST /servers/{server}/channels
**File:** `crates/delta/src/routes/servers/channel_create.rs`
**Auth:** Required
**Permission:** `ManageChannel`
**Body:** `DataCreateServerChannel`
```json
{
  "type": "Text|Voice",             // default: Text
  "name": "string (1-32)",
  "description": "string (0-1024)", // optional
  "nsfw": bool,                     // optional
  "voice": {                        // optional
    "max_users": 0
  }
}
```
**Response:** `Channel`

---

## Server Members

### GET /servers/{target}/members
**File:** `crates/delta/src/routes/servers/member_fetch_all.rs`
**Auth:** Required
**Query:** `OptionsFetchAllMembers`
| Param | Type | Description |
|-------|------|-------------|
| `exclude_offline` | bool? | Exclude offline users |
**Response:** `AllMemberResponse`
```json
{
  "members": [{ /* Member */ }],
  "users": [{ /* User */ }]
}
```

### GET /servers/{target}/members/{member}
**File:** `crates/delta/src/routes/servers/member_fetch.rs`
**Auth:** Required
**Query:**
| Param | Type | Description |
|-------|------|-------------|
| `roles` | bool? | Include role objects |
**Response:** `MemberResponse` (untagged: `Member` or `{ member, roles: { role_id: Role } }`)

### PATCH /servers/{server}/members/{member}
**File:** `crates/delta/src/routes/servers/member_edit.rs`
**Auth:** Required
**Permission:** Various (see notes)
**Body:** `DataMemberEdit`
```json
{
  "nickname": "string (1-32)",          // optional (ManageNicknames for others, ChangeNickname for self)
  "avatar": "autumn_attachment_id",     // optional (ManageNicknames for others, ChangeAvatar for self)
  "roles": ["role_id", ...],           // optional (ManageRole, rank check)
  "timeout": "ISO8601 timestamp",      // optional (TimeoutMembers, must be <7 days, rank check)
  "can_publish": bool,                  // optional (MuteMembers)
  "can_receive": bool,                  // optional (DeafenMembers)
  "voice_channel": "channel_id",       // optional (MoveMembers, must be in voice already)
  "remove": ["Nickname", "Avatar", "Roles", "Timeout", "CanReceive", "CanPublish", "JoinedAt", "VoiceChannel"]
}
```
**Response:** `Member`

### DELETE /servers/{target}/members/{member}
**File:** `crates/delta/src/routes/servers/member_remove.rs`
**Auth:** Required
**Permission:** `KickMembers` (rank check applies)
**Response:** Empty

### GET /servers/{target}/members_experimental_query
**File:** `crates/delta/src/routes/servers/member_experimental_query.rs`
**Auth:** Required
**Note:** Unstable API. Max 10 results.
**Query:** `OptionsQueryMembers`
| Param | Type | Description |
|-------|------|-------------|
| `query` | string? | Username/nickname search |
| `experimental_api` | bool? | Must be true |
**Response:** `MemberQueryResponse`
```json
{
  "members": [{ /* Member */ }],
  "users": [{ /* User */ }]
}
```

---

## Server Bans

### PUT /servers/{server}/bans/{target}
**File:** `crates/delta/src/routes/servers/ban_create.rs`
**Auth:** Required
**Permission:** `BanMembers` (rank check applies)
**Body:** `DataBanCreate`
```json
{
  "reason": "string (0-1024)"       // optional
}
```
**Response:** `ServerBan`

### GET /servers/{target}/bans
**File:** `crates/delta/src/routes/servers/ban_list.rs`
**Auth:** Required
**Permission:** `BanMembers`
**Response:** `BanListResult`
```json
{
  "users": [{ "_id": "...", "username": "...", "discriminator": "...", "avatar": null }],
  "bans": [{ "_id": { "server": "...", "user": "..." }, "reason": "..." }]
}
```

### DELETE /servers/{server}/bans/{target}
**File:** `crates/delta/src/routes/servers/ban_remove.rs`
**Auth:** Required
**Permission:** `BanMembers`
**Response:** Empty

---

## Server Invites (from server)

### GET /servers/{target}/invites
**File:** `crates/delta/src/routes/servers/invites_fetch.rs`
**Auth:** Required
**Permission:** `ManageServer`
**Response:** `Vec<Invite>`

---

## Server Roles

### POST /servers/{target}/roles
**File:** `crates/delta/src/routes/servers/roles_create.rs`
**Auth:** Required
**Permission:** `ManageRole`
**Note:** Emoji/role limit enforced.
**Body:** `DataCreateRole`
```json
{
  "name": "string (1-32)",
  "rank": 0                         // deprecated, no effect
}
```
**Response:** `NewRoleResponse`
```json
{
  "id": "role_id",
  "role": { /* Role */ }
}
```

### PATCH /servers/{target}/roles/{role_id}
**File:** `crates/delta/src/routes/servers/roles_edit.rs`
**Auth:** Required
**Permission:** `ManageRole` (rank check applies)
**Body:** `DataEditRole`
```json
{
  "name": "string (1-32)",          // optional
  "colour": "css_colour (1-128)",   // optional (validated against colour regex)
  "hoist": bool,                    // optional
  "rank": 0,                        // deprecated, no effect
  "remove": ["Colour"]
}
```
**Response:** `Role`

### GET /servers/{target}/roles/{role_id}
**File:** `crates/delta/src/routes/servers/roles_fetch.rs`
**Auth:** Required
**Response:** `Role`

### DELETE /servers/{target}/roles/{role_id}
**File:** `crates/delta/src/routes/servers/roles_delete.rs`
**Auth:** Required
**Permission:** `ManageRole` (rank check applies)
**Response:** Empty

### PATCH /servers/{target}/roles/ranks
**File:** `crates/delta/src/routes/servers/roles_edit_positions.rs`
**Auth:** Required
**Permission:** `ManageRole` (rank check for non-owners)
**Body:** `DataEditRoleRanks`
```json
{
  "ranks": ["role_id_highest", "role_id_next", ...]
}
```
**Response:** `Server`

---

## Server Permissions

### PUT /servers/{target}/permissions/{role_id}
**File:** `crates/delta/src/routes/servers/permissions_set.rs`
**Auth:** Required
**Permission:** `ManagePermissions` (rank check applies)
**Body:** `DataSetServerRolePermission`
```json
{
  "permissions": {
    "allow": 0,
    "deny": 0
  }
}
```
**Response:** `Server`

### PUT /servers/{target}/permissions/default
**File:** `crates/delta/src/routes/servers/permissions_set_default.rs`
**Auth:** Required
**Permission:** `ManagePermissions`
**Body:** `DataPermissionsValue`
```json
{
  "permissions": 0
}
```
**Response:** `Server`

---

## Server Emoji

### GET /servers/{target}/emojis
**File:** `crates/delta/src/routes/servers/emoji_list.rs`
**Auth:** Required
**Response:** `Vec<Emoji>`

---

## Bots

### POST /bots/create
**File:** `crates/delta/src/routes/bots/create.rs`
**Auth:** Required
**Body:** `DataCreateBot`
```json
{
  "name": "string (2-32, username regex)"
}
```
**Response:** `BotWithUserResponse`
```json
{
  /* ...Bot fields */
  "user": { /* User */ }
}
```

### GET /bots/{bot}
**File:** `crates/delta/src/routes/bots/fetch.rs`
**Auth:** Required (must be owner)
**Response:** `FetchBotResponse`
```json
{
  "bot": { /* Bot */ },
  "user": { /* User */ }
}
```

### GET /bots/@me
**File:** `crates/delta/src/routes/bots/fetch_owned.rs`
**Auth:** Required
**Response:** `OwnedBotsResponse`
```json
{
  "bots": [{ /* Bot */ }],
  "users": [{ /* User */ }]
}
```

### GET /bots/{target}/invite
**File:** `crates/delta/src/routes/bots/fetch_public.rs`
**Auth:** None (if bot is public)
**Response:** `PublicBot`

### PATCH /bots/{target}
**File:** `crates/delta/src/routes/bots/edit.rs`
**Auth:** Required (must be owner)
**Body:** `DataEditBot`
```json
{
  "name": "string (2-32)",              // optional
  "public": bool,                       // optional
  "analytics": bool,                    // optional
  "interactions_url": "string (1-2048)",// optional
  "remove": ["InteractionsURL"]
}
```
**Response:** `BotWithUserResponse`

### DELETE /bots/{target}
**File:** `crates/delta/src/routes/bots/delete.rs`
**Auth:** Required (must be owner)
**Response:** Empty

### POST /bots/{target}/invite
**File:** `crates/delta/src/routes/bots/invite.rs`
**Auth:** Required
**Body:** `InviteBotDestination` (untagged)
```json
// To server:
{ "server": "server_id" }
// To group:
{ "group": "group_id" }
```
**Response:** Empty

---

## Invites

### GET /invites/{target}
**File:** `crates/delta/src/routes/invites/invite_fetch.rs`
**Auth:** None
**Response:** `InviteResponse` (tagged by `type`)
```json
// Server invite:
{
  "type": "Server",
  "code": "...",
  "server_id": "...",
  "server_name": "...",
  "server_icon": { /* File? */ },
  "server_banner": { /* File? */ },
  "server_flags": 0,
  "channel_id": "...",
  "channel_name": "...",
  "channel_description": "...",
  "user_name": "...",
  "user_avatar": { /* File? */ },
  "member_count": 0
}
// Group invite:
{
  "type": "Group",
  "code": "...",
  "channel_id": "...",
  "channel_name": "...",
  "channel_description": "...",
  "user_name": "...",
  "user_avatar": { /* File? */ }
}
```

### POST /invites/{target}
**File:** `crates/delta/src/routes/invites/invite_join.rs`
**Auth:** Required
**Note:** Bots blocked.
**Response:** `InviteJoinResponse` (tagged by `type`)
```json
// Server:
{ "type": "Server", "channels": [...], "server": {...} }
// Group:
{ "type": "Group", "channel": {...}, "users": [...] }
```

### DELETE /invites/{target}
**File:** `crates/delta/src/routes/invites/invite_delete.rs`
**Auth:** Required (creator or `ManageServer`)
**Response:** Empty

---

## Customisation (Emoji)

### PUT /custom/emoji/{id}
**File:** `crates/delta/src/routes/customisation/emoji_create.rs`
**Auth:** Required
**Permission:** `ManageCustomisation`
**Note:** Emoji count limit enforced per server.
**Body:** `DataCreateEmoji`
```json
{
  "name": "string (1-32, regex: ^[a-z0-9_]+$)",
  "parent": { "type": "Server", "id": "server_id" },
  "nsfw": false
}
```
**Response:** `Emoji`

### DELETE /custom/emoji/{emoji_id}
**File:** `crates/delta/src/routes/customisation/emoji_delete.rs`
**Auth:** Required (creator or `ManageCustomisation`)
**Response:** Empty

### GET /custom/emoji/{emoji_id}
**File:** `crates/delta/src/routes/customisation/emoji_fetch.rs`
**Auth:** None
**Response:** `Emoji`
```json
{
  "_id": "ulid",
  "parent": { "type": "Server", "id": "..." },
  "creator_id": "...",
  "name": "emoji_name",
  "animated": false,
  "nsfw": false
}
```

---

## Safety

### POST /safety/report
**File:** `crates/delta/src/routes/safety/report_content.rs:26`
**Auth:** Required
**Note:** Bots blocked. Cannot report yourself.
**Body:** `DataReportContent`
```json
{
  "content": {
    // One of:
    "type": "Message",
    "id": "message_id",
    "report_reason": "NoneSpecified|Illegal|IllegalGoods|IllegalExtortion|IllegalPornography|IllegalHacking|ExtremeViolence|PromotesHarm|UnsolicitedSpam|Raid|SpamAbuse|ScamsFraud|Malware|Harassment"
  },
  // OR:
  "content": {
    "type": "Server",
    "id": "server_id",
    "report_reason": "..." // same as Message reasons
  },
  // OR:
  "content": {
    "type": "User",
    "id": "user_id",
    "report_reason": "NoneSpecified|UnsolicitedSpam|SpamAbuse|InappropriateProfile|Impersonation|BanEvasion|Underage",
    "message_id": "optional_context_message_id"
  },
  "additional_context": "string (0-1000)"
}
```
**Response:** Empty

---

## Onboarding

### GET /onboard/hello
**File:** `crates/delta/src/routes/onboard/hello.rs`
**Auth:** Required (session)
**Response:** `DataHello`
```json
{ "onboarding": true }
```
Note: `onboarding: true` means the user still needs to complete onboarding (choose username).

### POST /onboard/complete
**File:** `crates/delta/src/routes/onboard/complete.rs:30`
**Auth:** Required (session, no user yet)
**Body:** `DataOnboard`
```json
{
  "username": "string (2-32, regex: ^(\\p{L}|[\\d_.-])+$)"
}
```
**Response:** `User`

---

## Policy

### POST /policy/acknowledge
**File:** `crates/delta/src/routes/policy/acknowledge_policy_changes.rs`
**Auth:** Required
**Body:** None
**Response:** Empty

---

## Push Notifications

### POST /push/subscribe
**File:** `crates/delta/src/routes/push/subscribe.rs`
**Auth:** Required (session)
**Body:** `WebPushSubscription` (from authifier crate)
```json
{
  "endpoint": "https://push-service.example.com/...",
  "p256dh": "base64_public_key",
  "auth": "base64_auth_secret"
}
```
**Response:** Empty

### POST /push/unsubscribe
**File:** `crates/delta/src/routes/push/unsubscribe.rs`
**Auth:** Required (session)
**Response:** Empty

---

## Sync / Settings

### POST /sync/settings/fetch
**File:** `crates/delta/src/routes/sync/get_settings.rs`
**Auth:** Required
**Body:** `OptionsFetchSettings`
```json
{
  "keys": ["theme", "locale", "notifications", ...]
}
```
**Response:** `UserSettings` -- `HashMap<String, [timestamp: i64, data: String]>`
```json
{
  "theme": [1234567890, "{\"accent\":\"#FD6671\"}"],
  "locale": [1234567890, "en"]
}
```

### POST /sync/settings/set
**File:** `crates/delta/src/routes/sync/set_settings.rs`
**Auth:** Required
**Query:** `OptionsSetSettings`
| Param | Type | Description |
|-------|------|-------------|
| `timestamp` | i64? | Settings change timestamp (to avoid feedback loops) |
**Body:** `HashMap<String, String>`
```json
{
  "theme": "{\"accent\":\"#FD6671\"}",
  "locale": "en"
}
```
**Response:** Empty

### GET /sync/unreads
**File:** `crates/delta/src/routes/sync/get_unreads.rs`
**Auth:** Required
**Response:** `Vec<ChannelUnread>`
```json
[{
  "_id": { "channel": "...", "user": "..." },
  "last_id": "message_id",
  "mentions": ["message_id", ...]
}]
```

---

## Webhooks

**Note:** Webhooks routes are conditionally registered based on `config.features.webhooks_enabled`.
Source: `crates/delta/src/routes/mod.rs`

### GET /webhooks/{webhook_id}
**File:** `crates/delta/src/routes/webhooks/webhook_fetch.rs`
**Auth:** Required (user) + `ViewChannel`
**Response:** `ResponseWebhook` (no token field)
```json
{
  "id": "...",
  "name": "...",
  "avatar": "file_id or null",
  "channel_id": "...",
  "permissions": 0
}
```

### GET /webhooks/{webhook_id}/{token}
**File:** `crates/delta/src/routes/webhooks/webhook_fetch_token.rs`
**Auth:** Token in URL
**Response:** `Webhook` (includes token)

### PATCH /webhooks/{webhook_id}
**File:** `crates/delta/src/routes/webhooks/webhook_edit.rs`
**Auth:** Required (user)
**Permission:** `ManageWebhooks`
**Body:** `DataEditWebhook`
```json
{
  "name": "string (1-32)",          // optional
  "avatar": "autumn_attachment_id", // optional (1-128)
  "permissions": 0,                 // optional (u64)
  "remove": ["Avatar"]
}
```
**Response:** `Webhook`

### PATCH /webhooks/{webhook_id}/{token}
**File:** `crates/delta/src/routes/webhooks/webhook_edit_token.rs:12`
**Auth:** Token in URL
**Body:** `DataEditWebhook` (same as above)
**Response:** `Webhook`

### DELETE /webhooks/{webhook_id}
**File:** `crates/delta/src/routes/webhooks/webhook_delete.rs:14`
**Auth:** Required (user)
**Permission:** `ManageWebhooks`
**Response:** Empty

### DELETE /webhooks/{webhook_id}/{token}
**File:** `crates/delta/src/routes/webhooks/webhook_delete_token.rs:10`
**Auth:** Token in URL
**Response:** Empty

### POST /webhooks/{webhook_id}/{token}
**File:** `crates/delta/src/routes/webhooks/webhook_execute.rs:17`
**Auth:** Token in URL
**Permission:** Webhook's permission bitfield must include `SendMessage` (+ `UploadFiles`, `SendEmbeds`, `Masquerade`, `React` as needed)
**Body:** `DataMessageSend` (same as message send)
**Response:** `Message`

### POST /webhooks/{webhook_id}/{token}/github
**File:** `crates/delta/src/routes/webhooks/webhook_execute_github.rs`
**Auth:** Token in URL
**Header:** `X-GitHub-Event: <event_type>`
**Body:** Raw GitHub webhook JSON
**Supported Events:** `star`, `ping`, `push`, `commit_comment`, `create`, `delete`, `discussion`, `discussion_comment`, `fork`, `issue_comment`, `issues`, `pull_request`
**Response:** Empty (200)

---

## Auth (Authifier)

Routes provided by external `rocket_authifier` crate, mounted at `/auth`.
These are NOT in the revolt-backend repo but are standard authifier endpoints:

### Account (`/auth/account`)
- `POST /auth/account/create` -- Create account
- `POST /auth/account/reverify` -- Resend verification
- `GET /auth/account/` -- Fetch account info
- `POST /auth/account/change/password` -- Change password
- `POST /auth/account/change/email` -- Change email
- `POST /auth/account/verify/:code` -- Verify email
- `POST /auth/account/reset_password` -- Send password reset
- `PATCH /auth/account/reset_password` -- Confirm password reset
- `DELETE /auth/account/delete` -- Disable account
- `POST /auth/account/delete` -- Schedule account deletion

### Session (`/auth/session`)
- `POST /auth/session/login` -- Login
- `GET /auth/session/all` -- Fetch all sessions
- `DELETE /auth/session/all` -- Revoke all sessions
- `DELETE /auth/session/:id` -- Revoke session
- `PATCH /auth/session/:id` -- Edit session

### MFA (`/auth/mfa`)
- `POST /auth/mfa/ticket` -- Create MFA ticket
- `GET /auth/mfa/methods` -- Fetch MFA methods
- `PUT /auth/mfa/totp` -- Enable TOTP
- `DELETE /auth/mfa/totp` -- Disable TOTP
- `PUT /auth/mfa/recovery` -- Generate recovery codes
- `GET /auth/mfa/recovery` -- Fetch recovery codes

---

## Autumn (File Upload Service)

Base URL: configured in `features.autumn.url`
Source: `crates/services/autumn/src/api.rs`

### GET /
**Auth:** None
**Response:**
```json
{ "autumn": "Hello, I am a file server!", "version": "..." }
```

### POST /{tag}
**File:** `crates/services/autumn/src/api.rs:173`
**Auth:** Required (session or bot token)
**Content-Type:** `multipart/form-data`
**Body:** Single file field named `file`

**Tags and Limits:**

| Tag | Max Size | Max Resolution | Allowed Types |
|-----|----------|---------------|---------------|
| `attachments` | 20 MB | -- | Any |
| `avatars` | 4 MB | 40 MP or 10,000px | Image only |
| `backgrounds` | 6 MB | 40 MP or 10,000px | Image only |
| `icons` | 2.5 MB | 40 MP or 10,000px | Image only |
| `banners` | 6 MB | 40 MP or 10,000px | Image only |
| `emojis` | 500 KB | 40 MP or 10,000px | Image only |

**Response:**
```json
{ "id": "attachment_id_string" }
```
**Notes:**
- Files are deduplicated by SHA-256 hash
- Metadata is stripped (EXIF, etc.)
- ClamAV virus scanning if configured
- Emoji IDs use ULID format; others use nanoid(42)

### GET /{tag}/{file_id}
**File:** `crates/services/autumn/src/api.rs:364`
**Auth:** None
**Note:** Returns image preview/thumbnail. Non-image files redirect to `/{tag}/{file_id}/{filename}`.

**Preview Resolutions:**

| Tag | Preview Resolution | Animations Stripped |
|-----|-------------------|-------------------|
| `attachments` | Up to 1280px on any axis | No |
| `avatars` | Up to 128px on any axis | Yes |
| `backgrounds` | Up to 1280x720px | No |
| `icons` | Up to 128px on any axis | Yes |
| `banners` | Up to 480px on any axis | No |
| `emojis` | Up to 128px on any axis | No |

**Response:** `image/webp` binary (Cache-Control: public, max-age=604800, must-revalidate)

### GET /{tag}/{file_id}/{file_name}
**File:** `crates/services/autumn/src/api.rs:432`
**Auth:** None
**Note:** Returns original file. Use `original` as file_name to get redirected to actual filename.
**Response:** Original file binary with `Content-Disposition: attachment`

---

## January (Proxy / Embed Service)

Base URL: configured in `features.january.url`
Source: `crates/services/january/src/api.rs`

### GET /
**Auth:** None
**Response:**
```json
{ "january": "Hello, I am a media proxy server!", "version": "..." }
```

### GET /proxy?url={url}
**File:** `crates/services/january/src/api.rs:60`
**Auth:** None
**Query:**
| Param | Type | Description |
|-------|------|-------------|
| `url` | string | URL to proxy |
**Response:** Proxied file content with original Content-Type, Content-Disposition: inline
**Cache:** `public, max-age=600, immutable`

### GET /embed?url={url}
**File:** `crates/services/january/src/api.rs:87`
**Auth:** API key (currently commented out in code)
**Query:**
| Param | Type | Description |
|-------|------|-------------|
| `url` | string | URL to generate embed for |
**Response:** `Embed` (returns error if `Embed::None`)

---

## GifBox (GIF Search Service)

Base URL: separate service
Source: `crates/services/gifbox/src/routes/`

### GET /
**Auth:** None
**Response:**
```json
{ "message": "Gifbox lives on!", "version": "..." }
```

### GET /categories
**File:** `crates/services/gifbox/src/routes/categories.rs`
**Auth:** Required (user or bot token)
**Query:**
| Param | Type | Description |
|-------|------|-------------|
| `locale` | string | User's locale (e.g. `en_US`) |
**Response:** `Vec<CategoryResponse>`

### GET /search
**File:** `crates/services/gifbox/src/routes/search.rs`
**Auth:** Required (user or bot token)
**Query:**
| Param | Type | Description |
|-------|------|-------------|
| `query` | string | Search query (e.g. "Wave") |
| `locale` | string | User's locale (e.g. `en_US`) |
| `limit` | u32? | Results to return (default: 50) |
| `is_category` | bool? | Searching within a category |
| `position` | string? | Pagination cursor (from `next` field) |
**Response:** `PaginatedMediaResponse`

### GET /trending
**File:** `crates/services/gifbox/src/routes/trending.rs`
**Auth:** Required (user or bot token)
**Note:** Mounted at `/trending` but OpenAPI path says `/featured`.
**Query:**
| Param | Type | Description |
|-------|------|-------------|
| `locale` | string | User's locale (e.g. `en_US`) |
| `limit` | u32? | Results to return (default: 50) |
| `position` | string? | Pagination cursor |
**Response:** `PaginatedMediaResponse`

---

## WebSocket (Bonfire)

Source: `crates/bonfire/src/websocket.rs`
Events: `crates/core/database/src/events/client.rs` and `server.rs`

Connect to the URL from `RevoltConfig.ws` (e.g. `wss://ws.revolt.chat`).

### Connection Parameters

Query parameters on WebSocket URL:
| Param | Values | Default |
|-------|--------|---------|
| `version` | `1` | `1` |
| `format` | `json`, `msgpack` | `json` |
| `token` | session/bot token | (authenticate via message) |

### Client -> Server Messages

Source: `crates/core/database/src/events/server.rs`

All messages are JSON with `"type"` field.

#### Authenticate
```json
{ "type": "Authenticate", "token": "session_or_bot_token" }
```
Not needed if token passed in connection URL query params.

#### BeginTyping
```json
{ "type": "BeginTyping", "channel": "channel_id" }
```

#### EndTyping
```json
{ "type": "EndTyping", "channel": "channel_id" }
```

#### Subscribe
```json
{ "type": "Subscribe", "server_id": "server_id" }
```

#### Ping
```json
{ "type": "Ping", "data": 12345, "responded": null }
```
`data` can be a number or binary array.

### Server -> Client Events

Source: `crates/core/database/src/events/client.rs:52-302`

All events are JSON with `"type"` field.

#### Authenticated
```json
{ "type": "Authenticated" }
```

#### Logout
```json
{ "type": "Logout" }
```

#### Ready
```json
{
  "type": "Ready",
  "users": [{ /* User */ }],           // optional
  "servers": [{ /* Server */ }],       // optional
  "channels": [{ /* Channel */ }],     // optional
  "members": [{ /* Member */ }],       // optional
  "emojis": [{ /* Emoji */ }],         // optional
  "voice_states": [{ /* ChannelVoiceState */ }], // optional
  "user_settings": { /* UserSettings */ }, // optional
  "channel_unreads": [{ /* ChannelUnread */ }], // optional
  "policy_changes": [{ /* PolicyChange */ }] // optional
}
```
Default ReadyPayloadFields: `users=true, servers=true, channels=true, members=true, emojis=true, voice_states=true, user_settings=[], channel_unreads=false, policy_changes=true`

#### Pong
```json
{ "type": "Pong", "data": 12345 }
```

#### Error
```json
{ "type": "Error", "data": { /* Error object */ } }
```

#### Message
```json
{ "type": "Message", ...message_fields }
```
Flattened Message object (not nested).

#### MessageUpdate
```json
{
  "type": "MessageUpdate",
  "id": "message_id",
  "channel": "channel_id",
  "data": { /* PartialMessage */ },
  "clear": ["Content", "Embeds", ...]
}
```

#### MessageAppend
```json
{
  "type": "MessageAppend",
  "id": "message_id",
  "channel": "channel_id",
  "append": { /* AppendMessage: embeds to add */ }
}
```

#### MessageDelete
```json
{ "type": "MessageDelete", "id": "message_id", "channel": "channel_id" }
```

#### MessageReact
```json
{
  "type": "MessageReact",
  "id": "message_id",
  "channel_id": "channel_id",
  "user_id": "user_id",
  "emoji_id": "emoji_id"
}
```

#### MessageUnreact
```json
{
  "type": "MessageUnreact",
  "id": "message_id",
  "channel_id": "channel_id",
  "user_id": "user_id",
  "emoji_id": "emoji_id"
}
```

#### MessageRemoveReaction
```json
{
  "type": "MessageRemoveReaction",
  "id": "message_id",
  "channel_id": "channel_id",
  "emoji_id": "emoji_id"
}
```

#### BulkMessageDelete
```json
{ "type": "BulkMessageDelete", "channel": "channel_id", "ids": ["msg_id", ...] }
```

#### ServerCreate
```json
{
  "type": "ServerCreate",
  "id": "server_id",
  "server": { /* Server */ },
  "channels": [{ /* Channel */ }],
  "emojis": [{ /* Emoji */ }],
  "voice_states": [{ /* ChannelVoiceState */ }]
}
```

#### ServerUpdate
```json
{
  "type": "ServerUpdate",
  "id": "server_id",
  "data": { /* PartialServer */ },
  "clear": ["Description", "Categories", "SystemMessages", "Icon", "Banner"]
}
```

#### ServerDelete
```json
{ "type": "ServerDelete", "id": "server_id" }
```

#### ServerMemberUpdate
```json
{
  "type": "ServerMemberUpdate",
  "id": { "server": "...", "user": "..." },
  "data": { /* PartialMember */ },
  "clear": ["Nickname", "Avatar", "Roles", "Timeout", "CanReceive", "CanPublish", "JoinedAt", "VoiceChannel"]
}
```

#### ServerMemberJoin
```json
{
  "type": "ServerMemberJoin",
  "id": "server_id",
  "user": "user_id",
  "member": { /* Member */ }
}
```
Note: `user` field is deprecated, use `member.id.user`.

#### ServerMemberLeave
```json
{
  "type": "ServerMemberLeave",
  "id": "server_id",
  "user": "user_id",
  "reason": "Leave|Kick|Ban"
}
```

#### ServerRoleUpdate
```json
{
  "type": "ServerRoleUpdate",
  "id": "server_id",
  "role_id": "role_id",
  "data": { /* PartialRole */ },
  "clear": ["Colour"]
}
```

#### ServerRoleDelete
```json
{ "type": "ServerRoleDelete", "id": "server_id", "role_id": "role_id" }
```

#### ServerRoleRanksUpdate
```json
{ "type": "ServerRoleRanksUpdate", "id": "server_id", "ranks": ["role_id", ...] }
```

#### UserUpdate
```json
{
  "type": "UserUpdate",
  "id": "user_id",
  "data": { /* PartialUser */ },
  "clear": ["Avatar", "StatusText", "StatusPresence", "ProfileContent", "ProfileBackground", "DisplayName"],
  "event_id": "optional_event_id"
}
```

#### UserRelationship
```json
{ "type": "UserRelationship", "id": "your_user_id", "user": { /* User with relationship */ } }
```

#### UserSettingsUpdate
```json
{
  "type": "UserSettingsUpdate",
  "id": "user_id",
  "update": { "key": [timestamp, "json_value"], ... }
}
```

#### UserPlatformWipe
```json
{ "type": "UserPlatformWipe", "user_id": "user_id", "flags": 0 }
```
Clients should remove: messages, DM channels, relationships, server memberships for this user.

#### EmojiCreate
```json
{ "type": "EmojiCreate", ...emoji_fields }
```

#### EmojiDelete
```json
{ "type": "EmojiDelete", "id": "emoji_id" }
```

#### ReportCreate
```json
{ "type": "ReportCreate", ...report_fields }
```

#### ChannelCreate
```json
{ "type": "ChannelCreate", ...channel_fields }
```

#### ChannelUpdate
```json
{
  "type": "ChannelUpdate",
  "id": "channel_id",
  "data": { /* PartialChannel */ },
  "clear": ["Description", "Icon", "DefaultPermissions", "Voice"]
}
```

#### ChannelDelete
```json
{ "type": "ChannelDelete", "id": "channel_id" }
```

#### ChannelGroupJoin
```json
{ "type": "ChannelGroupJoin", "id": "channel_id", "user": "user_id" }
```

#### ChannelGroupLeave
```json
{ "type": "ChannelGroupLeave", "id": "channel_id", "user": "user_id" }
```

#### ChannelStartTyping
```json
{ "type": "ChannelStartTyping", "id": "channel_id", "user": "user_id" }
```

#### ChannelStopTyping
```json
{ "type": "ChannelStopTyping", "id": "channel_id", "user": "user_id" }
```

#### ChannelAck
```json
{ "type": "ChannelAck", "id": "channel_id", "user": "user_id", "message_id": "msg_id" }
```

#### WebhookCreate
```json
{ "type": "WebhookCreate", ...webhook_fields }
```

#### WebhookUpdate
```json
{
  "type": "WebhookUpdate",
  "id": "webhook_id",
  "data": { /* PartialWebhook */ },
  "remove": ["Avatar"]
}
```

#### WebhookDelete
```json
{ "type": "WebhookDelete", "id": "webhook_id" }
```

#### Auth
```json
{ "type": "Auth", /* AuthifierEvent (session invalidation, etc.) */ }
```

#### VoiceChannelJoin
```json
{
  "type": "VoiceChannelJoin",
  "id": "channel_id",
  "state": { "id": "user_id", "joined_at": "...", "is_receiving": true, "is_publishing": true }
}
```

#### VoiceChannelLeave
```json
{ "type": "VoiceChannelLeave", "id": "channel_id", "user": "user_id" }
```

#### VoiceChannelMove
```json
{
  "type": "VoiceChannelMove",
  "user": "user_id",
  "from": "channel_id",
  "to": "channel_id",
  "state": { /* UserVoiceState */ }
}
```

#### UserVoiceStateUpdate
```json
{
  "type": "UserVoiceStateUpdate",
  "id": "user_id",
  "channel_id": "channel_id",
  "data": { /* PartialUserVoiceState */ }
}
```

#### UserMoveVoiceChannel
```json
{
  "type": "UserMoveVoiceChannel",
  "node": "node_name",
  "from": "channel_id",
  "to": "channel_id",
  "token": "livekit_token"
}
```
Sent to the user being moved. Contains new token for the destination channel.

#### Bulk
```json
{ "type": "Bulk", "v": [{ /* EventV1 */ }, ...] }
```

---

## Enums & Constants

### Channel Types (tagged by `channel_type`)
- `SavedMessages` -- Personal notes channel
- `DirectMessage` -- 1:1 DM
- `Group` -- Group DM (1-50 participants)
- `TextChannel` -- Server text channel
- `VoiceChannel` -- Server voice channel

### MessageSort
- `Relevance` -- Text search relevance (search only)
- `Latest` -- Newest first (default)
- `Oldest` -- Oldest first

### MessageFlags (bitfield)
- `1` -- SuppressNotifications

### User Presence
- `Online`, `Idle`, `Focus`, `Busy`, `Invisible`

### Relationship Status
- `None`, `User`, `Friend`, `Outgoing`, `Incoming`, `Blocked`, `BlockedOther`

### FieldsUser (removable fields)
- `Avatar`, `StatusText`, `StatusPresence`, `ProfileContent`, `ProfileBackground`, `DisplayName`

### FieldsServer (removable fields)
- `Description`, `Categories`, `SystemMessages`, `Icon`, `Banner`

### FieldsChannel (removable fields)
- `Description`, `Icon`, `DefaultPermissions`, `Voice`

### FieldsMember (removable fields)
- `Nickname`, `Avatar`, `Roles`, `Timeout`, `CanReceive`, `CanPublish`, `JoinedAt`, `VoiceChannel`

### FieldsRole (removable fields)
- `Colour`

### FieldsMessage (clearable fields)
- `Content`, `Embeds`

### FieldsWebhook (removable fields)
- `Avatar`

### FieldsBot (removable fields)
- `InteractionsURL`

### ChannelPermission (bitfield)
Key permissions used in route guards:
- `ViewChannel`, `ReadMessageHistory`, `SendMessage`, `ManageMessages`
- `ManageChannel`, `ManagePermissions`, `ManageWebhooks`
- `InviteOthers`, `SendEmbeds`, `UploadFiles`
- `React`, `Masquerade`, `Connect`
- `ManageRole` (for masquerade colour)

### UserPermission (bitfield)
- `Access` -- Can see user
- `ViewProfile` -- Can view profile
- `SendMessage` -- Can open DM

### RemovalIntention
- `Leave`, `Kick`, `Ban`

---

## Endpoint Count Summary

| Category | Count |
|----------|-------|
| Users | 15 |
| Channels | 23 |
| Servers | 6 |
| Server Members | 5 |
| Server Bans | 3 |
| Server Invites | 1 |
| Server Roles | 5 |
| Server Permissions | 2 |
| Server Emoji | 1 |
| Bots | 7 |
| Invites | 3 |
| Customisation | 3 |
| Safety | 1 |
| Onboarding | 2 |
| Policy | 1 |
| Push | 2 |
| Sync | 3 |
| Webhooks | 8 |
| Auth (authifier) | ~12 |
| Autumn | 4 |
| January | 3 |
| GifBox | 4 |
| **TOTAL** | **~113 HTTP + WebSocket** |

WebSocket events: 5 client-to-server message types, 38 server-to-client event types.
