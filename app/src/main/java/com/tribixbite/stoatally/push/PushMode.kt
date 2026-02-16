package com.tribixbite.stoatally.push

/**
 * Push notification delivery mode selection.
 * Determines how push notifications reach the device.
 */
enum class PushMode(val key: String, val displayName: String) {
    /** FCM via stoatcord-bot relay — primary, always available */
    BOT_FCM("fcm", "Direct (Bot Relay)"),

    /** UnifiedPush via ntfy or other UP distributor (e.g., ntfy) */
    UNIFIED_PUSH("unifiedpush", "UnifiedPush"),

    /** Legacy: direct Stoat backend push (currently broken — FCM unconfigured) */
    BACKEND("backend", "Backend (Stoat Server)"),

    /** Push notifications disabled */
    OFF("off", "Off");

    companion object {
        fun fromKey(key: String?): PushMode {
            return entries.find { it.key == key } ?: BOT_FCM
        }
    }
}
