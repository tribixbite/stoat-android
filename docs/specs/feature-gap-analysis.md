# Feature Gap Analysis — Android vs Web/Desktop vs API

## Overview

Comprehensive comparison of Stoat Android client features against the web client (SolidJS), desktop client (Electron), Revolt backend API (121 endpoints from OpenAPI v0.11.0), and microservices (Autumn, January, GifBox).

## Sources Analyzed

| Source | Description |
|--------|-------------|
| `stoatchat/for-web` | Stoat web client (SolidJS + TypeScript) |
| `stoatchat/for-desktop` | Stoat desktop client (Electron wrapper) |
| `revoltchat/frontend` | Upstream Revolt web frontend |
| `revoltchat/backend` | Revolt backend API (Rust/Rocket) |
| `revoltchat/revite` | Legacy Revolt web client (React) |
| OpenAPI spec `/openapi.json` | 121 endpoints, v0.11.0 |
| Autumn CDN service | File upload/download (6 tags: attachments, avatars, backgrounds, icons, banners, emojis) |
| January proxy service | Media proxy (`/proxy?url=`) and embed generation (`/embed?url=`) |
| GifBox service | GIF search (`/search`, `/trending`, `/categories`) |
| Geo service | `geo.revolt.chat` — age restriction check |
| Health service | `health.revolt.chat/api/health` — service status |

## Infrastructure

| Domain | Purpose | Status |
|--------|---------|--------|
| `api.stoat.chat` | Main API (v0.11.0) | Active, identical to api.revolt.chat |
| `events.stoat.chat` | WebSocket events | Active |
| `cdn.stoatusercontent.com` | Autumn file CDN | Active |
| `proxy.stoatusercontent.com` | January media proxy | Active |
| `beta.stoat.chat` | Web app | Active |
| `01.hel-fi.voip.stoat.chat` | LiveKit voice/video | Active |
| `admin.stoatinternal.com` | Admin panel (SSO) | Active, not public API |
| `old-admin.stoatinternal.com` | Legacy admin panel | Active |
| `geo.revolt.chat` | Geo/age restriction | Active |
| `health.revolt.chat` | Service health | Active |

## Android Implementation Status

**Total OpenAPI endpoints: 121**
**Implemented in Android: 65** (54%)
**Missing from Android: 56** (46%)

### Implemented Endpoints (65)

| Category | Count | Endpoints |
|----------|-------|-----------|
| Auth/Session | 5 | login, create account, list sessions, delete session, delete all sessions |
| Channel | 14 | fetch, edit, delete, messages CRUD, ack, members, invites, search |
| Channel Messages | 8 | send, edit, delete, fetch, pin, unpin, bulk delete, reactions |
| Group DM | 3 | create, add member, remove member |
| Server | 17 | create, fetch, edit, delete, ack, members, kick, ban, unban, bans, edit member, create channel, roles CRUD, permissions |
| User | 9 | fetch self, edit self, fetch user, profile, DM, block/unblock, friend/unfriend |
| Sync | 3 | fetch settings, set settings, unreads |
| Other | 6 | onboard, push subscribe, report, invites, emoji fetch, root, voice join |

### Missing Endpoints (56) — Implementable Client-Side

#### Account Management (11 endpoints)
| Endpoint | Priority | Notes |
|----------|----------|-------|
| `GET /auth/account/` | High | Fetch account info (email) |
| `PATCH /auth/account/change/email` | High | Change email |
| `PATCH /auth/account/change/password` | High | Change password |
| `POST /auth/account/delete` | Medium | Delete account |
| `PUT /auth/account/delete` | Medium | Confirm deletion |
| `POST /auth/account/disable` | Low | Disable account |
| `PATCH /auth/account/reset_password` | Low | Used in flow |
| `POST /auth/account/reset_password` | Low | Send reset email |
| `POST /auth/account/reverify` | Low | Resend verification |
| `POST /auth/account/verify/{code}` | Low | Verify email |
| `PATCH /users/@me/username` | High | Change username |

