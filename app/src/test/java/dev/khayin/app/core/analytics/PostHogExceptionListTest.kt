package dev.khayin.app.core.analytics

import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostHogExceptionListTest {

    @Test
    fun `handled throwable produces one item with type, module, value and raw java frames`() {
        val list = PostHogExceptionList.fromThrowable(UnknownHostException("Unable to resolve host"), handled = true)

        assertEquals(1, list.size)
        val item = list.single()
        assertEquals("UnknownHostException", item["type"])
        assertEquals("java.net", item["module"])
        assertEquals("Unable to resolve host", item["value"])
        assertEquals(mapOf("handled" to true, "synthetic" to false, "type" to "generic"), item["mechanism"])

        val stacktrace = item["stacktrace"] as Map<*, *>
        assertEquals("raw", stacktrace["type"])
        val frames = stacktrace["frames"] as List<*>
        assertTrue(frames.isNotEmpty())
        val crashSite = frames.last() as Map<*, *>
        assertEquals("java", crashSite["platform"])
        assertEquals(PostHogExceptionListTest::class.java.name, crashSite["module"])
        assertEquals(true, crashSite["in_app"])
    }

    @Test
    fun `unhandled throwable keeps its cause chain`() {
        val error = IllegalStateException("outer", RuntimeException("inner"))

        val list = PostHogExceptionList.fromThrowable(error, handled = false)

        assertEquals(listOf("IllegalStateException", "RuntimeException"), list.map { it["type"] })
        assertEquals("chained", (list[1]["mechanism"] as Map<*, *>)["type"])
        assertTrue(list.all { (it["mechanism"] as Map<*, *>)["handled"] == false })
    }

    @Test
    fun `circular cause chain stops`() {
        val first = RuntimeException("first")
        val second = RuntimeException("second", first)
        first.initCause(second)

        assertEquals(2, PostHogExceptionList.fromThrowable(first, handled = true).size)
    }

    @Test
    fun `synthetic item uses the caller stack`() {
        val item = PostHogExceptionList.synthetic(type = "ApplicationError", message = "Playback failed", handled = true).single()

        assertEquals("ApplicationError", item["type"])
        assertEquals("Playback failed", item["value"])
        assertEquals(true, (item["mechanism"] as Map<*, *>)["synthetic"])
        val frames = (item["stacktrace"] as Map<*, *>)["frames"] as List<*>
        val crashSite = frames.last() as Map<*, *>
        assertEquals(PostHogExceptionListTest::class.java.name, crashSite["module"])
        assertFalse(frames.any { (it as Map<*, *>)["module"] == PostHogExceptionList::class.java.name })
    }
}
