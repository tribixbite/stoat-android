Scan Kotlin source files for crash-prone null-safety patterns.

Target: `$ARGUMENTS` (default: all app source files)

## Patterns to Find

Search `app/src/main/java/` (or the specified path) for these categories:

### Critical (crash on null data)
1. **Force-unwrap `!!`** — grep for `!!` in .kt files. Skip patterns that are safe:
   - Inside `?.let { ... it!! }` (redundant but safe)
   - After `?: return` / `?: throw` on the same local val
   - Standard Android patterns: `startDestinationRoute!!`, system service casts
2. **`.first()` without guard** — can throw `NoSuchElementException` on empty collections. Should be `.firstOrNull()`
3. **`catch (e: Error)`** — only catches JVM errors (OOM), misses network exceptions. Should be `catch (e: Exception)`

### High (race condition crashes)
4. **Unsafe cast `as` on mutable state** — `is` check then `as` cast on a `var`, `mutableStateOf`, or `.value` property. The value can change between check and cast. Should use `as?` safe cast or local `val` capture.
5. **Smart cast on `var`** — Kotlin can't smart-cast mutable variables. Look for `if (x != null) x.field` where x is a `var`.

### Medium (data integrity)
6. **`toFloat()` / `toInt()` on user/API strings** — can throw NumberFormatException. Should use `toFloatOrNull()` / `toIntOrNull()` with fallback.
7. **Division without zero guard** — `/ (list.size - 1)` when list could have 0 or 1 elements.

## Output Format

Group findings by severity (Critical > High > Medium). For each:
- File path and line number
- Code snippet
- Suggested fix (one-liner)
- Skip count: how many safe instances were filtered out

## After Audit
- Report total count per category
- Highlight top 5 highest-risk items to fix first
- Note any new pattern categories discovered
