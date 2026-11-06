package com.v2ray.ang.handler

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R

/**
 * Derafsh source switcher: a pool of built-in free-config feeds presented to the user as
 * neutral numbered entries ("Derafsh 1", "Derafsh 2", …) — source URLs are never surfaced
 * in the UI. Exactly one source is materialized as the built-in subscription
 * ([AppConfig.DEFAULT_SUBSCRIPTION_ID]) and only that one is ever refreshed; switching
 * rewrites its URL and triggers an immediate update so the previous pool's servers are
 * replaced in place under the same subscription id.
 *
 * Task 9 adds the premium pool: while a valid license unlocks it, the built-in
 * subscription can point at the locked license-api endpoint instead of a free feed
 * ([isPremiumSourceActive]); losing the license rewrites it back to the last free source.
 */
object DerafshSourceManager {

    /** UI index reported while the premium pool is the active source (no free radio selected). */
    const val PREMIUM_UI_INDEX = -1

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

    /** Index for UI purposes: premium active → [PREMIUM_UI_INDEX], else the free index. */
    fun activeUiIndex(): Int =
        if (isPremiumSourceActive()) PREMIUM_UI_INDEX else activeIndex()

    fun setActiveIndex(index: Int) {
        MmkvManager.encodeSettings(AppConfig.PREF_DERAFSH_ACTIVE_SOURCE, index.toString())
    }

    /** Whether the built-in subscription currently points at the locked premium pool. */
    fun isPremiumSourceActive(): Boolean =
        MmkvManager.decodeSettingsString(AppConfig.PREF_DERAFSH_PREMIUM_SOURCE) == "1"

    fun setPremiumSourceActive(active: Boolean) {
        if (active) {
            MmkvManager.encodeSettings(AppConfig.PREF_DERAFSH_PREMIUM_SOURCE, "1")
        } else {
            MmkvManager.encodeSettings(AppConfig.PREF_DERAFSH_PREMIUM_SOURCE, null as String?)
        }
    }

    /**
     * License died (server answered inactive/revoked — see [LicenseManager.refresh]):
     * if the built-in subscription still points at the locked premium endpoint, rewrite
     * it back to the last free source so the next scheduled refresh repopulates free
     * configs. Pure MMKV — safe from the :bg worker; UI-side callers may additionally
     * trigger an immediate fetch.
     */
    fun onLicenseLost(context: Context) {
        if (!isPremiumSourceActive()) return
        setPremiumSourceActive(false)
        val subId = AppConfig.DEFAULT_SUBSCRIPTION_ID
        val item = MmkvManager.decodeSubscription(subId) ?: return
        item.url = sourceUrl(activeIndex())
        item.remarks = context.getString(R.string.derafsh_source_name, activeIndex() + 1)
        MmkvManager.encodeSubscription(subId, item)
    }
}
