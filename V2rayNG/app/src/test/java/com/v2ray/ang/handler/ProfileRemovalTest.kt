package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProfileRemovalTest {

    @Test
    fun `updatedIndex removes only the targeted guids and preserves order`() {
        val result = ProfileRemoval.updatedIndex(
            currentList = listOf("a", "b", "c", "d"),
            targets = listOf("b", "d"),
        )

        assertEquals(listOf("a", "c"), result)
    }

    @Test
    fun `updatedIndex returns an equal list when nothing matches`() {
        val current = listOf("a", "b", "c")

        val result = ProfileRemoval.updatedIndex(current, listOf("x", "y"))

        assertEquals(current, result)
    }

    @Test
    fun `updatedIndex with empty targets keeps the list untouched`() {
        val current = listOf("a", "b")

        val result = ProfileRemoval.updatedIndex(current, emptyList())

        assertEquals(current, result)
    }

    @Test
    fun `updatedIndex can empty the whole list`() {
        val result = ProfileRemoval.updatedIndex(
            currentList = listOf("a", "b"),
            targets = listOf("a", "b"),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `selectionInvalidated is true when the selection is among the targets`() {
        assertTrue(ProfileRemoval.selectionInvalidated("sel", listOf("a", "sel")))
    }

    @Test
    fun `selectionInvalidated is false when the selection survives`() {
        assertFalse(ProfileRemoval.selectionInvalidated("sel", listOf("a", "b")))
    }

    @Test
    fun `selectionInvalidated is false without a selection`() {
        assertFalse(ProfileRemoval.selectionInvalidated(null, listOf("a")))
    }

    @Test
    fun `selectionInvalidated is false for empty targets`() {
        assertFalse(ProfileRemoval.selectionInvalidated("sel", emptyList()))
    }
}
