package ws.crandell.fafo.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/** The generated wordmark VectorDrawables (tools/svg2vd.py) carry the approved colors per variant. */
class WordmarkTest {
    private val res = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
    private fun fills(name: String) = Regex("""android:fillColor="(#[0-9A-F]{6})"""").findAll(File(res, "drawable/$name.xml").readText()).map { it.groupValues[1] }.toList()

    @Test fun darkWhiteIsWhiteWithRedAccents() = assertThat(fills("wordmark_dark_white").toSet()).containsExactly("#FFFFFF", "#D61018")
    @Test fun lightIsNavyWithRedAccents() = assertThat(fills("wordmark_light").toSet()).containsExactly("#0B1F35", "#D61018")
    @Test fun monoWhiteHasNoRed() = assertThat(fills("wordmark_mono_white").toSet()).containsExactly("#FFFFFF")
    @Test fun eachHasTheFourShapes() = listOf("wordmark_dark_white", "wordmark_light", "wordmark_mono_white").forEach { assertThat(fills(it)).hasSize(4) }
}
