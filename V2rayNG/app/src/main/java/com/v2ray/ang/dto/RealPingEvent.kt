package com.v2ray.ang.dto

sealed class RealPingEvent {

    /**
     * Periodic progress update while the batch is still running.
     * [completedInBatch] / [batchTotal] carry the numeric batch progress (cumulative
     * within the batch); they are `-1` when a producer cannot supply them, so existing
     * consumers that only read [text] stay unaffected.
     */
    data class Progress(
        val text: String,
        val completedInBatch: Int = -1,
        val batchTotal: Int = -1,
    ) : RealPingEvent()

    /** A single server result is available. */
    data class Result(val guid: String, val delayMillis: Long) : RealPingEvent()

    /**
     * The entire batch has finished or been cancelled. [batchTotal] reports how many
     * servers the finished batch covered (`-1` when unknown).
     */
    data class Finish(val status: String, val batchTotal: Int = -1) : RealPingEvent()
}
