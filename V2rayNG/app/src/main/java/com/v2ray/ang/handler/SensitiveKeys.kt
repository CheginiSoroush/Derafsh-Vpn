package com.v2ray.ang.handler


/**
 * Keys whose MMKV entries must never ride Google auto-backup (API 24-30) or
 * cloud backup / device-to-device transfer (API 31+) — O10 remediation.
 *
 * Before this file existed the app shipped `allowBackup="true"` with no
 * extraction rules, so the OS copied every MMKV file — including the WebDAV
 * credential ([KEY_WEBDAV_CONFIG] — a plaintext password). Per-key exclusion is
 * impossible at file granularity (the WebDAV key lived in MAIN next to every
 * server profile), so it lives in a
 * dedicated MMKV instance ([ID_SENSITIVE]) whose FILE — and only that file —
 * is excluded by `res/xml/backup_rules.xml` and
 * `res/xml/data_extraction_rules.xml`. Everything else keeps backing up, so
 * device migration stays one tap.
 *
 * The in-app manual backup path (BackupViewModel / WebDavAutoBackup via
 * MMKV.backupAllToDirectory + MMKV.restoreAllFromDirectory) is user-initiated
 * and intentionally still carries this instance: it is how a user restores
 * their own WebDAV configuration onto a new phone.
 *
 * Pure JVM seam: this file holds the DECISION (which keys, which types, which
 * instance). The MMKV I/O that executes it (MmkvManager routing + one-time
 * migration) is Android-bound and covered by real CI.
 */
internal const val ID_SENSITIVE = "derafsh_sensitive"

/** The WebDAV connection record (plaintext password inside). MAIN-hosted pre-O10. */
internal const val KEY_WEBDAV_CONFIG = "WEBDAV_CONFIG"

/** Settings-file keys that must move to (or be written straight to) [ID_SENSITIVE]. */
internal val SENSITIVE_SETTINGS_KEYS: Set<String> = setOf()

/** The subset of [SENSITIVE_SETTINGS_KEYS] stored as Long (everything else is a string). */
internal val SENSITIVE_LONG_KEYS: Set<String> = setOf()

/** Every key hosted by [ID_SENSITIVE] — settings keys plus the MAIN-hosted WebDAV record. */
internal val SENSITIVE_KEYS: Set<String> = SENSITIVE_SETTINGS_KEYS + KEY_WEBDAV_CONFIG

/** How the one-time migration must copy a legacy value into [ID_SENSITIVE]. */
internal enum class SensitiveMigrationKind { STRING, LONG }

/**
 * Pure migration decision (the JVM-testable seam of the O10 move): given a key
 * found in one of the legacy hosting instances, return the type the migration
 * must decode it as, or null when the key is not sensitive and must never be
 * touched by the migration. [MmkvManager.migrateSecretsInto] consumes this for
 * the Long-vs-String typing; the WebDAV record is unconditionally a JSON string.
 */
internal fun sensitiveMigrationKindFor(key: String): SensitiveMigrationKind? = when (key) {
    KEY_WEBDAV_CONFIG -> SensitiveMigrationKind.STRING
    in SENSITIVE_SETTINGS_KEYS ->
        if (key in SENSITIVE_LONG_KEYS) SensitiveMigrationKind.LONG else SensitiveMigrationKind.STRING
    else -> null
}
