# Stoat / Revolt API — Comprehensive Endpoint Discovery

> Last updated: 2026-02-13
> Source: DNS enumeration, OpenAPI spec (v0.11.0), revolt-backend source, Android app routes, web frontend config

---

## 1. Domains & Subdomains

### stoat.chat (Primary — Cloudflare)
| Subdomain | IP / Target | Purpose |
|-----------|-------------|---------|
| `stoat.chat` | 172.67.200.144, 104.21.50.38 | Web app (Cloudflare) |
| `api.stoat.chat` | 104.21.50.38 | REST API (Delta, Rocket) — same as `stoat.chat/api` |
| `events.stoat.chat` | 104.21.50.38 | WebSocket gateway (Bonfire) |
| `beta.stoat.chat` | 172.67.200.144 | Beta web client |
| `static.stoat.chat` | 104.21.50.38 | Static assets (404 currently) |
| `status.stoat.chat` | 143.244.60.197 (Phare) | Status page |
| `support.stoat.chat` | Vercel | Support portal |
| `developers.stoat.chat` | stoatchat.github.io | Developer documentation (GitHub Pages) |

### revolt.chat (Legacy / Shared — Cloudflare)
| Subdomain | IP / Target | Purpose |
|-----------|-------------|---------|
| `revolt.chat` | 104.21.69.166, 172.67.210.140 | Web app |
| `api.revolt.chat` | 172.67.210.140 | REST API — **REDIRECTS to Stoat backend** |
| `ws.revolt.chat` | 104.21.69.166 | Legacy WebSocket (502 currently) |
| `app.revolt.chat` | 172.67.210.140 | Legacy web app |
| `beta.revolt.chat` | 172.67.210.140 | Beta web client |
| `admin.revolt.chat` | 172.67.210.140 | Admin panel (302 redirect) |
| `autumn.revolt.chat` | 172.67.210.140 | Legacy file server |
| `vortex.revolt.chat` | 65.21.249.22 (Hetzner) | Legacy voice server (prod-proxy-01.hel.revolt.wtf) |
| `voso.revolt.chat` | 51.15.123.10 | Legacy voice (Scaleway, timed out) |
| `static.revolt.chat` | 104.21.69.166 | Static assets |
| `sentry.revolt.chat` | 172.67.210.140 | Error tracking (Sentry) |
| `status.revolt.chat` | 143.244.60.197 (Phare) | Status page |
| `wiki.revolt.chat` | 172.67.210.140 | Wiki |
| `help.revolt.chat` | 172.67.210.140 | Help center (302 redirect) |
| `support.revolt.chat` | Vercel | Support portal |
| `developers.revolt.chat` | 104.21.69.166 | Developer docs (302 redirect) |

### stoatusercontent.com (CDN / Media — Cloudflare)
| Subdomain | IP / Target | Purpose |
|-----------|-------------|---------|
| `cdn.stoatusercontent.com` | 104.21.67.51 | File server (Autumn) — uploads, avatars, emojis, etc. |
| `proxy.stoatusercontent.com` | 104.21.67.51, 172.67.213.210 | Media proxy (January) — URL embeds & image proxy |

### stoatinternal.com (Infrastructure — Cloudflare)
| Subdomain | IP / Target | Purpose |
|-----------|-------------|---------|
| `admin.stoatinternal.com` | 172.67.212.35 | Internal admin panel |
| `monitor.stoatinternal.com` | 172.67.212.35 | Monitoring dashboard |
| `prod-proxy-01.hel.srv.stoatinternal.com` | 65.21.249.22 | Production proxy (Hetzner Helsinki) |

### Other Domains
| Domain | Purpose |
|--------|---------|
| `stt.gg` | Invite link shortener (redirects to stoat.chat/discover/servers) |
| `revolt.wtf` | Infrastructure domain (mail exchange mx2.revolt.wtf at 116.202.22.52) |
| `geo.revolt.chat` | Geolocation service |
| `health.revolt.chat` | Health check API |
| `01.hel-fi.voip.stoat.chat` | LiveKit voice node (Helsinki, via prod-proxy-01.hel.srv.stoatinternal.com) |

