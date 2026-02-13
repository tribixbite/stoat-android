# Feature Gap Analysis — Android vs Web/Desktop

## Overview

Comprehensive comparison of Stoat Android client features against the web client (SolidJS), desktop client (Electron), legacy Revite client, and Revolt backend API. Goal: identify missing features and implement them so the Android app exceeds web app functionality.

## Sources Analyzed

| Source | Description |
|--------|-------------|
| `stoatchat/for-web` | Stoat web client (SolidJS + TypeScript) |
| `stoatchat/for-desktop` | Stoat desktop client (Electron wrapper) |
| `revoltchat/frontend` | Upstream Revolt web frontend |
| `revoltchat/backend` | Revolt backend API (Rust) |
| `revoltchat/revite` | Legacy Revolt web client (React) |

## Android API Routes: 59 implemented

## Gap Summary

### Priority 1 — Core Messaging (all users)

| Feature | Web | Android API | Android UI | Status |
|---------|-----|-------------|------------|--------|
| Pin/unpin message | Yes | **MISSING** | **MISSING** | TODO |
| Mark as unread | Yes | Partial (ack) | Toast "coming soon" | TODO |
| Bulk message delete | Yes | **MISSING** | **MISSING** | TODO |

### Priority 2 — Server Administration (admins/owners)

| Feature | Web | Android API | Android UI | Status |
|---------|-----|-------------|------------|--------|
| Kick member | Yes | **MISSING** | Placeholder TODO | TODO |
| Ban member | Yes | **MISSING** | **MISSING** | TODO |
| Unban member | Yes | **MISSING** | **MISSING** | TODO |
| List server bans | Yes | **MISSING** | **MISSING** | TODO |
| Create channel in server | Yes | **MISSING** | **MISSING** | TODO |
| Edit server settings | Yes | **MISSING** | **MISSING** | TODO |
| Timeout member | Yes | **MISSING** | **MISSING** | TODO |

### Priority 3 — Role & Permission Management

| Feature | Web | Android API | Android UI | Status |
|---------|-----|-------------|------------|--------|
| Create role | Yes | **MISSING** | **MISSING** | TODO |
| Edit role (name, color, permissions) | Yes | **MISSING** | **MISSING** | TODO |
| Delete role | Yes | **MISSING** | **MISSING** | TODO |
| Set server permissions for role | Yes | **MISSING** | **MISSING** | TODO |
| Set channel permission overrides | Yes | **MISSING** | Partial UI | TODO |
| Assign roles to member | Yes | **MISSING** | **MISSING** | TODO |

### Priority 4 — Member Management

| Feature | Web | Android API | Android UI | Status |
|---------|-----|-------------|------------|--------|
| Edit member nickname | Yes | **MISSING** | **MISSING** | TODO |
| Edit member avatar (server identity) | Yes | **MISSING** | **MISSING** | TODO |
| Edit member roles | Yes | **MISSING** | **MISSING** | TODO |
| Server identity (own nick/avatar) | Yes | **MISSING** | **MISSING** | TODO |

### Priority 5 — Custom Emoji

| Feature | Web | Android API | Android UI | Status |
|---------|-----|-------------|------------|--------|
| Create custom emoji | Yes | **MISSING** | **MISSING** | TODO |
| Delete custom emoji | Yes | **MISSING** | **MISSING** | TODO |
| Emoji management UI | Yes | N/A | **MISSING** | TODO |

### Priority 6 — Account Management

| Feature | Web | Android API | Android UI | Status |
|---------|-----|-------------|------------|--------|
| Change email | Yes | **MISSING** | **MISSING** | TODO |
| Change password | Yes | **MISSING** | **MISSING** | TODO |
| Change username | Yes | **MISSING** | **MISSING** | TODO |
| Delete account | Yes | **MISSING** | **MISSING** | TODO |
| Rename session | Yes | **MISSING** | **MISSING** | TODO |

### Priority 7 — Webhooks & Bots

| Feature | Web | Android API | Android UI | Status |
|---------|-----|-------------|------------|--------|
| Create webhook | Yes | **MISSING** | **MISSING** | TODO |
| Edit webhook | Yes | **MISSING** | **MISSING** | TODO |
| Delete webhook | Yes | **MISSING** | **MISSING** | TODO |
| Create bot | Yes | **MISSING** | **MISSING** | TODO |
| Edit/delete bot | Yes | **MISSING** | **MISSING** | TODO |
| List own bots | Yes | **MISSING** | **MISSING** | TODO |

### Priority 8 — Category Management

