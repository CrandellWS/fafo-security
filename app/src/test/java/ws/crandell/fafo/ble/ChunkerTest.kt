package ws.crandell.fafo.ble

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.random.Random

class ChunkerTest {
    @Test fun roundTripAcrossSizes() {
        for (size in listOf(0, 1, 19, 20, 21, 500, 4096)) {
            val msg = Random(size).nextBytes(size)
            val chunks = Chunker.split(msg, 20)
            chunks.forEach { assertThat(it.size).isAtMost(21) }
            val j = Chunker.Joiner()
            val out = chunks.mapNotNull { j.add(it) }
            assertThat(out).hasSize(1)
            assertThat(out[0]).isEqualTo(msg)
        }
    }

    @Test fun twoMessagesBackToBack() {
        val j = Chunker.Joiner()
        val a = Random(1).nextBytes(50)
        val b = Random(2).nextBytes(70)
        val out = (Chunker.split(a, 16) + Chunker.split(b, 16)).mapNotNull { j.add(it) }
        assertThat(out.map { it.toList() }).containsExactly(a.toList(), b.toList()).inOrder()
    }

    @Test fun oversizeRefused() {
        assertThrows(IllegalArgumentException::class.java) { Chunker.split(ByteArray(Chunker.MAX_MESSAGE + 1), 100) }
        val j = Chunker.Joiner()
        val chunk = byteArrayOf(0) + ByteArray(1000)
        assertThrows(IllegalStateException::class.java) { repeat(100) { j.add(chunk) } }
    }
}
