package com.v2ray.ang.handler

/**
 * Pure decision logic for subscription batch import. Kept free of storage and network
 * dependencies so the batching semantics can be unit-tested without MMKV.
 */
internal object SubscriptionBatch {

    /**
     * Applies [importOne] to every distinct line of [servers] that passes [isValidSubUrl]
     * and sums the reported counts.
     *
     * Each import is isolated: one throwing line — e.g. a scheme-less URL that survives
     * validation but fails URI parsing — is reported to [onError] and contributes 0,
     * instead of aborting the whole batch and silently dropping every remaining line
     * (the same one-bad-subscription failure class [AngConfigManager.updateConfigViaSub]
     * documents and guards against for the refresh path).
     *
     * @param servers The raw payload (subscription URLs, one per line).
     * @param isValidSubUrl Line filter.
     * @param importOne Imports a single URL, returning the number of subscriptions added.
     * @param onError Invoked with the failing line and its exception.
     * @return The sum of successful imports.
     */
    fun countImports(
        servers: String?,
        isValidSubUrl: (String) -> Boolean,
        importOne: (String) -> Int,
        onError: (String, Exception) -> Unit = { _, _ -> },
    ): Int {
        if (servers == null) return 0
        var count = 0
        servers.lines()
            .distinct()
            .forEach { line ->
                if (isValidSubUrl(line)) {
                    count += try {
                        importOne(line)
                    } catch (e: Exception) {
                        onError(line, e)
                        0
                    }
                }
            }
        return count
    }
}