---

## 2. API URLs Used by Clients

### Android App (StoatAPI.kt)
```
STOAT_BASE       = https://api.stoat.chat/0.8
STOAT_FILES      = https://cdn.stoatusercontent.com
STOAT_PROXY      = https://proxy.stoatusercontent.com
STOAT_WEBSOCKET  = wss://events.stoat.chat
STOAT_WEB_APP    = https://stoat.chat
STOAT_SUPPORT    = https://support.stoat.chat
STOAT_INVITES    = https://stt.gg
STOAT_KJBOOK     = https://stoatchat.github.io/for-android
```

### Web Frontend (env.ts)
```
DEFAULT_API_URL   = https://stoat.chat/api
DEFAULT_WS_URL    = wss://stoat.chat/events
DEFAULT_MEDIA_URL = https://cdn.stoatusercontent.com
DEFAULT_PROXY_URL = https://proxy.stoatusercontent.com
```

### Alpha / Dev (currently unresolvable)
```
alpha.revolt.chat/api       (DNS does not resolve)
local.revolt.chat:14702     (dev environment)
local.revolt.chat:14703     (dev WebSocket)
local.revolt.chat:14704     (dev Autumn)
local.revolt.chat:14705     (dev January)
```

---

## 3. Authentication Methods

| Method | Header | Description |
|--------|--------|-------------|
| Session Token | `x-session-token` | Primary auth for API and WebSocket. Obtained from `POST /auth/session/login` |
| MFA Ticket (valid) | `x-mfa-ticket` | Used for MFA-protected operations after validation |
| MFA Ticket (unvalidated) | `x-mfa-ticket` | Used when creating MFA tickets |
| Bot Token | `x-bot-token` | Authentication for bot accounts |
| Webhook Token | URL path parameter | Token-based auth for webhook execution (no header needed) |

### WebSocket Authentication
Connect to `wss://events.stoat.chat` with query parameters:
- `?token=<session_token>` — authenticate on connect
- `?version=1` — protocol version
- `?format=json` or `?format=msgpack` — serialization format
- `?ready=users&ready=servers&ready=channels&ready=members&ready=emojis&ready=voice_states&ready=channel_unreads&ready=user_settings[key]&ready=policy_changes` — selective Ready payload

Or send `Authenticate` frame after connecting: `{"type": "Authenticate", "token": "<token>"}`

---

## 4. REST API Endpoints (Delta — 75 routes)

### Core
| Method | Path | Auth | Summary |
|--------|------|------|---------|
| GET | `/` | None | Query Node — returns server configuration (RevoltConfig) |
| GET | `/openapi.json` | None | OpenAPI 3.0 specification |

### Account (7 routes)
| Method | Path | Auth | Summary |
|--------|------|------|---------|
| GET | `/auth/account/` | Session | Fetch Account info |
| POST | `/auth/account/create` | None | Create Account (email, password, invite?, captcha?) |
| PATCH | `/auth/account/change/email` | Session | Change Email |
| PATCH | `/auth/account/change/password` | Session | Change Password |
| POST | `/auth/account/delete` | MFA+Session | Delete Account |
| PUT | `/auth/account/delete` | None | Confirm Account Deletion (token from email) |
| POST | `/auth/account/disable` | MFA+Session | Disable Account |
| POST | `/auth/account/reset_password` | None | Send Password Reset email |
| PATCH | `/auth/account/reset_password` | None | Complete Password Reset |
| POST | `/auth/account/reverify` | None | Resend Verification email |
| POST | `/auth/account/verify/{code}` | None | Verify Email with code |

### MFA (6 routes)
| Method | Path | Auth | Summary |
|--------|------|------|---------|
| GET | `/auth/mfa/` | Session | MFA Status |
| GET | `/auth/mfa/methods` | Session | Get MFA Methods |
| PUT | `/auth/mfa/ticket` | Session/Unvalidated | Create MFA ticket |
| POST | `/auth/mfa/recovery` | MFA+Session | Fetch Recovery Codes |
| PATCH | `/auth/mfa/recovery` | MFA+Session | Generate Recovery Codes |
| PUT | `/auth/mfa/totp` | Session | Enable TOTP 2FA |
| POST | `/auth/mfa/totp` | MFA+Session | Generate TOTP Secret |
| DELETE | `/auth/mfa/totp` | MFA+Session | Disable TOTP 2FA |