#### MFA Management (7 endpoints)
| Endpoint | Priority | Notes |
|----------|----------|-------|
| `GET /auth/mfa/` | Medium | Check MFA status |
| `GET /auth/mfa/methods` | Medium | Get available MFA methods |
| `PUT /auth/mfa/ticket` | Medium | Create MFA ticket |
| `POST /auth/mfa/totp` | Medium | Generate TOTP secret |
| `PUT /auth/mfa/totp` | Medium | Enable TOTP |
| `DELETE /auth/mfa/totp` | Medium | Disable TOTP |
| `PATCH /auth/mfa/recovery` | Medium | Generate recovery codes |
| `POST /auth/mfa/recovery` | Medium | Fetch recovery codes |

#### Session Management (2 endpoints)
| Endpoint | Priority | Notes |
|----------|----------|-------|
| `PATCH /auth/session/{id}` | Medium | Edit/rename session |
| `POST /auth/session/logout` | Low | Explicit logout (vs delete) |

#### Bot Management (7 endpoints)
| Endpoint | Priority | Notes |
|----------|----------|-------|
| `GET /bots/@me` | Low | List owned bots |
| `POST /bots/create` | Low | Create bot |
| `GET /bots/{bot}` | Low | Fetch bot |
| `PATCH /bots/{target}` | Low | Edit bot |
| `DELETE /bots/{target}` | Low | Delete bot |
| `GET /bots/{target}/invite` | Low | Fetch public bot |
| `POST /bots/{target}/invite` | Low | Invite bot to server |

#### Webhook Management (9 endpoints)
| Endpoint | Priority | Notes |
|----------|----------|-------|
| `GET /channels/{id}/webhooks` | Low | List channel webhooks |
| `POST /channels/{id}/webhooks` | Low | Create webhook |
| `GET /webhooks/{id}` | Low | Get webhook |
| `PATCH /webhooks/{id}` | Low | Edit webhook |
| `DELETE /webhooks/{id}` | Low | Delete webhook |
| `GET /webhooks/{id}/{token}` | Low | Get webhook (token auth) |
| `PATCH /webhooks/{id}/{token}` | Low | Edit webhook (token auth) |
| `DELETE /webhooks/{id}/{token}` | Low | Delete webhook (token auth) |
| `POST /webhooks/{id}/{token}` | Low | Execute webhook |

#### Emoji Management (3 endpoints)
| Endpoint | Priority | Notes |
|----------|----------|-------|
| `PUT /custom/emoji/{id}` | Medium | Create emoji |
| `DELETE /custom/emoji/{id}` | Medium | Delete emoji |
| `GET /servers/{target}/emojis` | Medium | Fetch server emojis |

#### Server Management (5 endpoints)
| Endpoint | Priority | Notes |
|----------|----------|-------|
| `GET /servers/{target}/invites` | Medium | List server invites |
| `GET /servers/{target}/members_experimental_query` | High | Search members by name |
| `GET /servers/{target}/roles/{role_id}` | Low | Fetch single role |
| `PATCH /servers/{target}/roles/ranks` | Low | Reorder role ranks |
| `PUT /servers/{target}/permissions/default` | High | Set default perms |

#### Channel Management (2 endpoints)
| Endpoint | Priority | Notes |
|----------|----------|-------|
| `PUT /channels/{target}/permissions/default` | High | Set default channel perms |
| `DELETE /channels/{target}/messages/{msg}/reactions` | Medium | Remove all reactions |

#### User Features (5 endpoints)
| Endpoint | Priority | Notes |
|----------|----------|-------|
| `GET /users/dms` | Medium | List all DM channels |
| `GET /users/{target}/default_avatar` | Low | Fetch default avatar |
| `GET /users/{target}/flags` | Low | User flags (staff, etc.) |
| `GET /users/{target}/mutual` | Medium | Mutual friends/servers |
| `DELETE /invites/{target}` | Medium | Delete invite |

#### Misc (3 endpoints)
| Endpoint | Priority | Notes |
|----------|----------|-------|
| `POST /policy/acknowledge` | Low | Acknowledge policy |
| `POST /push/unsubscribe` | Medium | Unsubscribe push |
| `PUT /channels/{target}/end_ring/{user}` | Low | Stop voice ring |

