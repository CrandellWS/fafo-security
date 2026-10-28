package ws.crandell.fafo

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * The only key material allowed in the repository is the public debug keystore (keystore/README.md).
 * No release keystore, signing properties or Google services config may ever be committed.
 */
class RepoSecretsTest {
    private val repoRoot: File = listOf(File("."), File("..")).map { it.canonicalFile }
        .first { File(it, "settings.gradle.kts").isFile }

    private fun repoFiles() = repoRoot.walkTopDown()
        .onEnter { d -> d.name !in setOf("build", ".gradle", ".git", ".kotlin", ".idea", ".claude") }
        .filter { it.isFile }

    @Test fun onlyTheSharedDebugKeystoreIsInTheRepo() {
        val keys = repoFiles().filter { it.extension in setOf("keystore", "jks", "p12", "pfx") }
            .map { it.relativeTo(repoRoot).invariantSeparatorsPath }.toList()
        assertThat(keys).containsExactly("keystore/debug.keystore")
    }

    @Test fun noSigningPropertiesOrServiceConfigs() {
        val bad = repoFiles().filter {
            it.name in setOf("keystore.properties", "signing.properties", "google-services.json", "local.properties.release")
        }.map { it.relativeTo(repoRoot).path }.toList()
        assertThat(bad).isEmpty()
    }

    @Test fun noReleaseSigningConfigInTheBuild() {
        val gradle = File(repoRoot, "app/build.gradle.kts").readText()
        assertThat(gradle).doesNotContain("create(\"release\")")
        assertThat(Regex("""release\s*\{[^}]*signingConfig""").containsMatchIn(gradle)).isFalse()
    }
}