### Sessions (5 routes)
| Method | Path | Auth | Summary |
|--------|------|------|---------|
| POST | `/auth/session/login` | None | Login (returns session token or MFA challenge) |
| POST | `/auth/session/logout` | Session | Logout current session |
| GET | `/auth/session/all` | Session | Fetch all Sessions |
| DELETE | `/auth/session/all` | Session | Delete All Sessions (query: `revoke_self`) |
| DELETE | `/auth/session/{id}` | Session | Revoke specific Session |
| PATCH | `/auth/session/{id}` | Session | Edit Session (friendly name) |

### Onboarding (2 routes)
| Method | Path | Auth | Summary |
|--------|------|------|---------|
| GET | `/onboard/hello` | Session | Check Onboarding Status |
| POST | `/onboard/complete` | Session | Complete Onboarding (set username) |

### Users (11 routes)
| Method | Path | Auth | Summary |
|--------|------|------|---------|
| GET | `/users/@me` | Session | Fetch Self |
| PATCH | `/users/@me/username` | Session | Change Username |
| GET | `/users/{target}` | Session | Fetch User |
| PATCH | `/users/{target}` | Session | Edit User (display_name, avatar, status, profile, badges, flags) |
| GET | `/users/{target}/profile` | Session | Fetch User Profile |
| GET | `/users/{target}/default_avatar` | None | Fetch Default Avatar (PNG) |
| GET | `/users/{target}/flags` | None | Fetch User Flags |
| GET | `/users/{target}/mutual` | Session | Fetch Mutual Friends/Servers/Groups/DMs |
| GET | `/users/{target}/dm` | Session | Open Direct Message |
| GET | `/users/dms` | Session | Fetch all DM Channels |

### Relationships (5 routes)
| Method | Path | Auth | Summary |
|--------|------|------|---------|
| POST | `/users/friend` | Session | Send Friend Request (by username) |
| PUT | `/users/{target}/friend` | Session | Accept Friend Request |
| DELETE | `/users/{target}/friend` | Session | Deny/Remove Friend |
| PUT | `/users/{target}/block` | Session | Block User |
| DELETE | `/users/{target}/block` | Session | Unblock User |

### Channels (6 routes)
| Method | Path | Auth | Summary |
|--------|------|------|---------|
| GET | `/channels/{target}` | Session | Fetch Channel |
| PATCH | `/channels/{target}` | Session | Edit Channel |
| DELETE | `/channels/{target}` | Session | Close/Leave Channel (query: `leave_silently`) |
| POST | `/channels/{target}/invites` | Session | Create Invite |
| PUT | `/channels/{target}/permissions/default` | Session | Set Default Permission |
| PUT | `/channels/{target}/permissions/{role_id}` | Session | Set Role Permission |

### Groups (5 routes)
| Method | Path | Auth | Summary |
|--------|------|------|---------|
| POST | `/channels/create` | Session | Create Group (name, description, users, nsfw) |
| GET | `/channels/{target}/members` | Session | Fetch Group Members |
| PUT | `/channels/{group_id}/recipients/{member_id}` | Session | Add Member to Group |
| DELETE | `/channels/{target}/recipients/{member}` | Session | Remove Member from Group |

### Messaging (10 routes)
| Method | Path | Auth | Summary |
|--------|------|------|---------|
| GET | `/channels/{target}/messages` | Session | Fetch Messages (query: limit, before, after, sort, nearby, include_users) |
| POST | `/channels/{target}/messages` | Session | Send Message (header: Idempotency-Key) |
| GET | `/channels/{target}/messages/{msg}` | Session | Fetch single Message |
| PATCH | `/channels/{target}/messages/{msg}` | Session | Edit Message |
| DELETE | `/channels/{target}/messages/{msg}` | Session | Delete Message |
| DELETE | `/channels/{target}/messages/bulk` | Session | Bulk Delete Messages |
| POST | `/channels/{target}/search` | Session | Search Messages (body: query, pinned, limit, before, after, sort, include_users) |
| PUT | `/channels/{target}/ack/{message}` | Session | Acknowledge Message |
| POST | `/channels/{target}/messages/{msg}/pin` | Session | Pin Message |
| DELETE | `/channels/{target}/messages/{msg}/pin` | Session | Unpin Message |

