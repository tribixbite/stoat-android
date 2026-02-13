#!/bin/bash
# Comprehensive search API benchmark for Stoat
# Tests endpoint variations, parameters, and response formats

HEADER_FILE="$TMPDIR/search_header.txt"
CH="01KH9C6QEYSJMQCXB78NPVKCCC"  # General channel (has messages)
CH2="01KHA5XVJM598BCJCWSR9S2MJ6" # Another channel
TIMEOUT=30
RESULTS_FILE="$TMPDIR/search_benchmark_results.txt"

> "$RESULTS_FILE"

bench() {
    local label="$1"
    local url="$2"
    local data="$3"
    local start end elapsed status size body

    start=$(date +%s%N)
    body=$(curl -s -w "\n%{http_code}\n%{size_download}" \
        --connect-timeout 10 --max-time $TIMEOUT \
        -X POST "$url" \
        -H "Content-Type: application/json" \
        -H @"$HEADER_FILE" \
        -d "$data" 2>/dev/null)
    local exit_code=$?
    end=$(date +%s%N)
    elapsed=$(( (end - start) / 1000000 ))

    if [ $exit_code -ne 0 ]; then
        printf "%-45s | %5sms | EXIT:%d | curl error\n" "$label" "$elapsed" "$exit_code" | tee -a "$RESULTS_FILE"
        return
    fi

    # Extract status code and size from last two lines
    status=$(echo "$body" | tail -2 | head -1)
    size=$(echo "$body" | tail -1)
    # Get first 200 chars of response body (everything except last 2 lines)
    local resp_preview
    resp_preview=$(echo "$body" | head -n -2 | head -c 200)

    printf "%-45s | %5sms | HTTP:%s | %5s bytes | %.100s\n" "$label" "$elapsed" "$status" "$size" "$resp_preview" | tee -a "$RESULTS_FILE"
}

echo "=== Stoat Search API Benchmark ===" | tee -a "$RESULTS_FILE"
echo "Started: $(date)" | tee -a "$RESULTS_FILE"
echo "" | tee -a "$RESULTS_FILE"

echo "--- 1. API Version Discovery ---" | tee -a "$RESULTS_FILE"
bench "GET /0.8/" "https://api.stoat.chat/0.8/" '{}' &
# Try alternate API versions
for ver in "0.9" "1" "v1" "api" ""; do
    bench "GET /$ver/" "https://api.stoat.chat/$ver/" '{}' &
done
wait
echo "" | tee -a "$RESULTS_FILE"

echo "--- 2. Basic Search (General channel) ---" | tee -a "$RESULTS_FILE"
bench "query=hello, limit=1, no include_users" \
    "https://api.stoat.chat/0.8/channels/$CH/search" \
    '{"query":"hello","limit":1}'
bench "query=hello, limit=1, include_users=true" \
    "https://api.stoat.chat/0.8/channels/$CH/search" \
    '{"query":"hello","limit":1,"include_users":true}'
bench "query=hello, limit=5, no include_users" \
    "https://api.stoat.chat/0.8/channels/$CH/search" \
    '{"query":"hello","limit":5}'
bench "query=hello, limit=5, include_users=true" \
    "https://api.stoat.chat/0.8/channels/$CH/search" \
    '{"query":"hello","limit":5,"include_users":true}'
bench "query=hello, limit=10" \
    "https://api.stoat.chat/0.8/channels/$CH/search" \
    '{"query":"hello","limit":10}'
bench "query=hello, limit=25" \
    "https://api.stoat.chat/0.8/channels/$CH/search" \
    '{"query":"hello","limit":25}'
bench "query=hello, limit=50" \
    "https://api.stoat.chat/0.8/channels/$CH/search" \
    '{"query":"hello","limit":50}'
bench "query=hello, limit=100" \
    "https://api.stoat.chat/0.8/channels/$CH/search" \
    '{"query":"hello","limit":100}'
echo "" | tee -a "$RESULTS_FILE"

echo "--- 3. Sort Variations ---" | tee -a "$RESULTS_FILE"
for sort in "Relevance" "Latest" "Oldest"; do
    bench "sort=$sort, limit=5" \
        "https://api.stoat.chat/0.8/channels/$CH/search" \
        "{\"query\":\"hello\",\"limit\":5,\"sort\":\"$sort\"}"
done
echo "" | tee -a "$RESULTS_FILE"

echo "--- 4. Pinned Search ---" | tee -a "$RESULTS_FILE"
bench "pinned=true only" \
    "https://api.stoat.chat/0.8/channels/$CH/search" \
    '{"pinned":true,"limit":5}'
bench "pinned=true + include_users" \
    "https://api.stoat.chat/0.8/channels/$CH/search" \
    '{"pinned":true,"limit":5,"include_users":true}'
bench "pinned=true + query (should conflict)" \
    "https://api.stoat.chat/0.8/channels/$CH/search" \
    '{"pinned":true,"query":"hello","limit":5}'
echo "" | tee -a "$RESULTS_FILE"

echo "--- 5. Wildcard / Empty Queries ---" | tee -a "$RESULTS_FILE"
bench "query=space (wildcard)" \
    "https://api.stoat.chat/0.8/channels/$CH/search" \
    '{"query":" ","limit":3}'
bench "query=* (asterisk)" \
    "https://api.stoat.chat/0.8/channels/$CH/search" \
    '{"query":"*","limit":3}'
bench "query=. (dot)" \
    "https://api.stoat.chat/0.8/channels/$CH/search" \
    '{"query":".","limit":3}'
