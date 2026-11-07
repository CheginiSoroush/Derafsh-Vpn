package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SubscriptionBatchTest {

    @Test
    fun `null payload imports nothing`() {
        val imported = mutableListOf<String>()

        val count = SubscriptionBatch.countImports(
            servers = null,
            isValidSubUrl = { true },
            importOne = { imported.add(it); 1 },
        )

        assertEquals(0, count)
        assertEquals(emptyList<String>(), imported)
    }

    @Test
    fun `lines failing validation are skipped without calling the importer`() {
        val imported = mutableListOf<String>()

        val count = SubscriptionBatch.countImports(
            servers = "https://a.example/sub\nnot a url\nhttps://b.example/sub",
            isValidSubUrl = { it.startsWith("https://") },
            importOne = { imported.add(it); 1 },
        )

        assertEquals(2, count)
        assertEquals(listOf("https://a.example/sub", "https://b.example/sub"), imported)
    }

    @Test
    fun `duplicate lines are imported only once`() {
        val imported = mutableListOf<String>()

        val count = SubscriptionBatch.countImports(
            servers = "https://a.example/sub\nhttps://a.example/sub\nhttps://a.example/sub",
            isValidSubUrl = { true },
            importOne = { imported.add(it); 1 },
        )

        assertEquals(1, count)
        assertEquals(listOf("https://a.example/sub"), imported)
    }

    @Test
    fun `one throwing import does not abort the remaining lines`() {
        val imported = mutableListOf<String>()

        val count = SubscriptionBatch.countImports(
            servers = "https://good1.example/sub\nhttps://bad.example/sub\nhttps://good2.example/sub",
            isValidSubUrl = { true },
            importOne = { url ->
                if (url.contains("bad")) throw IllegalArgumentException("malformed URL")
                imported.add(url)
                1
            },
        )

        assertEquals(2, count)
        assertEquals(listOf("https://good1.example/sub", "https://good2.example/sub"), imported)
    }

    @Test
    fun `onError receives the failing line and its exception`() {
        val failures = mutableListOf<Pair<String, Exception>>()

        SubscriptionBatch.countImports(
            servers = "https://bad.example/sub",
            isValidSubUrl = { true },
            importOne = { throw IllegalStateException("boom") },
            onError = { line, e -> failures.add(line to e) },
        )

        assertEquals(1, failures.size)
        assertEquals("https://bad.example/sub", failures[0].first)
        assertEquals("boom", failures[0].second.message)
    }

    @Test
    fun `importer counts other than one are summed`() {
        val count = SubscriptionBatch.countImports(
            servers = "https://a.example/sub\nhttps://b.example/sub",
            isValidSubUrl = { true },
            importOne = { url -> if (url.contains("a.")) 0 else 3 },
        )

        assertEquals(3, count)
    }
}