#### Message Search Body (DataMessageSearch)
```json
{
  "query": "string (required for text search)",
  "pinned": "boolean (search pinned only; cannot combine with query)",
  "limit": "integer (max messages to return)",
  "before": "string (message ID, cursor backward)",
  "after": "string (message ID, cursor forward)",
  "sort": "Relevance | Latest | Oldest",
  "include_users": "boolean (include user/member objects)"
}
```

#### Message Sort Options
- `Relevance` — sort by text search relevance (default for search)
- `Latest` — newest first
- `Oldest` — oldest first

### Interactions (3 routes)
| Method | Path | Auth | Summary |
|--------|------|------|---------|
| PUT | `/channels/{target}/messages/{msg}/reactions/{emoji}` | Session | Add Reaction |
| DELETE | `/channels/{target}/messages/{msg}/reactions/{emoji}` | Session | Remove Reaction (query: user_id, remove_all) |
| DELETE | `/channels/{target}/messages/{msg}/reactions` | Session | Remove All Reactions |

### Servers (6 routes)
| Method | Path | Auth | Summary |
|--------|------|------|---------|
| POST | `/servers/create` | Session | Create Server |
| GET | `/servers/{target}` | Session | Fetch Server (query: include_channels) |
| PATCH | `/servers/{target}` | Session | Edit Server |
| DELETE | `/servers/{target}` | Session | Delete/Leave Server (query: leave_silently) |
| PUT | `/servers/{target}/ack` | Session | Mark Server As Read |
| POST | `/servers/{server}/channels` | Session | Create Channel in Server |

### Server Members (8 routes)
| Method | Path | Auth | Summary |
|--------|------|------|---------|
| GET | `/servers/{target}/members` | Session | Fetch Members (query: exclude_offline) |
| GET | `/servers/{target}/members/{member}` | Session | Fetch Member (query: roles) |
| PATCH | `/servers/{server}/members/{member}` | Session | Edit Member |
| DELETE | `/servers/{target}/members/{member}` | Session | Kick Member |
| PUT | `/servers/{server}/bans/{target}` | Session | Ban User |
| DELETE | `/servers/{server}/bans/{target}` | Session | Unban User |
| GET | `/servers/{target}/bans` | Session | Fetch Bans |
| GET | `/servers/{target}/invites` | Session | Fetch Invites |
| GET | `/servers/{target}/members_experimental_query` | Session | Query Members by Name (query: query, experimental_api=true) |

### Server Permissions / Roles (6 routes)
| Method | Path | Auth | Summary |
|--------|------|------|---------|
| POST | `/servers/{target}/roles` | Session | Create Role |
| GET | `/servers/{target}/roles/{role_id}` | Session | Fetch Role |
| PATCH | `/servers/{target}/roles/{role_id}` | Session | Edit Role |
| DELETE | `/servers/{target}/roles/{role_id}` | Session | Delete Role |
| PATCH | `/servers/{target}/roles/ranks` | Session | Edit Role Ranks/Positions |
| PUT | `/servers/{target}/permissions/default` | Session | Set Default Permission |
| PUT | `/servers/{target}/permissions/{role_id}` | Session | Set Role Permission |

### Server Customisation (1 route)
| Method | Path | Auth | Summary |
|--------|------|------|---------|
| GET | `/servers/{target}/emojis` | Session | Fetch Server Emoji |

### Emojis (3 routes)
| Method | Path | Auth | Summary |
|--------|------|------|---------|
| GET | `/custom/emoji/{emoji_id}` | None | Fetch Emoji |
| PUT | `/custom/emoji/{id}` | Session | Create New Emoji |
| DELETE | `/custom/emoji/{emoji_id}` | Session | Delete Emoji |

