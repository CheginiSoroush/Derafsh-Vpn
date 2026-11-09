package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig

/**
 * Derafsh source switcher: a pool of built-in free-config feeds presented to the user as
 * neutral numbered entries ("Derafsh 1", "Derafsh 2", …) — source URLs are never surfaced
 * in the UI. Exactly one source is materialized as the built-in subscription
 * ([AppConfig.DEFAULT_SUBSCRIPTION_ID]) and only that one is ever refreshed; switching
 * rewrites its URL and triggers an immediate update so the previous pool's servers are
 * replaced in place under the same subscription id.
 */
object DerafshSourceManager {

    /** All built-in source URLs. Index 0 is the default on fresh installs. */
    val SOURCE_URLS: List<String> get() = AppConfig.DERAFSH_SOURCE_URLS

    fun count(): Int = SOURCE_URLS.size

    fun sourceUrl(index: Int): String =
        SOURCE_URLS[index.coerceIn(0, SOURCE_URLS.lastIndex)]

    /** Index of the currently active free source (persisted; defaults to 0). */
    fun activeIndex(): Int =
        MmkvManager.decodeSettingsString(AppConfig.PREF_DERAFSH_ACTIVE_SOURCE)
            ?.toIntOrNull()
            ?.coerceIn(0, SOURCE_URLS.lastIndex)
            ?: 0

    fun setActiveIndex(index: Int) {
        MmkvManager.encodeSettings(AppConfig.PREF_DERAFSH_ACTIVE_SOURCE, index.toString())
    }

}
