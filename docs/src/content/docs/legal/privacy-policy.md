---
title: Privacy Policy
description: Privacy policy for Stoat and the Stoatcord Bot Discord application.
---

**Effective Date:** February 14, 2026
**Last Updated:** February 14, 2026

## Introduction

This privacy policy explains how the Stoat for Android application and the Stoatcord Bot Discord application ("we", "our", "the Services") collect, use, and protect your information.

## Information We Collect

### Stoat for Android

- **Account credentials**: Your Stoat/Revolt authentication token, stored locally on your device. This is never transmitted to any third party.
- **Push notification tokens**: Firebase Cloud Messaging tokens used solely for delivering push notifications.
- **Device information**: Crash reports via Sentry may include device model, OS version, and stack traces to help diagnose issues.

### Stoatcord Bot

When the Stoatcord Bot is added to a Discord server, it accesses:

- **Server metadata**: Server name, ID, icon, member count, and channel list — used to facilitate channel migration and message bridging.
- **Message content**: Messages in bridged channels are relayed between Discord and Stoat. Messages are not stored permanently; they are forwarded in real time and may be temporarily held in memory for processing.
- **User display names and avatars**: Used to attribute relayed messages to the correct sender via webhooks. No user credentials or tokens are collected.

## How We Use Your Information

- **Channel migration**: Server and channel metadata is used to create matching channels on Stoat when you use the import wizard.
- **Message bridging**: Message content is relayed between linked Discord and Stoat channels so conversations stay synchronized.
- **Crash reporting**: Error data helps us identify and fix bugs.
- **Push notifications**: FCM tokens deliver message notifications to your device.

## Data Storage

- **On-device**: Stoat for Android stores all user data locally on the device (authentication tokens, preferences, cached messages).
- **Stoatcord Bot**: The bot maintains a local SQLite database with channel link mappings and migration logs. No message content is persisted in this database.
- **Third-party services**: Crash reports are sent to Sentry. Push notification tokens are sent to Firebase Cloud Messaging. No other third-party services receive your data.

## Data Sharing

We do not sell, trade, or share your personal information with third parties, except:

- **Sentry**: Anonymous crash reports for debugging.
- **Firebase**: Push notification delivery tokens.
- **Discord API**: The bot interacts with Discord's API as required for its bridging functionality.
- **Stoat/Revolt API**: The bot interacts with the Stoat API to relay messages and create channels.

## Data Retention

- **Bot database**: Channel link mappings are retained as long as the bot is in your server. Migration logs are retained for troubleshooting. You can request deletion by removing the bot from your server.
- **On-device data**: Cleared when you uninstall the app or clear app data.
- **Crash reports**: Retained by Sentry per their standard retention policy (90 days).

## Your Rights

- **Remove the bot**: Kick the Stoatcord Bot from your Discord server at any time to stop all data access.
- **Uninstall the app**: Removes all locally stored data.
- **Request data deletion**: Contact us to request deletion of any data associated with your server.

## Security

We use HTTPS for all API communications. Authentication tokens are stored securely on-device. The bot API supports optional API key authentication. No passwords are ever stored or transmitted in plaintext.

## Children's Privacy

The Services are not directed at children under 13. We do not knowingly collect information from children under 13.

## Changes to This Policy

We may update this privacy policy from time to time. Changes will be reflected in the "Last Updated" date above and published to this page.

## Contact

For questions about this privacy policy, open an issue at [github.com/tribixbite/stoat-android](https://github.com/tribixbite/stoat-android/issues) or contact the project maintainer.
