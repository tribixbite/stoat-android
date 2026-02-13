Check the Stoat/Revolt API for a specific endpoint or feature. Test it with curl against the live API.

Steps:
1. Read the relevant route file to understand the current implementation
2. Test the endpoint with curl:
   ```
   curl -s -X METHOD "https://api.stoat.chat/0.8/ENDPOINT" \
     -H "x-session-token: TOKEN" \
     -H "Content-Type: application/json" \
     -d 'BODY'
   ```
   Note: Get the session token from the app's DataStore or use a test token
3. Document the response format, status codes, and any error responses
4. Compare with the Revolt API docs if available
5. Report any differences between documented and actual behavior
6. Note any undocumented query parameters or headers that work
