package com.tribixbite.stoatally.api

import android.util.Log
import com.tribixbite.stoatally.persistence.KVStorage
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Runtime configuration for the current server instance.
 * All server URLs are resolved dynamically from the API root endpoint.
 * Persisted to KVStorage so the selected instance survives app restarts.
 */
object InstanceConfig {
    private const val TAG = "InstanceConfig"

    // KVStorage keys
    private const val KEY_API_URL = "instance_api_url"
    private const val KEY_WS_URL = "instance_ws_url"
    private const val KEY_FILES_URL = "instance_files_url"
    private const val KEY_PROXY_URL = "instance_proxy_url"
    private const val KEY_APP_URL = "instance_app_url"
    private const val KEY_VAPID = "instance_vapid"
    private const val KEY_INSTANCE_NAME = "instance_name"

    // Default Stoat instance values
    private const val DEFAULT_API = "https://api.stoat.chat/0.8"
    private const val DEFAULT_WS = "wss://events.stoat.chat"
    private const val DEFAULT_FILES = "https://cdn.stoatusercontent.com"
    private const val DEFAULT_PROXY = "https://proxy.stoatusercontent.com"
    private const val DEFAULT_APP = "https://stoat.chat"
    private const val DEFAULT_VAPID = "BJto1I_OZi8hOkMfQNQJfod2osWBqcOO7eEOqFMvCfqNhqgxqOr7URnxYKTR4N6sR3sTPywfHpEsPXhrU9zfZgg"

    // Mutable runtime URLs — read by the rest of the app
    var apiUrl: String = DEFAULT_API
        private set
    var wsUrl: String = DEFAULT_WS
        private set
    var filesUrl: String = DEFAULT_FILES
        private set
    var proxyUrl: String = DEFAULT_PROXY
        private set
    var appUrl: String = DEFAULT_APP
        private set
    var vapid: String = DEFAULT_VAPID
        private set
    var instanceName: String = "Stoat"
        private set

    /** Whether using a non-default instance */
    val isCustomInstance: Boolean
        get() = apiUrl != DEFAULT_API

    /**
     * Known public instances. API URL is the entry point — other URLs
     * are resolved automatically via the root config endpoint.
     */
    val knownInstances = listOf(
        KnownInstance(
            name = "Stoat",
            description = "Official instance",
            apiUrl = "https://api.stoat.chat/0.8",
        ),
        KnownInstance(
            name = "Stoat (Legacy)",
            description = "Revolt-era API endpoint",
            apiUrl = "https://api.revolt.chat",
        ),
    )

    /**
     * Load saved instance config from KVStorage on app startup.
     * If no custom instance is saved, defaults are used (no-op).
     */
    suspend fun loadFromStorage(kvStorage: KVStorage) {
        val savedApi = kvStorage.get(KEY_API_URL) ?: return // no custom instance saved
        apiUrl = savedApi
        wsUrl = kvStorage.get(KEY_WS_URL) ?: DEFAULT_WS
        filesUrl = kvStorage.get(KEY_FILES_URL) ?: DEFAULT_FILES
        proxyUrl = kvStorage.get(KEY_PROXY_URL) ?: DEFAULT_PROXY
        appUrl = kvStorage.get(KEY_APP_URL) ?: DEFAULT_APP
        vapid = kvStorage.get(KEY_VAPID) ?: DEFAULT_VAPID
        instanceName = kvStorage.get(KEY_INSTANCE_NAME) ?: "Custom"
        Log.i(TAG, "Loaded instance: $instanceName ($apiUrl)")
    }

    /**
     * Save the current instance config to KVStorage for persistence.
     */
    suspend fun saveToStorage(kvStorage: KVStorage) {
        kvStorage.set(KEY_API_URL, apiUrl)
        kvStorage.set(KEY_WS_URL, wsUrl)
        kvStorage.set(KEY_FILES_URL, filesUrl)
        kvStorage.set(KEY_PROXY_URL, proxyUrl)
        kvStorage.set(KEY_APP_URL, appUrl)
        kvStorage.set(KEY_VAPID, vapid)
        kvStorage.set(KEY_INSTANCE_NAME, instanceName)
    }

    /**
     * Clear saved instance config, reverting to defaults.
     */
    suspend fun clearFromStorage(kvStorage: KVStorage) {
        kvStorage.remove(KEY_API_URL)
        kvStorage.remove(KEY_WS_URL)
        kvStorage.remove(KEY_FILES_URL)
        kvStorage.remove(KEY_PROXY_URL)
        kvStorage.remove(KEY_APP_URL)
        kvStorage.remove(KEY_VAPID)
        kvStorage.remove(KEY_INSTANCE_NAME)
    }

    /**
     * Apply a resolved config to the runtime state.
     */
    fun apply(name: String, api: String, resolved: ResolvedConfig) {
        instanceName = name
        apiUrl = api
        wsUrl = resolved.ws
        filesUrl = resolved.features.autumn.url
        proxyUrl = resolved.features.january.url
        appUrl = resolved.app
        vapid = resolved.vapid
        Log.i(TAG, "Applied instance: $name (api=$api, ws=$wsUrl, files=$filesUrl)")
    }

    /**
     * Reset to default Stoat instance (in memory only — call clearFromStorage to persist).
     */
    fun resetToDefault() {
        instanceName = "Stoat"
        apiUrl = DEFAULT_API
        wsUrl = DEFAULT_WS
        filesUrl = DEFAULT_FILES
        proxyUrl = DEFAULT_PROXY
        appUrl = DEFAULT_APP
        vapid = DEFAULT_VAPID
    }

    /**
     * Test connection to an API URL by fetching the root config endpoint.
     * Returns the resolved config on success, or throws on failure.
     */
    suspend fun testConnection(apiBaseUrl: String): ResolvedConfig {
        val url = apiBaseUrl.trimEnd('/')
        // Use a standalone HTTP client to avoid polluting the main one
        val response: ResolvedConfig = StoatHttp.get("$url/").body()
        return response
    }
}

/** A known public instance that can be selected from the list */
data class KnownInstance(
    val name: String,
    val description: String,
    val apiUrl: String,
)

/**
 * Server root config response — mirrors the GET / endpoint schema.
 * Used for instance discovery and connection testing.
 */
@Serializable
data class ResolvedConfig(
    val revolt: String,
    val features: ResolvedFeatures,
    val ws: String,
    val app: String,
    val vapid: String,
)

@Serializable
data class ResolvedFeatures(
    val captcha: ResolvedCaptcha,
    val email: Boolean,
    @SerialName("invite_only") val inviteOnly: Boolean,
    val autumn: ResolvedService,
    val january: ResolvedService,
)

@Serializable
data class ResolvedCaptcha(
    val enabled: Boolean,
    val key: String = "",
)

@Serializable
data class ResolvedService(
    val enabled: Boolean,
    val url: String,
)
