Add a new screen to the Stoat Android app. The user will describe the screen's purpose.

Steps:
1. Create a new Kotlin file in `app/src/main/java/chat/stoat/screens/` (appropriate subdirectory)
2. Follow the existing Compose screen pattern:
   - `@OptIn(ExperimentalMaterial3Api::class)` if using Material3 experimental APIs
   - `@Composable` function with navigation parameters
   - ViewModel with `mutableStateOf` for reactive state
   - TopAppBar with back navigation
   - Proper error handling and loading states
3. Add the navigation route in `activities/MainActivity.kt`:
   - Add `composable("route_name/{param}")` in the NavHost
   - Wire up navigation parameters
4. Add the entry point (button/menu item) in the appropriate existing screen
5. Add string resources for all UI text in `res/values/strings.xml`
6. Update `docs/specs/` if the feature warrants a spec document
7. Build with `./build-and-install.sh` to verify compilation
