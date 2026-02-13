# Notifications — Architecture Spec

## Overview

Push notifications are delivered via Firebase Cloud Messaging (FCM). The app registers a device token on login and subscribes to the Revolt push endpoint. Incoming messages are handled by `HandlerService` which creates rich notifications with messaging style, reply actions, and Android 11+ bubbles.

## Flow

1. **Token Registration**: `ChatRouterScreen` obtains FCM token via `FirebaseMessaging.getInstance().token`
2. **Push Subscription**: Token sent to server via `POST /push/subscribe` (`Push.kt`)
3. **Message Delivery**: FCM delivers payload to `HandlerService.onMessageReceived()`
4. **Notification Display**: Rich notification built with author avatar, channel name, reply action

## Key Files

| File | Purpose |
|------|---------|
| `api/routes/push/Push.kt` | `subscribePush()` — registers FCM token with server |
| `c2dm/HandlerService.kt` | FCM message handler, notification builder |
| `c2dm/ChannelRegistrator.kt` | Android notification channel registration |
| `screens/chat/ChatRouterScreen.kt` | Permission flow, token registration, retry logic |
| `screens/settings/NotificationSettingsScreen.kt` | Global notification settings UI |
| `api/settings/NotificationSettingsProvider.kt` | Mute state checker |
| `api/settings/SyncedSettings.kt` | Synced notification settings (server/channel mute maps) |

## Mute System

- Mute state stored in `SyncedSettings.notifications` as `Map<String, String>` (value = `"muted"`)
- Two maps: `server` (server ID -> state) and `channel` (channel ID -> state)
- Server mute automatically mutes all channels in that server
- `NotificationSettingsProvider.isChannelMuted(channelId, serverId)` checks both
- Mute state synced to server via `POST /settings/set` (`sync` API)

### Mute UI Entry Points

- **ChannelContextSheet**: Long-press channel in sidebar drawer
- **ServerContextSheet**: Long-press server icon / server context menu
- **ChannelSettingsOverview**: Channel settings -> mute toggle switch
- **NotificationSettingsScreen**: Settings -> Notifications (summary + unmute)
  - Muted servers show cached name (falls back to raw ID)
  - Muted channels show cached name + parent server name
  - Unmute button per entry

### Notification Filtering in HandlerService

- Before displaying, queries local SQLite for channel record to get server ID
- Checks `NotificationSettingsProvider.isChannelMuted(channelId, serverId)`
- Server mute overrides individual channel settings
- Muted notifications are silently consumed (logged at debug level)

## Error Handling

- `subscribePush()` returns `String?` — null on success, error message on failure
- Checks HTTP status code (4xx/5xx surfaced as error string)
- `NotificationSettingsScreen` displays push registration errors inline
- `HandlerService.onNewToken()` wrapped in try-catch
- Glide avatar loading has 10-second timeout with fallback to default icon
- Failed registration tracked via `pushRegistrationFailed` KV flag
- Retry on app resume via `retryPushRegistrationIfNeeded()`
- Payload parse failures logged with specific field names

## Background Connection

- WebSocket managed by `RealtimeSocket.connect()` in `StoatAPI.connectWS()`
- Pings sent every 30 seconds via `mainHandler.postDelayed()`
- When app is backgrounded, Android throttles the main looper → pings stop → server closes socket
- `connectWS()` always marks `DisconnectionState.Disconnected` when the session ends
- `ChatRouterScreen` reconnects on `Lifecycle.Event.ON_RESUME` if disconnected
- FCM handles background notification delivery independently of WebSocket

## Permission Flow

1. On app launch, check `areNotificationsEnabled()` and `pushNotificationsRejected` KV
2. If not enabled and not previously rejected, show `NotificationRationaleDialog`
3. If accepted on Android 13+, request `POST_NOTIFICATIONS` permission
4. If granted, register FCM token and subscribe push
5. If declined, set `pushNotificationsRejected` KV to prevent re-prompting

## TODO

- [ ] Consider implementing notification grouping per server
- [ ] Add in-app notification sound settings
- [ ] Support @mention-only notification mode per channel
- [ ] Handle notification tap → deep link to specific message
