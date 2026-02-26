# Discord Bridge & Import — Architecture Spec

## Overview

Stoat supports importing channel structures from Discord servers and setting up bidirectional message bridges via the **stoatcord-bot**. The system consists of:

1. **stoatcord-bot** — Bun/TypeScript bot running on Discord + Stoat simultaneously
2. **Android app screens** — Import wizard and bridge settings in server management
3. **Stoat API** — Standard channel creation + masquerade message sending

## Architecture

```
┌─────────────────┐     HTTP API        ┌──────────────────┐
│  Stoat Android   │◄──────────────────►│  stoatcord-bot   │
│  (Import Wizard  │  GET /api/guilds   │  (Bun + TS)      │
│   Bridge Settings│  GET /api/links    │                  │
│   )              │  POST/DELETE links  │  ┌──────────┐   │
└────────┬─────────┘                    │  │ SQLite DB│   │
         │                              │  └──────────┘   │
         │ Stoat REST API               │        │         │
         │ (create channels,            │        │ links   │
         │  send messages)              │        │         │
         ▼                              └───┬────┴────┬────┘
┌─────────────────┐                     Discord    Stoat
│  Stoat Backend   │◄── WebSocket ──────┤ WS       WS (Bonfire)
│  (Revolt)        │                    │          │
└─────────────────┘                     ▼          ▼
                              ┌───────────┐  ┌───────────┐
                              │  Discord  │  │  Stoat    │
                              │  Server   │  │  Server   │
                              └───────────┘  └───────────┘
```

## stoatcord-bot

