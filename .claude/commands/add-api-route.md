Add a new API route to the Stoat Android client. The user will specify the endpoint details.

Steps:
1. Determine the correct route file under `app/src/main/java/chat/stoat/api/routes/` based on the resource type (channel, server, user, etc.)
2. Read the existing route file to understand patterns (serialization, error handling, cache updates)
3. Add the new route function following existing patterns:
   - Use `StoatHttp.{method}` for the HTTP call
   - Use `.api()` extension on the path string
   - Add `@Serializable` data classes for request/response bodies
   - Handle errors with try-catch on `StoatAPIError` deserialization
   - Update relevant caches (`StoatAPI.messageCache`, `StoatAPI.serverCache`, etc.)
   - Add KDoc comment documenting the endpoint and required permissions
4. If UI is needed, identify the appropriate sheet or screen to add the action
5. Add string resources for any new UI text
6. Build with `./build-and-install.sh` to verify compilation

Format for user input: `METHOD /path/to/endpoint - description`
Example: `PUT /channels/{id}/messages/{msgId}/pin - Pin a message`
