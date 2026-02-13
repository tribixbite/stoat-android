# Message Search — Architecture Spec

## Overview

Message search allows users to search for messages within a channel using the Revolt API's `POST /channels/{channelId}/search` endpoint. Results are displayed in a dedicated full-screen UI with filter and sort options.

## API

### Endpoint

`POST /channels/{channelId}/search`

### Request Body

```json
{
  "query": "search text",
  "limit": 25,
  "before": "ULID",
  "after": "ULID",
  "sort": "Relevance|Latest|Oldest",
  "include_users": true,
  "pinned": true
}
```

### Response

Returns `MessagesInChannel` schema:
```json
{
  "messages": [...],
  "users": [...],
  "members": [...]
}
```

## Key Files

| File | Purpose |
|------|---------|
| `api/routes/channel/Search.kt` | `searchMessages()` API route |
| `screens/search/MessageSearchScreen.kt` | Search UI + ViewModel |

## UI

### Entry Points

- Search icon in channel `TopAppBar` (all channel types)
- Navigates to `search/{channelId}` route

### Search Screen

- **Top bar**: Back button + search text field (auto-focused)
- **Sort/pinned chips**: Pinned only, sort order (Relevance/Latest/Oldest)
- **Content filter chips** (client-side, Discord-style):
  - Has: Link — matches URLs in message content
  - Has: Attachment — messages with any attachment
  - Has: Image — messages with image attachments (content_type starts with `image/`)
  - Has: File — messages with non-image attachments
- **From user filter**: Text field for username substring match (case insensitive)
- **Results list**: Message previews with author avatar, name, timestamp, content (max 3 lines), attachment count indicator
- **Pagination**: Infinite scroll using `before` parameter from last result
- **Empty state**: "No messages found" when search returns no results

### Search Behavior

- 400ms debounce on query input
- Minimum 1 character to trigger search
- Fresh search on sort/filter change (server-side)
- Client-side filters applied after API results — reapplied without refetching
- Results include user data for avatar/name display

## Navigation

Route: `search/{channelId}` in main `NavHost` (MainActivity.kt)

Triggered via `Action.TopNavigate("search/$channelId")` from the channel screen's search icon button.

## TODO

- [ ] Tap result to navigate to message in channel context (using `nearby` fetch)
- [x] Add from-user filter (client-side username match)
- [x] Add has-attachment/image/file/link filters (client-side)
- [ ] Highlight search query matches in results
- [ ] Consider caching recent searches