**Repository**: [github.com/tribixbite/stoatcord-bot](https://github.com/tribixbite/stoatcord-bot)

### Stack
- Runtime: Bun v1.2.20 via glibc-runner (grun) on Termux ARM64
- Language: TypeScript (direct execution, no build step)
- Discord: discord.js v14
- Database: bun:sqlite (WAL mode)
- HTTP: Bun.serve()
- Env: Manual .env loader (`src/env.ts`) — grun doesn't forward env vars

### HTTP API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | /api/guilds | List all Discord guilds bot is in |
| GET | /api/guilds/:id/channels | Full channel + role list for a guild |
| GET | /api/links | All active bridge links |
| GET | /api/links/guild/:id | Bridge links for a specific guild |
| POST | /api/links | Create bridge link (auto-creates webhook) |
| DELETE | /api/links/:discordChannelId | Remove a bridge link |
| POST | /api/claim-code | Generate one-time code to authorize server linking |

**Auth (three tiers):**
- **Admin API key**: `X-API-Key` header — gates all non-public endpoints (auto-generated if not set)
- **Per-guild Bearer token**: `Authorization: Bearer <token>` — scopes archive endpoints to a guild
- **Per-user push token**: `X-API-Key` header (overloaded) — scopes push endpoints to a user

**Additional endpoints (not shown above):**

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | /api/health | None | Healthcheck (Railway uptime monitors) |
| GET | /api/diag | Admin key | Bot diagnostics and status |
| POST | /api/test-notify | Admin key | Send a test mention to a Stoat channel |
| GET | /api/push/vapid | None | VAPID public key for WebPush |
| POST | /api/push/register | Admin or push token | Register device for push notifications |
| DELETE | /api/push/unregister | Admin or push token | Remove a device registration |
| GET | /api/push/status | Admin or push token | Check device registration status |
| GET | /api/archive/status | Bearer token | Get archive job status |
| POST | /api/archive/start | Bearer token | Start a message history export |
| POST | /api/archive/pause | Bearer token | Pause a running archive job |
| POST | /api/archive/resume | Bearer token | Resume a paused archive job |

### Bridge Mechanism

**Discord → Stoat**: Bot receives Discord message event → looks up linked Stoat channel → sends via Stoat REST API with `masquerade` field (preserves author name + avatar).

**Stoat → Discord**: Bot receives Stoat WebSocket message → looks up linked Discord channel → sends via Discord webhook with custom username + avatar.

**Echo prevention**: Three layers — masquerade/bot check, 60s message ID tracking set, channel update dedup (10s TTL).

**Additional sync features:**
- **Edit/delete sync**: Message edits and deletes propagate both directions using `bridge_messages` table for ID mapping
- **Reaction sync**: Emoji reactions forwarded bidirectionally with echo prevention
- **Typing indicators**: Bidirectional relay with per-user 5s debounce
- **Channel metadata**: Name, description, and NSFW flag changes sync with dedup
- **Reply chains**: Resolved via bridge_messages lookup; falls back to quote-style formatting
- **Attachment re-hosting**: Files uploaded to Autumn CDN (Stoat→Discord) or Discord CDN; >20MB uses URL fallback
- **Outage recovery**: On WS reconnect, detects gaps via `last_bridged_*` timestamps, replays missed messages

### Discord Slash Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/migrate` | Administrator | Interactive migration wizard with dual-admin auth |
| `/link` | Manage Channels | Link current Discord channel to a Stoat channel for bridging |
| `/unlink` | Manage Channels | Remove bridge link from current channel |
| `/unlink-server` | Administrator | Remove the server-level link, all channel bridges, and role links |
| `/status` | None | Show bridge status for the server |
| `/token` | Administrator | View or regenerate the guild's archive API token |
| `/archive` | Administrator | Export/import message history (start/status/pause/resume subcommands) |

#### `/migrate` Options (in order)
1. `claim_code` — One-time code from Stoat admin (includes server ID — no separate ID needed)
2. `stoat_server_id` — Stoat server ID (triggers live approval if no code provided)
3. `mode` — What to migrate: missing (default), roles, categories, all

#### `/migrate` Modes
- **missing** (default) — Only create channels/roles that don't exist on Stoat yet
- **roles** — Create missing roles only (no channels)
- **categories** — Organize existing Stoat channels into categories matching Discord layout
- **all** — Create everything (warns about potential duplicates)

### Stoat-Side Commands

The bot listens for `!stoatcord` prefix or `<@BOT_ID>` mentions in Stoat channels:

| Command | Admin Required | Description |
|---------|---------------|-------------|
| `!stoatcord code` | Yes | Generate one-time claim code (encodes server ID) |
| `!stoatcord request <guild_id>` | Yes | Send migration request embed to a Discord guild |
| `!stoatcord status` | No | Show bridge link status for this Stoat server |
| `!stoatcord ping <user_id>` | No | Send a mention to test push notifications |
| `!stoatcord diag` | No | Run notification diagnostics |
| `!stoatcord archive` | No | Show archive job status for the linked guild |
| `!stoatcord push setup` | No | Generate a push token (sent via DM) |
| `!stoatcord push revoke` | No | Revoke push token and unregister all devices |
| `!stoatcord push status` | No | Check push registration status |
| `!stoatcord help` | No | List available commands |

Admin check: server owner OR member with `ManageServer` permission on any role.
DM commands: `help`, `ping`, and `push` subcommands work in direct messages.

### Security Model — Dual-Admin Authorization

Every migration into an existing Stoat server requires fresh authorization from both sides. No "already linked" bypass — re-runs always re-auth.

**Three mutually exclusive auth paths for `/migrate`:**

| Path | Trigger | Auth |
|------|---------|------|
| **A: New server** | No code, no server ID | None — bot creates and owns it |
| **B: Claim code** | `claim_code:XXXXXX` | Code validates identity + encodes server ID. Generated by Stoat admin via `!stoatcord code` |
| **C: Live approval** | `stoat_server_id:XXX` (no code) | Bot posts approval request to Stoat server, admin must reply "approve" within 5 min |

**Additional safeguards:**
1. **Discord permission gating**: `/migrate` requires Administrator, `/link`+`/unlink` require Manage Channels — enforced by Discord itself
2. **One-to-one binding**: Each Stoat server can only be linked to one Discord guild — prevents cross-guild hijacking
3. **Atomic claim codes**: `UPDATE WHERE used_by_guild IS NULL` — only one guild can consume a code
4. **Full user tracking**: Every code generation, code use, approval, and migration operation records the Discord user ID, Stoat user ID, auth method, and timestamp
5. **API key auth**: HTTP API optionally protected by shared secret in `X-Api-Key` header
6. **Stoat API enforcement**: Write permissions enforced by Stoat API per-operation

**Live approval flow (Path C):**
1. Discord admin runs `/migrate stoat_server_id:XXX`
2. Bot posts approval request message to first text channel in Stoat server
3. Stoat server admin replies to that message with "approve"/"yes"/"confirm" or "deny"/"reject"/"no"
4. Bot validates replier has ManageServer permission
5. In-memory Promise resolves → migration proceeds in Discord
6. 5-minute timeout if no response

### Database Tables (Schema V7)
- `schema_version` — Tracks DB schema version for incremental migrations
- `server_links` — Discord guild ↔ Stoat server mappings (one-to-one), auth method + user tracking, per-guild `api_token` (V6)
- `channel_links` — Discord channel ↔ Stoat channel with webhook credentials, `last_bridged_stoat`/`last_bridged_discord` for gap detection
- `role_links` — Discord role ↔ Stoat role mappings
- `claim_codes` — One-time codes with creator/consumer tracking, 1-hour expiry
- `migration_requests` — Live approval flow tracking (pending/approved/rejected/expired/cancelled)
- `migration_log` — Audit trail with Discord + Stoat user IDs per operation
- `bridge_messages` — Message ID pair tracking (Discord ID ↔ Stoat ID) for edit/delete/reply sync (V3)
- `archive_jobs` — Archive export/import job tracking with progress and status (V5)
- `archive_messages` — Archived message content storage for import (V5)
- `push_tokens` — Per-user push notification tokens with user ID binding (V7)

## Android App Screens

### DiscordImportScreen
**Path**: `screens/settings/server/DiscordImportScreen.kt`
**Route**: `settings/server/{serverId}/import-discord`

3-step wizard:
1. Connect to stoatcord-bot (enter API URL, fetch guilds)
2. Select Discord server → channel checklist with Select All/Deselect All
3. Import channels (batch `createChannel()` with 2.5s rate limit delay)

### BridgeSettingsScreen
**Path**: `screens/settings/server/BridgeSettingsScreen.kt`
**Route**: `settings/server/{serverId}/bridge-settings`

Bridge management:
1. Connect to stoatcord-bot (same API URL/key)
2. Select Discord guild → view active bridges
3. Create new bridges (dropdown: Discord channel → Stoat channel)
4. Delete bridges (trash icon on each link row)

### API Client
**Path**: `api/routes/discord/Discord.kt`

Functions:
- `fetchBotGuilds(botApiUrl, apiKey)` → `List<DiscordGuildPreview>`
- `fetchGuildChannels(botApiUrl, apiKey, guildId)` → `GuildChannelsResponse`
- `fetchGuildLinks(botApiUrl, apiKey, guildId)` → `List<BridgeLinkInfo>`
- `createBridgeLink(botApiUrl, apiKey, discordChannelId, stoatChannelId)` → `CreateLinkResponse`
- `deleteBridgeLink(botApiUrl, apiKey, discordChannelId)` → Unit

Uses a separate lightweight Ktor HttpClient (no Stoat auth interceptors).

## Server Settings Entry Points

Both screens are accessible from Server Settings → Manage Channel section:
- "Import from Discord" — `icn_download_24dp` icon
- "Bridge Settings" — `icn_link_24dp` icon

Gated behind `canManage || permissions has PermissionBit.ManageChannel`.

## Configuration

### stoatcord-bot `.env`
```
DISCORD_TOKEN=           # Discord bot token
STOAT_TOKEN=             # Stoat bot token
STOAT_API_BASE=          # Default: https://api.stoat.chat/0.8
STOAT_WS_URL=            # Default: wss://events.stoat.chat
STOAT_CDN_URL=           # Default: https://cdn.stoatusercontent.com
STOAT_AUTUMN_URL=        # Default: https://autumn.stoat.chat
API_PORT=3210            # HTTP API port
API_KEY=                 # Admin API key (auto-generated if not set)
DB_PATH=stoatcord.db     # SQLite database path (absolute for containers)
PUSH_ENABLED=true        # Enable push notification relay
FIREBASE_SERVICE_ACCOUNT= # Path to Firebase service account JSON
FIREBASE_SA_JSON=        # Or inline JSON (for containers)
VAPID_PUBLIC_KEY=        # WebPush VAPID public key
VAPID_PRIVATE_KEY=       # WebPush VAPID private key
PUSH_BOT_API_URL=        # Public URL for push registration
CORS_ORIGINS=            # Comma-separated allowed origins (default: block all)
TENOR_API_KEY=           # Tenor v2 API key (utility script only)
```

### Android App
Bot client ID hardcoded: `1472115292925857865`
Default bot API URL: `http://localhost:3210` (user must change to actual bot host)

## Status
- Import wizard (Android): Complete
- Bridge settings (Android): Complete
- Bot HTTP API: Complete (admin, guild-scoped, and per-user push token auth)
- Bot message relay: Complete (bidirectional with echo prevention)
- Edit/delete sync: Complete (both directions)
- Reaction sync: Complete (emoji react/unreact forwarding)
- Typing indicators: Complete (bidirectional, 5s debounce)
- Channel metadata sync: Complete (name, description, NSFW)
- Reply chain preservation: Complete (bridge_messages table lookups)
- Attachment re-hosting: Complete (Autumn CDN upload, URL fallback for >20MB)
- Outage recovery: Complete (gap detection on WS reconnect, replays missed messages)
- Discord slash commands: Complete (/migrate, /link, /unlink, /unlink-server, /status, /token, /archive)
- Migration wizard (Discord): Complete — selective mode, dedup, categories, dry-run
- Dual-admin auth system: Complete — claim codes, live approval, user tracking
- Stoat command system: Complete — code/request/status/ping/diag/archive/push/help
- DM command support: Complete — help, ping, push commands work in DMs
- Role migration: Complete (19+ Discord→Revolt permission mappings)
- Category organization: Complete (maps Discord categories to Stoat server categories)
- Archive system: Complete — export Discord history, import to Stoat via masquerade
- Push notification relay: Complete — FCM (HTTP v1), WebPush (VAPID), UnifiedPush
- Per-user push tokens: Complete — DM-based token issuance, scoped push endpoints
- CI/CD: Complete — GitHub Actions deploys to Railway on push to main
- Rate limiting: Complete — IP-based (60/min general, 10/min push)
- SSRF protection: Complete — unified validator blocking private IPv4/IPv6/metadata
