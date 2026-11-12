package com.dressence.nothinglauncherplus

import java.nio.charset.StandardCharsets

/**
 * Running a shell script as root, the module's one way to do it (until 0.70.7 there were five
 * copies: perf.Device, ui.DebugReport, ui.SystemFont and two in UpdateReceiver, each a little
 * different: some threw, some never did, one sent a script, the others one command).
 *
 * The script goes to `su` on its standard input, then `exit`: a one-line command and a long
 * script go the same way, and nothing in them needs quoting for a command line. The output is
 * read to the end (so a chatty command can never block on a full pipe) and the exit code waited
 * for. Blocking, and root may first ask the user: call it off the main thread.
 *
 * (perf.OverlayService keeps its own long-lived root shell: it reads values from it all the
 * time, which is a different job.)
 */
object Root {

    /** Runs [script] as root: null if it succeeded, else its output or why it could not run. Never throws. */
    @JvmStatic
    fun run(script: String): String? = try {
        val (code, output) = exec(script)
        if (code == 0) null else output.ifEmpty { "Root access was refused (exit $code)" }
    } catch (e: Exception) {
        e.message ?: e.toString()
    }

    /** Runs [script] as root: its output if it succeeded, else null. Never throws. */
    @JvmStatic
    fun read(script: String): String? = try {
        val (code, output) = exec(script)
        if (code == 0) output else null
    } catch (e: Exception) {
        null
    }

    private fun exec(script: String): Pair<Int, String> {
        val p = ProcessBuilder("su").redirectErrorStream(true).start()
        p.outputStream.use {
            it.write(script.toByteArray(StandardCharsets.UTF_8))
            it.write("\nexit\n".toByteArray(StandardCharsets.UTF_8))
        }
        val output = String(p.inputStream.readBytes(), StandardCharsets.UTF_8).trim()
        return p.waitFor() to output
    }
}
