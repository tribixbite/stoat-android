# Message Search — Architecture Spec

## Overview

Message search allows users to search for messages within a channel using the Revolt API's `POST /channels/{channelId}/search` endpoint. Results are displayed in a dedicated full-screen UI with filter and sort options.

## API

### Endpoint

`POST /channels/{channelId}/search` (v0.8 only — v0.9, v1 do not exist)

### Request Body

```json
{
  "query": "search text (1-64 chars, optional)",
  "limit": 25,
  "before": "ULID",
  "after": "ULID",
  "sort": "Relevance|Latest|Oldest",
  "include_users": true,
  "pinned": true
}
```

**Key behaviors:**
- `query` and `pinned` are mutually exclusive (server returns HTTP 400 if both sent)
- Omitting `query` entirely (null) returns all messages (~0.4s) — useful for filter-only searches
- Empty string `""` is rejected (min length 1)
- Space `" "` returns zero results (not a valid wildcard)
- Query uses MongoDB `$text $search` syntax: multi-word = OR, `"exact phrase"`, `-negation`
- Multi-word queries are significantly slower (14-15s vs 3-4s for single word)
- Exact phrase queries (`"..."`) may timeout (>30s)
- Server ignores unknown parameters (author_id, has, content_type, mentions — all ignored)
- All content/attachment/user filters must be implemented client-side

### Response Format

**Without `include_users`** (faster, ~2.5-4s):
```json
[
  {"_id": "...", "author": "...", "content": "...", "attachments": [...], ...}
]
```

**With `include_users=true`** (slower, ~3.5-5s):
```json
{
  "messages": [...],
  "users": [...],
  "members": [...]
}
```

### Performance Benchmarks

| Scenario | Time | Notes |
|----------|------|-------|
| Null query (browse all) | ~0.4s | Fastest — no text search |
| Pinned browse | ~0.4s | Very fast |
| Single word, no include_users | ~2.5-4s | Recommended default |
| Single word, include_users=true | ~3.5-5s | ~1-2s overhead |
| Multi-word OR query | ~14-15s | Significantly slower |
| Exact phrase query | >30s | May timeout |
| Sort=Latest | ~2.6s | Fastest sort |
| Sort=Oldest | ~2.8s | |
| Sort=Relevance | ~4.1s | Slowest sort |
| Limit variation (1-100) | Minimal | Limit doesn't significantly affect speed |
| Server-side filter params | Ignored | author_id, has, content_type, mentions not supported |

## Key Files

| File | Purpose |
|------|---------|
| `api/routes/channel/Search.kt` | `searchMessages()` API route with per-request 30s timeout |
| `screens/search/MessageSearchScreen.kt` | Search UI + ViewModel with client-side filters |

## UI

### Entry Points

- Search icon in channel `TopAppBar` (all channel types)
- Navigates to `search/{channelId}` route

### Search Screen

- **Top bar**: Back button + search text field (auto-focused) + submit button / loading spinner
- **Sort/pinned chips**: Pinned only, sort order (Relevance/Latest/Oldest)
- **Content filter chips** (all client-side, applied after API results):
  - Has: Link — matches URLs in message content via regex
  - Has: Attachment — messages with any attachment
  - Has: Image — messages with image/* content_type attachments
  - Has: File — messages with non-image attachments
  - Has: Embed — messages with URL embeds
  - Has: Reply — messages that are replies to other messages
  - Has: Reaction — messages with emoji reactions
  - Has: Mention — messages that @mention users
- **From user filter**: Text field for username/display_name substring match (case insensitive)
- **Results list**: Message previews with author avatar, name, timestamp, content (max 3 lines)
- **Metadata indicators**: attachment count, embed count, reply, reaction count, mention count, pinned
- **Pagination**: Infinite scroll using `before` parameter from last result
- **Empty state**: "No messages found" when search returns no results
- **Error display**: Inline error container showing HTTP errors and timeouts

### Search Behavior

- Explicit submit via search button or keyboard Search action (no auto-search)
- Can search with just from:user or has: filters without text query (omits query to browse all)
- Fresh search on sort/filter change if a search was already performed
- Client-side filters applied after API results — reapplied without refetching
- Smart `include_users`: only sent when from:user filter is active or first search with empty cache
- API errors displayed inline in error container
- `SearchResult` sealed class surfaces errors to UI instead of swallowing them

### HttpTimeout Configuration

Global (`StoatAPI.kt`):
- connectTimeout: 10s
- socketTimeout: 15s
- requestTimeout: 30s

Search per-request override:
- socketTimeout: 30s
- requestTimeout: 45s

## Navigation

Route: `search/{channelId}` in main `NavHost` (MainActivity.kt)

Triggered via `Action.TopNavigate("search/$channelId")` from the channel screen's search icon button.

## TODO

- [ ] Tap result to navigate to message in channel context (using `nearby` fetch)
- [ ] Full server search (search across all channels in a server)
- [ ] DM search
- [ ] Highlight search query matches in results
- [ ] Consider caching recent searches
- [x] Add from-user filter (client-side username match)
- [x] Add has-attachment/image/file/link filters (client-side)
- [x] Add has-embed/reply/reaction/mention filters (client-side)
- [x] Fix wildcard query (null instead of space)
- [x] Per-request timeout overrides for slow search endpoint
- [x] Smart include_users (skip when not needed)