### Invites (3 routes)
| Method | Path | Auth | Summary |
|--------|------|------|---------|
| GET | `/invites/{target}` | None | Fetch Invite (public info) |
| POST | `/invites/{target}` | Session | Join Invite |
| DELETE | `/invites/{target}` | Session | Delete Invite |

### Bots (6 routes)
| Method | Path | Auth | Summary |
|--------|------|------|---------|
| GET | `/bots/@me` | Session | Fetch Owned Bots |
| POST | `/bots/create` | Session | Create Bot |
| GET | `/bots/{bot}` | Session | Fetch Bot |
| PATCH | `/bots/{target}` | Session | Edit Bot |
| DELETE | `/bots/{target}` | Session | Delete Bot |
| GET | `/bots/{target}/invite` | Session | Fetch Public Bot |
| POST | `/bots/{target}/invite` | Session | Invite Bot |

### Webhooks (9 routes)
| Method | Path | Auth | Summary |
|--------|------|------|---------|
| GET | `/channels/{channel_id}/webhooks` | Session | Get All Webhooks for channel |
| POST | `/channels/{target}/webhooks` | Session | Create Webhook |
| GET | `/webhooks/{webhook_id}` | Session | Get Webhook |
| PATCH | `/webhooks/{webhook_id}` | Session | Edit Webhook |
| DELETE | `/webhooks/{webhook_id}` | Session | Delete Webhook |
| GET | `/webhooks/{webhook_id}/{token}` | None | Get Webhook (token auth) |
| POST | `/webhooks/{webhook_id}/{token}` | None | Execute Webhook (token auth, header: Idempotency-Key) |
| PATCH | `/webhooks/{webhook_id}/{token}` | None | Edit Webhook (token auth) |
| DELETE | `/webhooks/{webhook_id}/{token}` | None | Delete Webhook (token auth) |
| POST | `/webhooks/{webhook_id}/{token}/github` | None | GitHub Webhook handler (header: X-Github-Event) |

### Voice (3 routes)
| Method | Path | Auth | Summary |
|--------|------|------|---------|
| POST | `/channels/{target}/join_call` | Session | Join Call (body: node, force_disconnect, recipients) |
| PUT | `/channels/{target}/end_ring/{target_user}` | Session | Stop Ring |

### Sync (3 routes)
| Method | Path | Auth | Summary |
|--------|------|------|---------|
| POST | `/sync/settings/fetch` | Session | Fetch Settings (body: list of keys) |
| POST | `/sync/settings/set` | Session | Set Settings (query: timestamp) |
| GET | `/sync/unreads` | Session | Fetch Unreads |

### Push Notifications (2 routes)
| Method | Path | Auth | Summary |
|--------|------|------|---------|
| POST | `/push/subscribe` | Session | Subscribe to Web Push (body: endpoint, p256dh, auth) |
| POST | `/push/unsubscribe` | Session | Unsubscribe from Web Push |

### Safety (1 route)
| Method | Path | Auth | Summary |
|--------|------|------|---------|
| POST | `/safety/report` | Session | Report Content (message/server/user + reason) |

### Policy (1 route)
| Method | Path | Auth | Summary |
|--------|------|------|---------|
| POST | `/policy/acknowledge` | Session | Acknowledge Policy Changes |

---

## 5. Autumn (File Server) — cdn.stoatusercontent.com

Version: 0.10.2

### Routes
| Method | Path | Auth | Summary |
|--------|------|------|---------|
| GET | `/` | None | Root — returns `{"autumn": "Hello, I am a file server!", "version": "0.10.2"}` |
| POST | `/{tag}` | Session/Bot | Upload file (multipart/form-data) |
| OPTIONS | `/{tag}` | None | CORS preflight |
| GET | `/{tag}/{file_id}` | None | Fetch preview (thumbnail/resized image, or redirect to full file) |
| GET | `/{tag}/{file_id}/{file_name}` | None | Fetch original file (Content-Disposition: attachment) |
| GET | `/{tag}/{file_id}/original` | None | Redirect to original file with correct filename |

