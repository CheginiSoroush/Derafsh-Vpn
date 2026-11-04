package com.v2ray.ang.ui.main

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * Request ownership for the speed-test flows: a late reply must not complete a newer test.
 * Bulk requests can be STARTED from IO threads (startup maintenance, source switch and the
 * fastest-server pick all run off the main thread) while replies are collected on the main
 * thread, so every field is an atomic reference — plain vars would race across threads.
 */
internal class MainTestRequests {
    data class Bulk(val id: String, val groupId: String)

    private val current = AtomicReference<String?>(null)
    private val bulkRef = AtomicReference<Bulk?>(null)

    val bulk: Bulk?
        get() = bulkRef.get()

    val isTesting: Boolean
        get() = current.get() != null || bulkRef.get() != null

    fun beginCurrent(): String = UUID.randomUUID().toString().also { current.set(it) }

    fun completeCurrent(id: String): Boolean = current.compareAndSet(id, null)

    fun invalidateCurrent() {
        current.set(null)
    }

    fun beginBulk(groupId: String): Bulk =
        Bulk(UUID.randomUUID().toString(), groupId).also { bulkRef.set(it) }

    fun completeBulk(id: String): Bulk? {
        while (true) {
            val request = bulkRef.get() ?: return null
            if (request.id != id) return null
            if (bulkRef.compareAndSet(request, null)) return request
        }
    }

    fun cancelBulk() {
        bulkRef.set(null)
    }
}