| Feature | Web | Android API | Android UI | Status |
|---------|-----|-------------|------------|--------|
| Create category | Yes | **MISSING** | **MISSING** | TODO |
| Delete category | Yes | **MISSING** | **MISSING** | TODO |

### Already Implemented (Android matches/exceeds web)

| Feature | Notes |
|---------|-------|
| Message send/edit/delete | API + UI complete |
| Message reactions (add/remove) | API + UI complete |
| Message reply | API + UI complete |
| Message search with filters | API + UI with 8 client-side filters |
| User relationships (friend/block) | API + UI complete |
| DMs and group DMs | API + UI complete |
| Server create/leave/delete | API + UI complete |
| Channel settings (name, desc, icon) | API + UI complete |
| Push notifications (FCM) | API + handler complete |
| Invites (create/join) | API + UI complete |
| Content reporting | API + UI complete |
| Media viewing (image/video) | Dedicated activities |
| Authentication + MFA | API + UI complete |
| Session management | API + UI (view/logout) |
| WebSocket real-time events | 30+ event types handled |
| Settings sync | API complete |

## Backend API Endpoints NOT in Android

### Channels
```
PUT  /channels/{id}/messages/{msgId}/pin       # Pin message
DELETE /channels/{id}/messages/{msgId}/pin      # Unpin message
DELETE /channels/{id}/messages/bulk             # Bulk delete messages
PUT  /channels/{id}/permissions/{roleId}       # Set channel permission override
DELETE /channels/{id}/permissions/{roleId}     # Remove channel permission override
```

### Servers
```
PATCH /servers/{id}                             # Edit server
POST  /servers/{id}/channels                    # Create channel
DELETE /servers/{id}/members/{userId}            # Kick member
PATCH /servers/{id}/members/{userId}            # Edit member (nick, avatar, roles, timeout)
GET   /servers/{id}/bans                        # List bans
PUT   /servers/{id}/bans/{userId}               # Ban member
DELETE /servers/{id}/bans/{userId}              # Unban member
POST  /servers/{id}/roles                       # Create role
PATCH /servers/{id}/roles/{roleId}             # Edit role
DELETE /servers/{id}/roles/{roleId}            # Delete role
PUT   /servers/{id}/permissions/{roleId}       # Set server permission for role
```

### Account
```
PATCH /auth/account/change/email               # Change email
PATCH /auth/account/change/password            # Change password
POST  /auth/account/delete                     # Delete account
PATCH /auth/session/{id}                       # Rename session
```

### Custom Emoji
```
PUT   /custom/emoji/{id}                       # Create emoji
DELETE /custom/emoji/{id}                      # Delete emoji
```

### Bots
```
POST  /bots/create                             # Create bot
GET   /bots/{id}                               # Fetch bot
PATCH /bots/{id}                               # Edit bot
DELETE /bots/{id}                              # Delete bot
GET   /bots/@me                                # List own bots
POST  /bots/{id}/invite                        # Invite bot to server
```

### Webhooks
```
GET   /servers/{id}/webhooks                   # List webhooks
POST  /channels/{id}/webhooks                  # Create webhook
PATCH /webhooks/{id}                           # Edit webhook
DELETE /webhooks/{id}                          # Delete webhook
```

## Implementation Plan

### Phase 1: Server Moderation (highest admin impact)
1. Add kick member API route + UI in ServerMemberContextSheet
2. Add ban/unban member API routes + UI
3. Add timeout member via edit member API route

### Phase 2: Pin Messages + Mark Unread
4. Add pin/unpin API routes
5. Add pin/unpin to MessageContextSheet
6. Implement mark-as-unread (ack to previous message)

### Phase 3: Server Management
7. Add create channel in server API route + UI
8. Add edit server API route + server settings UI
9. Add role management (create/edit/delete) API routes + UI

### Phase 4: Member Management
10. Add edit member API (nickname, avatar, roles)
11. Add server identity (own nick/avatar) UI

### Phase 5: Account & Content Management
12. Add change email/password routes
13. Add custom emoji management
14. Add webhook/bot management

## Key Files

| File | Purpose |
|------|---------|
| `api/routes/server/Server.kt` | Server API routes — needs kick, ban, role, channel creation |
| `api/routes/channel/Channel.kt` | Channel API routes — needs pin/unpin, bulk delete |
| `api/routes/auth/Sessions.kt` | Auth routes — needs rename session |
| `sheets/MemberContextSheet.kt` | Member context menu — needs moderation actions |
| `sheets/MessageContextSheet.kt` | Message context menu — needs pin/unpin |