### Upload Tags & Limits
| Tag | Max Size | Max Resolution | Allowed Types |
|-----|----------|---------------|---------------|
| `attachments` | 20 MB | any | Any file type |
| `avatars` | 4 MB | 40 MP or 10,000px | Image only |
| `backgrounds` | 6 MB | 40 MP or 10,000px | Image only |
| `icons` | 2.5 MB | 40 MP or 10,000px | Image only |
| `banners` | 6 MB | 40 MP or 10,000px | Image only |
| `emojis` | 500 KB | 40 MP or 10,000px | Image only |

### Preview Resolutions
| Tag | Preview Max | Animations Stripped |
|-----|------------|-------------------|
| `attachments` | 1280px on any axis | No |
| `avatars` | 128px on any axis | Yes |
| `backgrounds` | 1280x720px | No |
| `icons` | 128px on any axis | Yes |
| `banners` | 480px on any axis | No |
| `emojis` | 128px on any axis | No |

### Rate Limits
- Upload: 10 requests per 10-second window (per user per tag)
- Other routes: unlimited

### Response Headers
- `Cache-Control: public, max-age=604800, must-revalidate`
- `X-RateLimit-Limit`, `X-RateLimit-Bucket`, `X-RateLimit-Remaining`, `X-RateLimit-Reset-After`

---

## 6. January (Media Proxy) — proxy.stoatusercontent.com

Version: 0.7.18

### Routes
| Method | Path | Auth | Summary |
|--------|------|------|---------|
| GET | `/` | None | Root — returns `{"january": "Hello, I am a media proxy server!", "version": "0.7.18"}` |
| GET | `/proxy?url={url}` | None | Proxy a URL and serve the media file (Content-Disposition: inline) |
| GET | `/embed?url={url}` | API Key | Generate embed metadata for a URL |

### Cache
- `Cache-Control: public, max-age=600, immutable`

---

## 7. Gifbox (GIF Search) Service

### Routes
| Method | Path | Auth | Summary |
|--------|------|------|---------|
| GET | `/` | None | Root |
| GET | `/categories` | None | List GIF categories |
| GET | `/search` | None | Search GIFs (via Tenor API) |
| GET | `/trending` | None | Trending GIFs |

### Rate Limit Headers
- `X-RateLimit-Limit`, `X-RateLimit-Bucket`, `X-RateLimit-Remaining`, `X-RateLimit-Reset-After`

---

## 8. Other Microservices

### Geolocation — geo.revolt.chat
```
GET /?client=android  ->  {"countryCode": "US", "isAgeRestrictedGeo": false}
```

### Health Check — health.revolt.chat
```
GET /api/health  ->  {"version": "0.5.3-7", "poll_rate": 300000}
```

### Voice Ingress (LiveKit webhook receiver — internal)
```
POST /<node>  (LiveKit webhook events: participant_joined, participant_left, track_published, track_unpublished, track_muted, track_unmuted)
```

---

## 9. WebSocket Protocol (Bonfire)

### Connection
```
wss://events.stoat.chat?version=1&format=json&token=<session_token>
```

Query parameters:
- `version` — protocol version (integer, default: 1)
- `format` — `json` (default) or `msgpack`
- `token` — session token (or send Authenticate frame after connecting)
- `ready` — selective Ready payload fields (repeated param): `users`, `servers`, `channels`, `members`, `emojis`, `voice_states`, `channel_unreads`, `user_settings[key]`, `policy_changes`

### Client -> Server Messages (sendable)
| Type | Fields | Description |
|------|--------|-------------|
| `Authenticate` | `token: string` | Authenticate session |
| `BeginTyping` | `channel: string` | Start typing indicator |
| `EndTyping` | `channel: string` | Stop typing indicator |
| `Subscribe` | `server_id: string` | Subscribe to server member events (lazy loading) |
| `Ping` | `data: number\|bytes` | Keepalive ping (send every ~30s) |

