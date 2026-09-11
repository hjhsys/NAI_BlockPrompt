package com.hjhsys.naiblockprompt.data.diagnostics

import android.content.Context
import android.os.Build
import com.hjhsys.naiblockprompt.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant

class CrashLogStore(private val context: Context) {
    private val crashFile: File
        get() = File(context.filesDir, FILE_NAME)

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun read(): String? = crashFile.takeIf(File::isFile)?.readText()

    fun clear(): Boolean = !crashFile.exists() || crashFile.delete()

    private fun write(thread: Thread, throwable: Throwable) {
        val stackTrace = StringWriter().also { writer ->
            throwable.printStackTrace(PrintWriter(writer))
        }.toString()
        val report = buildString {
            appendLine("NAI Block Prompt crash report")
            appendLine("Time (UTC): ${Instant.now()}")
            appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Thread: ${thread.name}")
            appendLine()
            append(redactSecrets(stackTrace).take(MAX_REPORT_CHARS))
        }
        val temporary = File(context.filesDir, "$FILE_NAME.tmp")
        temporary.writeText(report)
        if (!temporary.renameTo(crashFile)) {
            crashFile.writeText(report)
            temporary.delete()
        }
    }

    companion object {
        private const val FILE_NAME = "last_crash.txt"
        private const val MAX_REPORT_CHARS = 200_000

        internal fun redactSecrets(text: String): String = text
            .replace(Regex("(?i)(authorization\\s*[:=]\\s*bearer\\s+)[^\\s,;]+"), "$1<REDACTED>")
            .replace(Regex("(?i)((?:api[_ -]?token|persistent[_ -]?token|access[_ -]?token)\\s*[:=]\\s*)[^\\s,;]+"), "$1<REDACTED>")
    }
}
