package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.RulesetItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * JVM tests for [SettingsManager.normalizeImportedRulesetIds]: clipboard/QR import
 * content is untrusted, and the routing UI keys its list items by RulesetItem.id —
 * a blank or duplicated id reaching the stored list crashes LazyColumn recomposition
 * ("Key was already used"). The normalizer must mint a fresh UUID for every imported
 * id that is blank or already claimed, and keep everything else untouched.
 */
class RulesetImportIdGuardTest {

    @Test
    fun `unique non-blank imported ids are preserved in order`() {
        val imported = listOf(
            RulesetItem(id = "a", remarks = "r1"),
            RulesetItem(id = "b", remarks = "r2"),
            RulesetItem(id = "c", remarks = "r3")
        )

        val result = SettingsManager.normalizeImportedRulesetIds(emptyList(), imported)

        assertEquals(listOf("a", "b", "c"), result.map { it.id })
        assertEquals(imported, result)
    }

    @Test
    fun `imported id colliding with a kept item is replaced with a fresh unique id`() {
        val kept = listOf(RulesetItem(id = "k1"), RulesetItem(id = "k2"))
        val imported = listOf(RulesetItem(id = "k1", remarks = "re-import of locked rule"))

        val result = SettingsManager.normalizeImportedRulesetIds(kept, imported)

        assertEquals(1, result.size)
        assertNotEquals("k1", result[0].id)
        assertTrue(result[0].id.isNotBlank())
        val allIds = kept.map { it.id } + result.map { it.id }
        assertEquals(allIds.size, allIds.toSet().size)
    }

    @Test
    fun `duplicates within the import keep only the first occurrence`() {
        val imported = listOf(
            RulesetItem(id = "a", remarks = "first"),
            RulesetItem(id = "a", remarks = "second"),
            RulesetItem(id = "b", remarks = "third"),
            RulesetItem(id = "a", remarks = "fourth")
        )

        val result = SettingsManager.normalizeImportedRulesetIds(emptyList(), imported)

        assertEquals(4, result.size)
        assertEquals("a", result[0].id)
        assertEquals("b", result[2].id)
        assertNotEquals("a", result[1].id)
        assertNotEquals("a", result[3].id)
        assertNotEquals(result[1].id, result[3].id)
        assertEquals(4, result.map { it.id }.toSet().size)
    }

    @Test
    fun `blank imported ids get fresh distinct ids`() {
        val imported = listOf(
            RulesetItem(id = "", remarks = "hand-crafted entry"),
            RulesetItem(id = "   ", remarks = "hand-crafted entry 2"),
            RulesetItem(id = "real", remarks = "third")
        )

        val result = SettingsManager.normalizeImportedRulesetIds(emptyList(), imported)

        assertEquals(3, result.size)
        assertEquals("real", result[2].id)
        val minted = listOf(result[0].id, result[1].id)
        minted.forEach { assertTrue(it.isNotBlank()) }
        assertNotEquals(minted[0], minted[1])
    }

    @Test
    fun `original imported items are not mutated`() {
        val source = RulesetItem(id = "dup")
        val imported = listOf(source, RulesetItem(id = "dup"))

        SettingsManager.normalizeImportedRulesetIds(emptyList(), imported)

        assertEquals("dup", source.id)
    }

    @Test
    fun `empty inputs yield empty result`() {
        assertTrue(SettingsManager.normalizeImportedRulesetIds(emptyList(), emptyList()).isEmpty())
    }
}