### Server -> Client Events (receivable)
| Type | Key Fields | Description |
|------|------------|-------------|
| `Bulk` | `v: EventV1[]` | Multiple events batched |
| `Error` | `data: Error` | Error event |
| `Authenticated` | — | Successfully authenticated |
| `Logout` | — | Logged out (session revoked) |
| `Ready` | `users?, servers?, channels?, members?, emojis?, voice_states?, user_settings?, channel_unreads?, policy_changes?` | Initial state payload |
| `Pong` | `data: number\|bytes` | Ping response |
| `Message` | (full Message object) | New message |
| `MessageUpdate` | `id, channel, data, clear[]` | Message edited |
| `MessageAppend` | `id, channel, append{embeds}` | Embed added to message |
| `MessageDelete` | `id, channel` | Message deleted |
| `MessageReact` | `id, channel_id, user_id, emoji_id` | Reaction added |
| `MessageUnreact` | `id, channel_id, user_id, emoji_id` | Reaction removed by user |
| `MessageRemoveReaction` | `id, channel_id, emoji_id` | Reaction removed entirely |
| `BulkMessageDelete` | `channel, ids[]` | Multiple messages deleted |
| `ServerCreate` | `id, server, channels[], emojis[], voice_states[]` | Joined server |
| `ServerUpdate` | `id, data, clear[]` | Server edited |
| `ServerDelete` | `id` | Left/deleted server |
| `ServerMemberUpdate` | `id{server,user}, data, clear[]` | Member edited |
| `ServerMemberJoin` | `id, user, member` | User joined server |
| `ServerMemberLeave` | `id, user, reason` | User left/kicked/banned |
| `ServerRoleUpdate` | `id, role_id, data, clear[]` | Role created/edited |
| `ServerRoleDelete` | `id, role_id` | Role deleted |
| `ServerRoleRanksUpdate` | `id, ranks[]` | Role positions changed |
| `UserUpdate` | `id, data, clear[], event_id?` | User info changed |
| `UserRelationship` | `id, user` | Relationship changed |
| `UserSettingsUpdate` | `id, update` | Remote settings update |
| `UserPlatformWipe` | `user_id, flags` | User banned/deleted from platform |
| `EmojiCreate` | (full Emoji object) | New emoji |
| `EmojiDelete` | `id` | Emoji deleted |
| `ReportCreate` | (full Report object) | New report (admin) |
| `ChannelCreate` | (full Channel object) | New channel |
| `ChannelUpdate` | `id, data, clear[]` | Channel edited |
| `ChannelDelete` | `id` | Channel deleted |
| `ChannelGroupJoin` | `id, user` | User joined group |
| `ChannelGroupLeave` | `id, user` | User left group |
| `ChannelStartTyping` | `id, user` | User started typing |
| `ChannelStopTyping` | `id, user` | User stopped typing |
| `ChannelAck` | `id, user, message_id` | User acknowledged message |
| `WebhookCreate` | (full Webhook object) | Webhook created |
| `WebhookUpdate` | `id, data, remove[]` | Webhook edited |
| `WebhookDelete` | `id` | Webhook deleted |
| `Auth` | (AuthifierEvent) | Auth events (session deletion) |
| `VoiceChannelJoin` | `id, state{user,joined_at,...}` | User joined voice |
| `VoiceChannelLeave` | `id, user` | User left voice |
| `VoiceChannelMove` | `user, from, to, state` | User moved voice channels |
| `UserVoiceStateUpdate` | `id, channel_id, data` | Voice state changed (mute/camera) |
| `UserMoveVoiceChannel` | `node, from, to, token` | Client should move to different voice channel |

---

## 10. Rate Limits

### Delta (REST API) — 10-second sliding window
| Bucket | Limit (per 10s) | Applies To |
|--------|-----------------|------------|
| `user_edit` | 2 | PATCH `/users/{target}` |
| `users` | 20 | All other `/users/*` routes |
| `bots` | 10 | All `/bots/*` routes |
| `messaging` | 10 | POST `/channels/{id}/messages` (per channel) |
| `channels` | 15 | All other `/channels/{id}/*` routes (per channel) |
| `servers` | 5 | All `/servers/{id}/*` routes (per server) |
| `auth` | 15 | All `/auth/*` non-DELETE routes |
| `auth_delete` | 255 | DELETE `/auth/*` routes |
| `default_avatar` | 255 | GET `/users/{id}/default_avatar` |
| `swagger` | 100 | Swagger/OpenAPI spec routes |
| `safety` | 15 | Most `/safety/*` routes |
| `safety_report` | 3 | POST `/safety/report` |
| `any` (default) | 20 | Everything else |

