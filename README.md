<div align="center">
    <h1>Stoat for Android (Unofficial Fork)</h1>
    <p><strong>⚠️ This is an UNOFFICIAL, third-party fork.</strong></p>
    <p>Not affiliated with, endorsed by, or maintained by the <a href="https://stoat.chat">Stoat</a> team.</p>
    <br/>
    <div>
        <a href="https://play.google.com/store/apps/details?id=chat.revolt"><img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" width="200"></a>
        <br/>
        <small>(Google Play listing is the official app by the Stoat team, not this fork)</small>
    </div>
    <small>Google Play is a trademark of Google LLC.</small>
    <br/><br/>
</div>

> **Disclaimer**: This fork is developed independently for personal/educational use.
> It is not associated with Stoat, stoat.chat, or the original Revolt project.
> Use at your own risk. See the [Stoat terms](https://stoat.chat/terms) for third-party usage requirements.

## What is this?

This is an **unofficial fork** of the [Stoat for Android](https://github.com/stoatchat/for-android) client with additional features, bug fixes, and improvements not present in the official app. See the [Fork Changes](https://tribixbite.github.io/stoat-android/reference/fork-changes/) page for a full list of differences.

Key additions over upstream:
- **Message search** with filters (per-channel and server-wide)
- **Spoiler text** rendering (`||spoiler||`)
- **Server management** (roles, permissions, emoji, invites, bans)
- **Account management** (email, password, MFA/TOTP)
- **Moderation tools** (kick, ban, bulk delete, pin)
- **Copy text/ID** directly from message context menu
- **Mark as unread**, collapsible categories, and many bug fixes

## Stack

- [Kotlin](https://kotlinlang.org/)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
    - For some Material components, the View-based
      [Material Components Android](https://github.com/material-components/material-components-android)
      (MDC-Android) library is used.
- [Ktor](https://ktor.io/)
- [Dagger](https://dagger.dev/) with [Hilt](https://dagger.dev/hilt/)

## Resources

### This Fork

- [Fork Documentation & Changes](https://tribixbite.github.io/stoat-android/)
- [Feature Gap Analysis](https://tribixbite.github.io/stoat-android/reference/fork-changes/)

### Official Stoat

- [Stoat Website](https://stoat.chat)
- [Stoat Development Server](https://app.revolt.chat/invite/API)
- [Stoat Server](https://app.revolt.chat/invite/Testers)
- [General Contribution Guide](https://developers.revolt.chat/contrib.html)

## Quick Start

Open the project in Android Studio. You can then run the app on an emulator or a physical device by
running the `app` module.

For Termux ARM64 builds, use `./build-and-install.sh` (see [CLAUDE.md](./CLAUDE.md) for details).

## License

This fork inherits the license from the upstream repository. As a third-party fork, it complies with the AGPLv3 requirements by publishing source code publicly.
