package com.v2ray.ang.dto.entities

/**
 * Per-server test metadata.
 *
 * [testedAt] stamps when [testDelayMillis] was measured (0 for legacy entries written
 * before the field existed, and for wiped results). The auto test passes only re-test
 * a server whose result is missing or older than [com.v2ray.ang.AppConfig.DERAFSH_TEST_FRESH_MS],
 * so switching sources or reopening the app never re-tests the whole pool from scratch.
 */
data class ServerAffiliationInfo(
    var testDelayMillis: Long = 0L,
    var testedAt: Long = 0L,
)