bench "no query field at all" \
    "https://api.stoat.chat/0.8/channels/$CH/search" \
    '{"limit":3}'
bench "empty string query" \
    "https://api.stoat.chat/0.8/channels/$CH/search" \
    '{"query":"","limit":3}'
echo "" | tee -a "$RESULTS_FILE"

echo "--- 6. MongoDB text search syntax ---" | tee -a "$RESULTS_FILE"
bench "multi-word OR: hello welcome" \
    "https://api.stoat.chat/0.8/channels/$CH/search" \
    '{"query":"hello welcome","limit":5}'
bench "exact phrase: \"hello guys\"" \
    "https://api.stoat.chat/0.8/channels/$CH/search" \
    '{"query":"\"hello guys\"","limit":5}'
bench "negation: hello -guys" \
    "https://api.stoat.chat/0.8/channels/$CH/search" \
    '{"query":"hello -guys","limit":5}'
echo "" | tee -a "$RESULTS_FILE"

echo "--- 7. Different Channel ---" | tee -a "$RESULTS_FILE"
bench "channel2, query=hello, limit=5" \
    "https://api.stoat.chat/0.8/channels/$CH2/search" \
    '{"query":"hello","limit":5}'
bench "channel2, wildcard, limit=5" \
    "https://api.stoat.chat/0.8/channels/$CH2/search" \
    '{"query":" ","limit":5}'
echo "" | tee -a "$RESULTS_FILE"

echo "--- 8. Pagination (before/after) ---" | tee -a "$RESULTS_FILE"
bench "before=01KHA93W7BWKP73K0T1TJNAHTV" \
    "https://api.stoat.chat/0.8/channels/$CH/search" \
    '{"query":"hello","limit":5,"before":"01KHA93W7BWKP73K0T1TJNAHTV"}'
bench "after=01KHA8CX1GPZYT710HVRA51N1Y" \
    "https://api.stoat.chat/0.8/channels/$CH/search" \
    '{"query":"hello","limit":5,"after":"01KHA8CX1GPZYT710HVRA51N1Y"}'
echo "" | tee -a "$RESULTS_FILE"

echo "--- 9. Unsupported/Extended Parameters ---" | tee -a "$RESULTS_FILE"
bench "author_id param (if supported)" \
    "https://api.stoat.chat/0.8/channels/$CH/search" \
    '{"query":"hello","limit":5,"author_id":"01KH687388ZNY4DD6S1KN999K5"}'
bench "has param (if supported)" \
    "https://api.stoat.chat/0.8/channels/$CH/search" \
    '{"query":"hello","limit":5,"has":"attachment"}'
bench "content_type param (if supported)" \
    "https://api.stoat.chat/0.8/channels/$CH/search" \
    '{"query":"hello","limit":5,"content_type":"image"}'
bench "mentions param (if supported)" \
    "https://api.stoat.chat/0.8/channels/$CH/search" \
    '{"query":"hello","limit":5,"mentions":"01J956AMNB4Z2GWWGSXRM5REGD"}'
echo "" | tee -a "$RESULTS_FILE"

echo "--- 10. Revolt /search on alternate base URLs ---" | tee -a "$RESULTS_FILE"
bench "revolt.chat API" \
    "https://api.revolt.chat/channels/$CH/search" \
    '{"query":"hello","limit":3}'
bench "stoat.chat v0.9" \
    "https://api.stoat.chat/0.9/channels/$CH/search" \
    '{"query":"hello","limit":3}'
bench "stoat.chat v1" \
    "https://api.stoat.chat/v1/channels/$CH/search" \
    '{"query":"hello","limit":3}'
echo "" | tee -a "$RESULTS_FILE"

echo "--- 11. Response Format Test (full body) ---" | tee -a "$RESULTS_FILE"
echo "Full response with include_users=false, limit=2:" | tee -a "$RESULTS_FILE"
curl -s --connect-timeout 10 --max-time $TIMEOUT \
    -X POST "https://api.stoat.chat/0.8/channels/$CH/search" \
    -H "Content-Type: application/json" \
    -H @"$HEADER_FILE" \
    -d '{"query":"hello","limit":2}' 2>/dev/null | python3 -m json.tool 2>/dev/null || echo "(raw output above)" | tee -a "$RESULTS_FILE"
echo "" | tee -a "$RESULTS_FILE"

echo "Full response with include_users=true, limit=2:" | tee -a "$RESULTS_FILE"
curl -s --connect-timeout 10 --max-time $TIMEOUT \
    -X POST "https://api.stoat.chat/0.8/channels/$CH/search" \
    -H "Content-Type: application/json" \
    -H @"$HEADER_FILE" \
    -d '{"query":"hello","limit":2,"include_users":true}' 2>/dev/null | python3 -m json.tool 2>/dev/null || echo "(raw output above)" | tee -a "$RESULTS_FILE"
echo "" | tee -a "$RESULTS_FILE"

echo "Full response with pinned=true, limit=5:" | tee -a "$RESULTS_FILE"
curl -s --connect-timeout 10 --max-time $TIMEOUT \
    -X POST "https://api.stoat.chat/0.8/channels/$CH/search" \
    -H "Content-Type: application/json" \
    -H @"$HEADER_FILE" \
    -d '{"pinned":true,"limit":5}' 2>/dev/null | python3 -m json.tool 2>/dev/null || echo "(raw output above)" | tee -a "$RESULTS_FILE"

echo "" | tee -a "$RESULTS_FILE"
echo "=== Benchmark Complete ===" | tee -a "$RESULTS_FILE"
echo "Finished: $(date)" | tee -a "$RESULTS_FILE"
