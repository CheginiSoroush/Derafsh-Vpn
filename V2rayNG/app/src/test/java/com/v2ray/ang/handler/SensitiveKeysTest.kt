package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig.PREF_IS_BOOTED
import com.v2ray.ang.AppConfig.PREF_ROUTING_RULESET
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * JVM tests for the O10 backup-posture hardening's pure seam: exactly which keys
 * live in the backup-excluded MMKV instance ("derafsh_sensitive"), which of them
 * are Longs, and how the one-time migration decides to copy each legacy value.
 *
 * The MMKV I/O that executes these decisions (MmkvManager routing + migration) is
 * Android-bound and covered by real CI (D12/D34 dead-seam precedent — no MMKV
 * native in JVM tests).
 */
class SensitiveKeysTest {

    @Test
    fun `sensitive instance id matches the paths excluded by the backup rule XMLs`() {
        // res/xml/backup_rules.xml and res/xml/data_extraction_rules.xml exclude
        // mmkv/derafsh_sensitive (+ .crc) by literal path — if this pin drifts,
        // the XMLs silently stop excluding anything (or exclude the wrong file).
        assertEquals("derafsh_sensitive", ID_SENSITIVE)
    }

    @Test
    fun `sensitive set is exactly the WebDAV record`() {
        assertEquals(
            setOf("WEBDAV_CONFIG"),
            SENSITIVE_KEYS,
        )
        assertEquals(1, SENSITIVE_KEYS.size, "no key may join or leave the excluded set unnoticed")
    }

    @Test
    fun `long-typed subset stays within the settings-hosted sensitive keys`() {
        assertEquals(setOf<String>(), SENSITIVE_LONG_KEYS)
        assertTrue(
            SENSITIVE_LONG_KEYS.all { it in SENSITIVE_SETTINGS_KEYS },
            "long subset must be a subset of the settings-hosted sensitive keys",
        )
        assertTrue(
            SENSITIVE_SETTINGS_KEYS.all { it in SENSITIVE_KEYS },
            "settings-hosted sensitive keys must all ride the sensitive instance",
        )
    }

    @Test
    fun `every sensitive key resolves to a migration kind - copy can never be skipped`() {
        for (key in SENSITIVE_KEYS) {
            assertTrue(
                sensitiveMigrationKindFor(key) != null,
                "sensitive key $key must be decidable by the migration, never null",
            )
        }
    }

    @Test
    fun `webdav record migrates as STRING`() {
        assertEquals(SensitiveMigrationKind.STRING, sensitiveMigrationKindFor("WEBDAV_CONFIG"))
    }

    @Test
    fun `non-sensitive keys are invisible to the migration`() {
        assertNull(sensitiveMigrationKindFor(PREF_IS_BOOTED))
        assertNull(sensitiveMigrationKindFor(PREF_ROUTING_RULESET))
        assertNull(sensitiveMigrationKindFor("SELECTED_SERVER"))
        assertNull(sensitiveMigrationKindFor(""))
    }
}
