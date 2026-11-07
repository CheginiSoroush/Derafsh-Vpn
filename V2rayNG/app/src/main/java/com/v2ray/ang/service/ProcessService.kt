package com.v2ray.ang.service

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ProcessService {
    private var process: Process? = null

    /**
     * Runs a process with the given command.
     * @param context The context.
     * @param cmd The command to run.
     */
    fun runProcess(context: Context, cmd: MutableList<String>) {
        LogUtil.i(AppConfig.TAG, cmd.toString())

        try {
            val proBuilder = ProcessBuilder(cmd)
            proBuilder.redirectErrorStream(true)
            val started = proBuilder
                .directory(context.filesDir)
                .start()
            process = started

            CoroutineScope(Dispatchers.IO).launch {
                Thread.sleep(50L)
                LogUtil.i(AppConfig.TAG, "runProcess check")
                // Wait on the EXACT process this call started (A10): reading the field after
                // the sleep can observe a replacement from a newer runProcess call, leaving
                // this call's process unreaped (zombie) and double-waiting the new one.
                try {
                    started.waitFor()
                    LogUtil.i(AppConfig.TAG, "runProcess exited")
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "runProcess wait failed", e)
                }
            }
            LogUtil.i(AppConfig.TAG, started.toString())

        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, e.toString(), e)
        }
    }

    /**
     * Stops the running process.
     */
    fun stopProcess() {
        try {
            LogUtil.i(AppConfig.TAG, "runProcess destroy")
            process?.destroy()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to destroy process", e)
        }
    }
}
