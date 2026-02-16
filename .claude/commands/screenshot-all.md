Walk through every reachable app screen via ADB and capture screenshots for visual verification.

## Prerequisites
- ADB connected (`adb devices` shows device)
- App installed (`com.tribixbite.stoatally.debug`)
- Screenshots saved to `~/storage/shared/DCIM/Screenshots/stoat-audit/`

## Screen List

### Pre-login (no auth required)
1. Greeting screen (default launch)
2. Login screen (tap Log In)
3. Sign Up screen (tap Sign Up)

### Post-login (requires active session)
4. Channel list / server sidebar
5. Channel view with messages
6. Server context sheet (long-press server)
7. Channel context sheet (long-press channel)
8. User info sheet (tap user avatar in message)
9. Member list sheet (tap member count in channel header)
10. Settings screen (gear icon)
11. Profile settings
12. Notification settings
13. Push notification settings
14. Server settings (from server context)
15. Role management
16. Ban management
17. Channel permissions
18. Message search (from channel toolbar)
19. Server search (from server context)
20. Friends screen
21. Emoji picker (tap emoji button in message input)

## Process

For each screen:
1. Navigate via `adb shell am start` or `adb shell input tap` coordinates
2. Wait 2s for animations/loading
3. `adb shell screencap -p /sdcard/screenshot.png`
4. Pull and resize: ensure no dimension >= 2000px and size < 4MB
5. Save as `{number}_{screen_name}.png`
6. Return to previous screen

## After Capture
- Review each screenshot for:
  - Layout issues (overlapping elements, clipped text)
  - Dark mode consistency (light backgrounds, wrong colors)
  - Missing content (empty states, broken images)
  - Touch target size (minimum 48dp for accessibility)
- Report any issues found with screenshot reference
- Note screens that couldn't be reached (e.g. need login credentials)
