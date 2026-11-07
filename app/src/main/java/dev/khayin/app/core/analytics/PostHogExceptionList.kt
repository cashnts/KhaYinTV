package dev.khayin.app.core.analytics

import java.util.Collections
import java.util.IdentityHashMap

/**
 * Builds the `$exception_list` property that PostHog Error Tracking requires on `$exception` events.
 * The shape follows the posthog-android SDK coercer, because the pinned SDK version has no
 * `captureException` API.
 */
internal object PostHogExceptionList {
    private const val IN_APP_PREFIX = "dev.khayin."
    private const val MAX_EXCEPTIONS = 10
    private const val MAX_FRAMES = 64

    fun fromThrowable(throwable: Throwable, handled: Boolean): List<Map<String, Any>> {
        val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        val threadId = currentThreadId()
        val items = mutableListOf<Map<String, Any>>()
        var current: Throwable? = throwable
        while (current != null && items.size < MAX_EXCEPTIONS && seen.add(current)) {
            val type = current.javaClass.`package`?.let { current.javaClass.name.removePrefix(it.name + ".") }
                ?: current.javaClass.name
            items += buildItem(
                type = type,
                value = current.message,
                module = current.javaClass.`package`?.name,
                stackTrace = current.stackTrace,
                handled = handled,
                synthetic = false,
                mechanismType = if (items.isEmpty()) "generic" else "chained",
                threadId = threadId
            )
            current = current.cause
        }
        return items
    }

    /** For error logs without a throwable: the stack of the logging call site stands in for one. */
    fun synthetic(type: String, message: String, handled: Boolean): List<Map<String, Any>> {
        val internalClasses = listOf(PostHogExceptionList::class.java.name, PostHogAnalytics::class.java.name)
        val stackTrace = Throwable().stackTrace
            .dropWhile { frame -> internalClasses.any { frame.className == it || frame.className.startsWith("$it$") } }
            .toTypedArray()
        return listOf(
            buildItem(
                type = type,
                value = message,
                module = null,
                stackTrace = stackTrace,
                handled = handled,
                synthetic = true,
                mechanismType = "generic",
                threadId = currentThreadId()
            )
        )
    }

    private fun buildItem(
        type: String,
        value: String?,
        module: String?,
        stackTrace: Array<StackTraceElement>,
        handled: Boolean,
        synthetic: Boolean,
        mechanismType: String,
        threadId: Long
    ): Map<String, Any> = buildMap {
        put("type", type)
        if (!value.isNullOrEmpty()) put("value", value)
        if (!module.isNullOrEmpty()) put("module", module)
        put("thread_id", threadId)
        put(
            "mechanism",
            mapOf("handled" to handled, "synthetic" to synthetic, "type" to mechanismType)
        )
        val frames = buildFrames(stackTrace)
        if (frames.isNotEmpty()) {
            put("stacktrace", mapOf("frames" to frames, "type" to "raw"))
        }
    }

    // PostHog expects frames outermost first, so the crash site is the last frame.
    private fun buildFrames(stackTrace: Array<StackTraceElement>): List<Map<String, Any>> =
        stackTrace.take(MAX_FRAMES).reversed().map { frame ->
            buildMap {
                put("module", frame.className)
                put("function", frame.methodName)
                put("platform", "java")
                if (frame.lineNumber >= 0) put("lineno", frame.lineNumber)
                if (!frame.fileName.isNullOrEmpty()) put("filename", frame.fileName)
                put("in_app", frame.className.startsWith(IN_APP_PREFIX))
            }
        }

    @Suppress("DEPRECATION")
    private fun currentThreadId(): Long = Thread.currentThread().id
}
