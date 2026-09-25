package com.github.uiopak.lstcrc.starter

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText

/**
 * A real repository for scenario and performance tests: google/gson at release commits pinned by
 * SHA, so every diff git reports between them is fixed. Only those commits are fetched
 * (`--depth=1`, about 1 MB), once per build directory; each test gets its own copy.
 *
 * The working copy is on [CHECKED_OUT]; every release in [RELEASES] is a local branch.
 */
object GsonFixture {
    private const val REPOSITORY_URL = "https://github.com/google/gson.git"

    /** Local branch name to pinned release commit. */
    val RELEASES: Map<String, String> = linkedMapOf(
        "gson-2.10.1" to "117941130324e7692ccd589e19705cf17fd9d851",
        "gson-2.11.0" to "3b7c2ad8ce8dd471449ccbaee6d22f64c9816a38",
        "gson-2.12.1" to "07683b6c157fdf319582eff8e434f9fdcc2a647d",
        "gson-2.13.1" to "bec33dceb61708a514c675c4bcf6578345e4a888",
    )

    const val CHECKED_OUT = "gson-2.13.1"

    // Bump the version when the fixture layout changes, so stale caches are rebuilt.
    private val cacheDirectory: Path = Path.of("build", "test-repos", "gson-v1")

    /** Copies the fixture (working tree and `.git`) into the empty project directory [target]. */
    @Synchronized
    fun copyInto(target: Path) {
        ensureCache()
        cacheDirectory.toFile().copyRecursively(target.toFile(), overwrite = true)
    }

    private fun ensureCache() {
        val readyMarker = cacheDirectory.resolve(".git").resolve("lstcrc-fixture-ready")
        if (readyMarker.exists()) return

        cacheDirectory.toFile().deleteRecursively()
        cacheDirectory.createDirectories()
        git("init", "--quiet")
        git("config", "user.name", "LST-CRC Starter UI Tests")
        git("config", "user.email", "lst-crc-starter-ui-tests@example.invalid")
        git("config", "core.autocrlf", "false")
        git("remote", "add", "origin", REPOSITORY_URL)
        git("fetch", "--quiet", "--depth=1", "origin", *RELEASES.values.toTypedArray())
        RELEASES.forEach { (branch, sha) -> git("branch", branch, sha) }
        git("checkout", "--quiet", CHECKED_OUT)

        writeIdeaProject()
        // Keep the project files out of git's view.
        cacheDirectory.resolve(".git").resolve("info").createDirectories()
        cacheDirectory.resolve(".git").resolve("info").resolve("exclude").writeText(".idea/\n")

        readyMarker.writeText("ok\n")
    }

    /**
     * A minimal IntelliJ project: one module whose content root is the repository, mapped to Git.
     * Without it, a directory containing gson's pom.xml would be opened as a Maven import
     * (downloading dependencies), and an empty `.idea` would leave the files outside any module.
     */
    private fun writeIdeaProject() {
        val idea = cacheDirectory.resolve(".idea").createDirectories()
        idea.resolve("modules.xml").writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <project version="4">
              <component name="ProjectModuleManager">
                <modules>
                  <module fileurl="file://${'$'}PROJECT_DIR${'$'}/.idea/gson-fixture.iml" filepath="${'$'}PROJECT_DIR${'$'}/.idea/gson-fixture.iml" />
                </modules>
              </component>
            </project>
            """.trimIndent()
        )
        idea.resolve("gson-fixture.iml").writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <module type="JAVA_MODULE" version="4">
              <component name="NewModuleRootManager">
                <content url="file://${'$'}MODULE_DIR${'$'}/.." />
                <orderEntry type="sourceFolder" forTests="false" />
              </component>
            </module>
            """.trimIndent()
        )
        idea.resolve("vcs.xml").writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <project version="4">
              <component name="VcsDirectoryMappings">
                <mapping directory="${'$'}PROJECT_DIR${'$'}" vcs="Git" />
              </component>
            </project>
            """.trimIndent()
        )
    }

    private fun git(vararg args: String) {
        val process = ProcessBuilder(listOf("git", *args))
            .directory(cacheDirectory.toFile())
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        check(process.waitFor() == 0) { "git ${args.joinToString(" ")} failed:\n$output" }
    }
}
