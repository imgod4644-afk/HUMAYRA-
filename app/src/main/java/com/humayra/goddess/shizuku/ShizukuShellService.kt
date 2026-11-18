package com.humayra.goddess.shizuku

import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.system.exitProcess

class ShizukuShellService : IShellService.Stub() {
    override fun exec(command: String): String = try {
        val p = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
        val out = BufferedReader(InputStreamReader(p.inputStream)).readText()
        val code = p.waitFor()
        "EXIT:$code\n$out".trim()
    } catch (e: Throwable) { "ERR:${e.message}" }

    override fun destroy() { exitProcess(0) }
}
