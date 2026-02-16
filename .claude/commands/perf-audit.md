Audit the codebase for performance bottlenecks, latency issues, and optimization opportunities.

Target: `$ARGUMENTS` (default: all app source files)

## Patterns to Find

Search `app/src/main/java/` (or the specified path) for these categories:

### Critical (blocks UI thread or causes multi-second delays)
1. **`runBlocking` on main thread** — grep for `runBlocking` in .kt files. Any usage inside a Handler, Runnable, or on Looper.getMainLooper() blocks the entire UI. Should use `CoroutineScope(Dispatchers.IO).launch` or `withContext`.
2. **Sequential API calls that could be parallel** — look for multiple `suspend` calls in sequence where results are independent. Pattern: `val a = fetchA()` then `val b = fetchB()` without `a` depending on `b`. Should use `coroutineScope { async { } }` + `awaitAll()`.
3. **Double deserialization (SerializationException catch)** — `try { decodeFromString(StoatAPIError) } catch (SerializationException)` then `decodeFromString(ActualType)`. Every successful response throws+catches an exception and deserializes twice. Should check `res.status.value !in 200..299` first.
4. **Pre-draw listener blocking** — `OnPreDrawListener` returning false while waiting on network calls. Delays all rendering until network completes.

### High (adds 200ms+ per occurrence)
5. **Sequential list fetches in loops** — `for (item in list) { fetchSomething(item) }` instead of `list.map { async { fetch(it) } }.awaitAll()`. Each iteration adds a full round-trip.
6. **Redundant API calls** — same endpoint called multiple times for the same data within a single screen load. Check for duplicate `fetch*()` calls in ViewModels.
7. **Missing cache usage** — `fetchUser(id)` when `StoatAPI.userCache[id]` would suffice. Check if `getOrFetch*` variants exist but aren't used.
8. **Force-unwrap after null fetch** — `fetchSomething()!!` pattern where the fetch returns null on failure, causing the entire coroutine to crash and retry from scratch.

### Medium (adds 50-200ms or wastes resources)
9. **HTTP interceptor overhead** — Chucker with `alwaysReadResponseBody(true)` or large `maxContentLength`. Buffers every response body even when not inspecting.
10. **No connection pooling** — OkHttp without explicit connection pool config. Default is fine for most cases but check if many concurrent connections are needed.
11. **Unbounded image loading** — Glide/Coil loads without `override()` or size constraints on list items. Can cause OOM or jank on scroll.
12. **Recomposition storms** — `mutableStateOf` updated in tight loops or `LaunchedEffect(Unit)` triggering state changes that cascade recompositions.

### Low (micro-optimizations, fix if convenient)
13. **String concatenation in hot paths** — `"$STOAT_FILES/avatars/${id}"` in render loops. Should pre-compute or use StringBuilder for lists.
14. **Unnecessary `withContext` wrapping** — `withContext(Dispatchers.IO) { }` around already-suspended Ktor calls (Ktor handles its own dispatching).

## Verification Steps

After finding issues, for each:
1. Measure or estimate the latency impact (e.g. "3 sequential 200ms calls = 600ms wasted")
2. Propose the fix with code snippet
3. Rate risk: low (local change), medium (affects data flow), high (architectural)

## Output Format

Group findings by severity (Critical > High > Medium > Low). For each:
- File path and line number
- Code snippet showing the anti-pattern
- Estimated latency impact
- Suggested fix (one-liner or short snippet)
- Risk level

## After Audit
- Report total count per category
- Highlight top 5 highest-impact items to fix first
- Compare against previous audit results if available
- Note any new pattern categories discovered
