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

Auth: Optional `X-Api-Key` header (shared secret).

### Bridge Mechanism

**Discord → Stoat**: Bot receives Discord message event → looks up linked Stoat channel → sends via Stoat REST API with `masquerade` field (preserves author name + avatar).

**Stoat → Discord**: Bot receives Stoat WebSocket message → looks up linked Discord channel → sends via Discord webhook with custom username + avatar.

**Echo prevention**: Two layers — masquerade check + 60s message ID tracking set.

### Discord Slash Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/migrate` | Administrator | Interactive migration wizard with mode selection |
| `/link` | Manage Channels | Link current Discord channel to a Stoat channel for bridging |
| `/unlink` | Manage Channels | Remove bridge link from current channel |
| `/status` | None | Show bridge status for the server |

#### `/migrate` Modes
- **missing** (default) — Only create channels/roles that don't exist on Stoat yet
- **roles** — Create missing roles only (no channels)
- **categories** — Organize existing Stoat channels into categories matching Discord layout
- **all** — Create everything (warns about potential duplicates)

### Security Model

1. **Discord permission gating**: `/migrate` requires Administrator, `/link`+`/unlink` require Manage Channels — enforced by Discord itself (non-admins can't see/invoke the commands)
2. **Bot ownership check**: When targeting an existing Stoat server, the bot verifies it owns that server (only servers created by the bot can be migrated into)
3. **One-to-one binding**: Each Stoat server can only be linked to one Discord guild — prevents cross-guild hijacking
4. **API key auth**: HTTP API optionally protected by shared secret in `X-Api-Key` header

### Database Tables
- `server_links` — Discord guild ↔ Stoat server mappings (one-to-one)
- `channel_links` — Discord channel ↔ Stoat channel with webhook credentials
- `role_links` — Discord role ↔ Stoat role mappings
- `migration_log` — Audit trail of import operations

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
DISCORD_TOKEN=     # Discord bot token
STOAT_TOKEN=       # Stoat bot token
STOAT_API_BASE=    # Default: https://api.stoat.chat/0.8
STOAT_WS_URL=      # Default: wss://events.stoat.chat
API_PORT=3210      # HTTP API port
API_KEY=           # Shared secret (optional)
```

### Android App
Bot client ID hardcoded: `1472115292925857865`
Default bot API URL: `http://localhost:3210` (user must change to actual bot host)

## Status
- Import wizard (Android): Complete
- Bridge settings (Android): Complete
- Bot HTTP API: Complete
- Bot message relay: Complete (user avatar resolution implemented)
- Discord slash commands: Complete (/migrate, /link, /unlink, /status)
- Migration wizard (Discord): Complete — selective mode, dedup, categories, security checks
- Role migration: Complete (19+ Discord→Revolt permission mappings)
- Category organization: Complete (maps Discord categories to Stoat server categories)
