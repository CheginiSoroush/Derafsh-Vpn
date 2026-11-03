package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig

/**
 * First-launch onboarding gate for Derafsh.
 *
 * The onboarding carousel (app introduction + privacy-policy consent) shows once per
 * install, before MainActivity runs anything else (ads, maintenance, UI). It re-shows
 * automatically if [CONSENT_VERSION] is bumped after a material privacy-policy change,
 * so consent can be re-collected from existing installs too.
 */
object OnboardingManager {

    /** Bump to re-collect privacy-policy consent from all installs after policy changes. */
    private const val CONSENT_VERSION = 1

    fun needsOnboarding(): Boolean =
        MmkvManager.decodeSettingsInt(AppConfig.PREF_ONBOARDING_DONE, 0) != 1 ||
                MmkvManager.decodeSettingsInt(AppConfig.PREF_POLICY_CONSENT_VERSION, 0) < CONSENT_VERSION

    /** Persists completion: onboarding seen + current consent version accepted. */
    fun markCompleted() {
        MmkvManager.encodeSettings(AppConfig.PREF_ONBOARDING_DONE, 1)
        MmkvManager.encodeSettings(AppConfig.PREF_POLICY_CONSENT_VERSION, CONSENT_VERSION)
    }
}