### Autumn (File Server) — 10-second sliding window
| Bucket | Limit (per 10s) | Applies To |
|--------|-----------------|------------|
| `upload` | 10 | POST `/{tag}` (per tag) |
| `any` | unlimited | All GET routes |

### Response Headers (all services)
```
X-RateLimit-Limit: <max requests>
X-RateLimit-Bucket: <bucket name>
X-RateLimit-Remaining: <remaining requests>
X-RateLimit-Reset-After: <ms until reset>
```

### Rate Limited Response
```
HTTP 429 Too Many Requests
{"retry_after": <ms>}
```

---

## 11. Feature Limits (from config)

### Global Limits
| Limit | Value |
|-------|-------|
| Group size | 100 members |
| Message embeds | 5 |
| Message replies | 5 |
| Message reactions | 20 |
| Server emoji | 100 |
| Server roles | 200 |
| Server channels | 200 |
| New user threshold | 72 hours |
| Upload body limit | 20 MB |

### Per-User Limits (default / new_user)
| Limit | Default | New User |
|-------|---------|----------|
| Outgoing friend requests | 10 | 5 |
| Owned bots | 5 | 2 |
| Message length | 2000 chars | 2000 chars |
| Message attachments | 5 | 5 |
| Servers | 100 | 50 |
| Voice quality | 16000 Hz | 16000 Hz |
| Video enabled | Yes | Yes |
| Video resolution | 1080x720 | 1080x720 |
| Video aspect ratio | 0.3 - 2.5 | 0.3 - 2.5 |

---

## 12. Push Notification System (pushd)

### Supported Platforms
- **VAPID** (Web Push) — queue: `notifications.outbound.vapid`
- **FCM** (Firebase Cloud Messaging / Android) — queue: `notifications.outbound.fcm`
- **APN** (Apple Push Notification) — queue: `notifications.outbound.apn`

### Notification Queues (RabbitMQ)
| Queue | Purpose |
|-------|---------|
| `notifications.origin.message` | Regular message notifications |
| `notifications.origin.mass_mention` | @everyone / @role mentions |
| `notifications.ingest.fr_accepted` | Friend request accepted |
| `notifications.ingest.fr_received` | Friend request received |
| `notifications.ingest.dm_call` | DM voice call |
| `notifications.ingest.generic` | Generic notifications (title + body) |
| `notifications.process.ack` | Badge updates (Apple) |

---

## 13. Infrastructure Notes

- **Cloudflare** proxies all public domains (stoat.chat, revolt.chat, stoatusercontent.com, stoatinternal.com)
- **Hetzner Helsinki** hosts the production proxy (65.21.249.22)
- **Scaleway** previously hosted voice (51.15.123.10, mail relay)
- **MongoDB** for data storage
- **Redis** for pub/sub (WebSocket event distribution, presence, caching)
- **RabbitMQ** for push notification queuing
- **S3-compatible storage** (MinIO in dev, Backblaze B2 in prod) for file storage
- **LiveKit** for voice/video calls
- **ClamAV** for virus scanning uploads
- **hCaptcha** for account creation captcha (sitekey: `3daae85e-09ab-4ff6-9f24-e8f4f335e433`)
- **Sentry** for error tracking
- **Phare** for status page monitoring

---

## 14. Port Scan Results

### api.stoat.chat (Cloudflare)
- 80/tcp (HTTP), 443/tcp (HTTPS), 8080/tcp (HTTP-proxy), 8443/tcp (HTTPS-alt)

### cdn.stoatusercontent.com (Cloudflare)
- 80/tcp (HTTP), 443/tcp (HTTPS), 8080/tcp (HTTP-proxy), 8443/tcp (HTTPS-alt)

### 65.21.249.22 (prod-proxy, Hetzner)
- 80/tcp (HTTP), 443/tcp (HTTPS)
