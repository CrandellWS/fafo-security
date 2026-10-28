package ws.crandell.fafo

import org.junit.Assert.assertEquals
import org.junit.Test

class SanityTest {
    @Test
    fun packageNameIsFixed() {
        assertEquals("ws.crandell.fafo", SanityTest::class.java.packageName)
    }
}