### Microservice Endpoints (not in main OpenAPI)

#### Autumn (CDN) — `cdn.stoatusercontent.com`
| Endpoint | Android Status | Notes |
|----------|---------------|-------|
| `GET /` | Not needed | Root info |
| `POST /:tag` | [x] Implemented | Upload file (6 tags, multipart) |
| `GET /:tag/:file_id` | [x] Via URL | Fetch preview/thumbnail |
| `GET /:tag/:file_id/:filename` | [x] Via URL | Fetch original file |

Upload tags and limits:
- `attachments`: 20 MB, any type
- `avatars`: 4 MB, image only, 128px preview
- `backgrounds`: 6 MB, image only, 1280x720 preview
- `icons`: 2.5 MB, image only, 128px preview
- `banners`: 6 MB, image only, 480px preview
- `emojis`: 500 KB, image only, 128px preview

#### January (Proxy) — `proxy.stoatusercontent.com`
| Endpoint | Android Status | Notes |
|----------|---------------|-------|
| `GET /` | Not needed | Root info |
| `GET /proxy?url=` | [x] Via URL helper | Proxy media files |
| `GET /embed?url=` | Not implemented | Generate embed for URL |

#### GifBox — GIF picker service
| Endpoint | Android Status | Notes |
|----------|---------------|-------|
| `GET /` | Not needed | Root info |
| `GET /categories` | **MISSING** | GIF categories |
| `GET /search?q=` | **MISSING** | Search GIFs |
| `GET /trending` | **MISSING** | Trending GIFs |

### Recently Completed (this session)

| Feature | Commit | Status |
|---------|--------|--------|
| Pin/unpin message API + UI | `40e859e` | [x] Done (fixed POST method) |
| Bulk delete messages API | `40e859e` | [x] Done (API only, no UI yet) |
| Kick member API + UI | `40e859e` | [x] Done with confirmation dialog |
| Ban/unban member API + UI | `40e859e` | [x] Done with reason field |
| Fetch bans API | `40e859e` | [x] Done (API only) |
| Edit server API | `40e859e` | [x] Done (API only) |
| Edit member API | `40e859e` | [x] Done (API only, no UI yet) |
| Create channel in server API | `40e859e` | [x] Done (API only) |
| Role CRUD API | `40e859e` | [x] Done (API only) |
| Server/channel permissions API | `40e859e` | [x] Done (API only) |
| Message search with filters | Earlier commits | [x] Done (API + full UI) |

### Priority Implementation Order

#### Phase 1: High-priority user-facing (next)
1. Account settings UI (change email, password, username)
2. Default permissions (server + channel)
3. Member search (experimental query)
4. Mutual friends/servers
5. GIF picker (GifBox integration)
6. Push unsubscribe on logout

#### Phase 2: Admin UI
7. Bulk delete UI (message selection)
8. Server invite list UI
9. Role management UI (create/edit/delete/reorder)
10. Channel creation UI
11. Member editing UI (nickname, avatar, roles)
12. Ban list management UI

#### Phase 3: Security features
13. MFA management (enable/disable TOTP, recovery codes)
14. Session renaming
15. Account deletion flow
16. Email verification flow

#### Phase 4: Power features
17. Emoji management (create/delete server emojis)
18. Webhook management
19. Bot management
20. Remove all reactions from message

## Key Files

| File | Purpose |
|------|---------|
| `api/routes/server/Server.kt` | Server API — 17 routes implemented |
| `api/routes/channel/Channel.kt` | Channel API — message, invite, settings |
| `api/routes/channel/Message.kt` | Pin, unpin, reactions, bulk delete |
| `api/routes/channel/Search.kt` | Message search with 8 filters |
| `api/routes/auth/Sessions.kt` | Session management — needs edit |
| `sheets/MemberContextSheet.kt` | Member context — kick/ban implemented |
| `sheets/MessageContextSheet.kt` | Message context — pin/unpin implemented |
