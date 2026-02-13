# Features Requiring Backend/API Changes

Features that cannot be implemented in the Android client without changes to the Revolt/Stoat backend API. These are features present in Discord or other chat platforms that the current API (v0.11.0, 121 endpoints) does not support.

## Confirmed Missing from API

### Threading & Forums
| Feature | Discord Equivalent | Revolt Status |
|---------|-------------------|---------------|
| Thread channels | Threads in text channels | No API support. No thread-related endpoints or data types in OpenAPI spec. |
| Forum channels | Forum channel type with posts | No `Forum` channel type. Only `TextChannel`, `VoiceChannel`, `Group`, `DirectMessage`, `SavedMessages` exist. |
| Thread auto-archive | Auto-archive after inactivity | N/A — no threads |

### Scheduled Events
| Feature | Discord Equivalent | Revolt Status |
|---------|-------------------|---------------|
| Server events | Scheduled Events | No event endpoints or schemas in API |
| Event RSVP | Interested/Going | N/A |
| Event reminders | Push notifications for events | N/A |

### Stage Channels
| Feature | Discord Equivalent | Revolt Status |
|---------|-------------------|---------------|
| Stage channels | Stage channel type | No stage channel type. Voice exists via LiveKit but no speaker/audience model |
| Speaker queue | Request to speak | N/A |

### Auto-Moderation
| Feature | Discord Equivalent | Revolt Status |
|---------|-------------------|---------------|
| AutoMod rules | Keyword filters, spam, mention limits | No AutoMod endpoints. Backend has basic word filters but no user-configurable rules API |
| AutoMod actions | Timeout, delete, alert | N/A |
| Regex rules | Custom regex patterns | N/A |

### Audit Log
| Feature | Discord Equivalent | Revolt Status |
|---------|-------------------|---------------|
| Server audit log | Track admin actions | No audit log endpoint in API. Admin panel may have internal access but not exposed to clients |
| Action filtering | Filter by type/user | N/A |

### Application Commands
| Feature | Discord Equivalent | Revolt Status |
|---------|-------------------|---------------|
| Slash commands | `/command` interactions | No application command registration API. Bots can only respond to message content |
| Command autocomplete | Parameter suggestions | N/A |
| Context menu commands | Right-click → App actions | N/A |

### Interactive Components
| Feature | Discord Equivalent | Revolt Status |
|---------|-------------------|---------------|
| Buttons on messages | Action buttons | No message component schema in API |
| Select menus | Dropdown selections | N/A |
| Modals/Forms | Interactive forms | N/A |
| Message polls | Built-in polls | No poll endpoint or schema. Can only be done via bot hacks (reactions) |

### Rich Presence & Activities
| Feature | Discord Equivalent | Revolt Status |
|---------|-------------------|---------------|
| Rich presence | Game status with details | User status exists (text + presence) but no rich presence fields (game, large_image, etc.) |
| Embedded activities | YouTube Together, etc. | No activity endpoints |
| Custom status emoji | Emoji in status | Status is text-only, no emoji field |

### Server Features
| Feature | Discord Equivalent | Revolt Status |
|---------|-------------------|---------------|
| Server templates | Create from template | No template endpoints |
| Server discovery | Browse public servers | No discovery/explore endpoints in API. Web uses external listing |
| Welcome screen | Onboarding channels | Only basic onboarding (username set). No welcome screen config |
| Server boost/premium | Nitro boost perks | No subscription/boost system |
| Vanity URL | Custom invite link | No vanity URL management endpoint |

### Media Features
| Feature | Discord Equivalent | Revolt Status |
|---------|-------------------|---------------|
| Stickers | Custom/Nitro stickers | No sticker endpoints. Emojis only |
| Soundboard | Server sound effects | No soundboard API |
| Super reactions | Animated reactions | No super reaction type in API |
| Screen sharing | Go Live in voice | LiveKit voice exists but no screen sharing signaling in API |
| Video calls | Camera in voice | LiveKit handles this but client support needed |

### Permission Features
| Feature | Discord Equivalent | Revolt Status |
|---------|-------------------|---------------|
| Permission overwrites per user | Per-user channel overrides | API only supports per-role overrides, not per-user |
| Channel-specific timeout | Slow mode per channel | No slow mode field in channel schema |
| Link preview control | Suppress embeds per channel | No per-channel embed suppression setting |

### Notification Features
| Feature | Discord Equivalent | Revolt Status |
|---------|-------------------|---------------|
| @everyone/@here mentions | Mention roles/everyone | `@everyone` mentioning exists but no `@here` (online-only) |
| Mention roles | @role pings | No role mention parsing in API |
| Suppress @everyone | Per-user setting | Notification settings exist but no granular suppress |

## Partially Supported (Workarounds Possible)

### Features with client-side workarounds
| Feature | Current State | Workaround |
|---------|--------------|------------|
| Message pinning view | Pin API exists, search with `pinned=true` works | [x] Already implemented via search filter |
| Mark as unread | No dedicated endpoint | Ack to a previous message ID (partially works) |
| GIF picker | GifBox service exists | Needs client integration with `/search`, `/trending`, `/categories` |
| Embed suppression | No per-message suppress | Can edit message to remove embeds client-side |
| Typing indicators | WebSocket events exist | [x] Already implemented |
| Read receipts | Ack events via WebSocket | [x] Already implemented per-channel |
| User notes | No notes API | Could use sync/settings as key-value store |
| Server folders | No server folder API | Could use sync/settings for client-side grouping |

## Summary

| Category | Feature Count | Backend Work Required |
|----------|--------------|----------------------|
| Threads & Forums | 3 | New channel types + full CRUD API |
| Scheduled Events | 3 | New data model + CRUD API |
| Stage Channels | 2 | New channel subtype + speaker model |
| AutoMod | 3 | New rule engine + CRUD API |
| Audit Log | 2 | New logging system + query API |
| Application Commands | 3 | New interaction framework |
| Interactive Components | 4 | New message component schema |
| Rich Presence | 3 | Extended user status model |
| Server Features | 5 | Multiple new systems |
| Media Features | 5 | Various new subsystems |
| Permission Features | 3 | Schema extensions |
| Notification Features | 3 | Extended notification model |
| **Total** | **39** | — |

These 39 features represent the gap between Revolt/Stoat and Discord that cannot be closed without backend API changes. The Revolt team would need to implement these server-side before any client can support them.

## What We CAN Do (56 missing endpoints)

All 56 remaining unimplemented endpoints from the current API v0.11.0 can be added purely client-side. See [feature-gap-analysis.md](feature-gap-analysis.md) for the full list and implementation priority.
