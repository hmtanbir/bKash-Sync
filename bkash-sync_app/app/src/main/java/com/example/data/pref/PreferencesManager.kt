package com.example.data.pref

import android.content.Context
import android.content.SharedPreferences
import com.example.data.api.CryptoHelper

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("bkash_sync_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ENDPOINT = "api_endpoint"
        private const val KEY_API_KEY_ENCRYPTED = "api_key_encrypted"
        private const val KEY_IS_CONFIGURED = "is_configured"
        private const val KEY_USER_PHONE_NUMBER = "user_phone_number"
        private const val KEY_IS_DARK_MODE = "is_dark_mode"
        private const val KEY_SYNC_INTERVAL_SECONDS = "sync_interval_seconds"
        private const val KEY_AUTO_RECONCILE_ON_SWEEP = "auto_reconcile_on_sweep"
    }

    var autoReconcileOnSweep: Boolean
        get() = prefs.getBoolean(KEY_AUTO_RECONCILE_ON_SWEEP, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_RECONCILE_ON_SWEEP, value).apply()

    var syncIntervalSeconds: Int
        get() = prefs.getInt(KEY_SYNC_INTERVAL_SECONDS, 15)
        set(value) = prefs.edit().putInt(KEY_SYNC_INTERVAL_SECONDS, value).apply()

    var apiEndpoint: String
        get() = prefs.getString(KEY_ENDPOINT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ENDPOINT, value).apply()

    var apiKey: String
        get() {
            val encrypted = prefs.getString(KEY_API_KEY_ENCRYPTED, "") ?: ""
            return if (encrypted.isNotEmpty()) {
                CryptoHelper.decrypt(encrypted)
            } else {
                ""
            }
        }
        set(value) {
            val encrypted = CryptoHelper.encrypt(value)
            prefs.edit().putString(KEY_API_KEY_ENCRYPTED, encrypted).apply()
        }

    var userPhoneNumber: String
        get() = prefs.getString(KEY_USER_PHONE_NUMBER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_PHONE_NUMBER, value).apply()

    var isConfigured: Boolean
        get() = prefs.getBoolean(KEY_IS_CONFIGURED, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_CONFIGURED, value).apply()

    var isDarkMode: Boolean
        get() = prefs.getBoolean(KEY_IS_DARK_MODE, true)
        set(value) = prefs.edit().putBoolean(KEY_IS_DARK_MODE, value).apply()

    fun getVerifyUrl(): String {
        return getUrlForPath(apiEndpoint, "verify")
    }

    fun getDeltaSyncUrl(): String {
        return getUrlForPath(apiEndpoint, "synctransaction")
    }

    fun getSyncUrl(): String {
        return getUrlForPath(apiEndpoint, "transactions")
    }

    fun saveConfig(endpoint: String, key: String, userPhone: String) {
        apiEndpoint = endpoint
        apiKey = key
        userPhoneNumber = userPhone
        isConfigured = true
    }

    fun clearConfig() {
        prefs.edit()
            .remove(KEY_ENDPOINT)
            .remove(KEY_API_KEY_ENCRYPTED)
            .remove(KEY_USER_PHONE_NUMBER)
            .putBoolean(KEY_IS_CONFIGURED, false)
            .apply()
    }
}

fun getUrlForPath(inputUrl: String, path: String): String {
    var url = inputUrl.trim()
    if (url.isEmpty()) return ""
    
    // Prepend https:// if no schemes exist
    if (!url.startsWith("http://") && !url.startsWith("https://")) {
        url = "https://$url"
    }
    
    // Strip trailing paths to find the logical base URL
    while (url.endsWith("/")) {
        url = url.substring(0, url.length - 1)
    }
    if (url.endsWith("/transactions")) {
        url = url.substring(0, url.length - "/transactions".length)
    } else if (url.endsWith("/verify")) {
        url = url.substring(0, url.length - "/verify".length)
    }
    
    while (url.endsWith("/")) {
        url = url.substring(0, url.length - 1)
    }
    
    return "$url/$path"
}
