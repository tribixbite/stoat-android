# Stoat/Revolt Search API Benchmark

**Date:** 2026-02-13 07:20 EST  
**Base URL:** `https://api.stoat.chat/0.8`  
**Channel:** `01KH9C6QEYSJMQCXB78NPVKCCC`  
**Server:** `01KH9C6QEY8ZS7KPBBV63W3ERT`  
**Total "hello" messages in channel:** 3  

---

## Results Summary Table

| # | Test Description | Method | Status | Time (s) | Notes |
|---|---|---|---|---|---|
| 1 | Basic search, no `include_users` | POST | **200** | 18.04 | Returns bare JSON array of messages |
| 2 | `include_users: true` | POST | **200** | 5.89 | Returns `{messages, users, members}` object |
| 3 | `include_users: false` | POST | **200** | 3.00 | Returns bare JSON array (same as #1) |
| 4 | `limit: 1` | POST | **200** | 2.78 | Returns 1 message (with users/members) |
| 5 | `limit: 25` | POST | **200** | 2.92 | Returns 3 msgs (all available) |
| 6 | `limit: 100` | POST | **200** | 3.93 | Returns 3 msgs (all available) |
| 7 | API v0.9 | POST | **404** | 0.31 | Version does not exist |
| 8 | API v1 | POST | **404** | 0.33 | Version does not exist |
| 9 | No version prefix | POST | **200** | 5.50 | Works! Returns bare array |
| 10 | GET instead of POST | GET | **404** | 0.32 | Only POST supported |
| 11 | `sort: "Relevance"` | POST | **200** | 3.43 | Default sort order |
| 12 | `sort: "Latest"` | POST | **200** | 2.22 | Descending ID order (verified) |
| 13 | `sort: "Oldest"` | POST | **200** | 2.20 | Ascending ID order (verified) |
| 14 | `sort: "relevance"` (lowercase) | POST | **422** | 0.32 | Case-sensitive enum -- rejected |
| 15 | `pinned: true` | POST | **400** | 0.33 | `InvalidOperation` -- not supported |
| 16 | `pinned: false` | POST | **400** | 0.32 | `InvalidOperation` -- not supported |
| 17 | Space-only query `" "` | POST | **200** | 0.32 | Returns empty array `[]` |
| 18 | Asterisk wildcard `"*"` | POST | **200** | 0.40 | Returns empty array `[]` |
| 19 | Empty query `""` | POST | **400** | 0.29 | `FailedValidation`: min length 1 |
| 20a | Pagination baseline (sort=Latest) | POST | **200** | 2.42 | Last ID: `01KHA8CX1GPZYT710HVRA51N1Y` |
| 20b | Paginate with `before` param | POST | **200** | 2.19 | Returns empty `[]` -- no earlier matches |
| 21 | `author` filter | POST | **200** | 2.76 | **Ignored** -- returned all 3 authors |
| 22 | `channel` in body | POST | **200** | 2.70 | Ignored (channel is in URL path) |
| 23 | `has: "attachment"` | POST | **200** | 3.19 | Ignored -- returned all results |
| 24 | `content_type: "image"` | POST | **200** | 3.16 | Ignored -- returned all results |
| 25 | `nearby` message | POST | **200** | 2.98 | Ignored -- returned all results |
| 26 | Server-wide search (`/servers/.../search`) | POST | **404** | 0.27 | Endpoint does not exist |
| 26b | Server search (no version prefix) | POST | **404** | 0.63 | Endpoint does not exist |
| 27 | List DM channels (`/users/dms`) | GET | **200** | 0.26 | Returns `[]` -- no DMs |
| 28 | DM search | -- | **SKIP** | -- | No DM channels available |

### Boundary Tests

| Test | Status | Notes |
|---|---|---|
| `limit: 0` | **400** | `FailedValidation`: range min=1, max=100 |
| `limit: -1` | **400** | `FailedValidation`: range min=1, max=100 |
| `limit: 101` | **400** | `FailedValidation`: range min=1, max=100 |

---

## Key Findings

### 1. Response Format Depends on `include_users`
- **Without `include_users`** (or `include_users: false`): Returns a bare JSON **array** of message objects.
- **With `include_users: true`**: Returns a JSON **object** with three keys:
  - `messages`: array of message objects
  - `users`: array of user objects  
  - `members`: array of member objects
- This format difference is critical for client parsing logic.

### 2. Performance Observations
- **Test 1 was anomalously slow** (18.0s) -- likely cold-start or first-request overhead. Subsequent requests to the same endpoint were 3-6s.
- `include_users: true` adds ~1-3s overhead for user/member resolution.
- Sort by `Latest`/`Oldest` (2.2s) is marginally faster than `Relevance` (3.4s).
- Error responses are fast (0.27-0.40s) since they short-circuit before search execution.
- Wildcard/space queries return instantly with empty results (no full-text match).

### 3. API Version Support
- **`/0.8/`** -- supported, primary version
- **No prefix** -- also works (falls through to default handler)
- **`/0.9/`**, **`/v1/`** -- 404, not implemented
- Only **POST** method is accepted; GET returns 404.

### 4. Sort Parameter
- Accepts: `"Relevance"`, `"Latest"`, `"Oldest"` (PascalCase only)
- **Case-sensitive**: lowercase `"relevance"` returns 422 Unprocessable Entity
- `"Oldest"` produces ascending ULID order (verified)
- `"Latest"` produces descending ULID order (verified)

### 5. Filtering is Severely Limited
- **`pinned`**: Returns `InvalidOperation` error (400). The field is recognized but explicitly rejected.
- **`author`**: Silently ignored. No server-side author filtering.
- **`has`**: Silently ignored. No attachment/content type filtering.
- **`content_type`**: Silently ignored.
- **`nearby`**: Silently ignored. No context-window search.
- **`channel` in body**: Silently ignored (channel comes from URL path).
- Unknown/unsupported params are accepted without error but have no effect.

### 6. Pagination with `before`
- The `before` parameter is accepted and appears functional.
- Returns empty array when no results exist before the given message ID.
- Combined with `sort: "Latest"`, this enables cursor-based pagination.

### 7. Query Validation
- **Min length**: 1 character (empty string rejected with `FailedValidation`)
- **Max length**: 64 characters
- **Limit range**: 1-100 (inclusive, validated server-side)
- Space-only and wildcard `*` queries pass validation but return no results.

### 8. No Server-Wide Search
- `/servers/{id}/search` returns 404 on both versioned and unversioned paths.
- Search is **channel-scoped only**. To search across a server, the client must iterate channels.

### 9. Error Source Locations (from responses)
- Errors reference `crates/delta/src/routes/channels/message_search.rs` -- confirms this is the Revolt Delta backend (Rust/Rocket).
- Line 28: query validation
- Line 34: pinned filter rejection (InvalidOperation)

---

## Recommendations for Client Implementation

1. **Always send `include_users: true`** -- the client needs user data to display messages, and the response format is more structured (object vs bare array).
2. **Use PascalCase sort values** exactly: `"Relevance"`, `"Latest"`, `"Oldest"`.
3. **Implement client-side filtering** for author, attachments, etc., since the API does not support them.
4. **Handle two response formats** or always use `include_users` to get the consistent object format.
5. **Implement cursor pagination** using `before` + `sort: "Latest"` for infinite scroll.
6. **For server-wide search**, iterate all accessible channels and merge results client-side (sorted by timestamp).
7. **Cap limit at 100** and validate before sending to avoid 400 errors.
8. **Query length**: enforce 1-64 character range client-side for responsive UX.
