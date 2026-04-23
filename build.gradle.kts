import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

plugins {
    id("java") // Java support
    alias(libs.plugins.kotlin) // Kotlin support
    alias(libs.plugins.intelliJPlatform) // IntelliJ Platform Gradle Plugin
    alias(libs.plugins.changelog) // Gradle Changelog Plugin
    alias(libs.plugins.qodana) // Gradle Qodana Plugin
    alias(libs.plugins.kover) // Gradle Kover Plugin
    idea // IntelliJ IDEA support
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

// Set the JVM language level used to build the project.
kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

// Configure project's dependencies
repositories {
    mavenCentral()
    maven("https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-ide-starter")
    // IntelliJ Platform Gradle Plugin Repositories Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html
    intellijPlatform {
        defaultRepositories()
//        mavenCentral()
    }
}

sourceSets {
    create("uiTest") {
        compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
        runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    }
}

idea {
    module {
        testSources.from(sourceSets["uiTest"].kotlin.srcDirs)
        testResources.from(sourceSets["uiTest"].resources.srcDirs)
    }
}

configurations.named("uiTestImplementation") {
    extendsFrom(configurations.testImplementation.get())
}

configurations.named("uiTestRuntimeOnly") {
    extendsFrom(configurations.testRuntimeOnly.get())
}

// Global configuration to handle vulnerable transitive dependencies.
// This block ensures that specific safe versions are used throughout the project.
configurations.all {
    resolutionStrategy {
        // Force a non-vulnerable version of commons-io, overriding the old version brought in by zt-exec.
        force(libs.commons.io)
        // Force a non-vulnerable commons-lang3 version for transitive test dependencies (e.g., video-recorder-junit5).
        force(libs.commons.lang3)

        // Substitute the vulnerable log4j: log4j with a safe SLF4J bridge.
        dependencySubstitution {
            // Eagerly resolve the dependency from the catalog to build the GAV string.
            // This is necessary because the `using(module(...))` API requires a concrete
            // version string and does not support lazy providers. This is a valid configuration
            // input and should not break caching unless the version itself changes.
            val log4jOverSlf4jDep = libs.log4j.over.slf4j.get()
            val log4jOverSlf4jGAV = "${log4jOverSlf4jDep.module}:${log4jOverSlf4jDep.version}"
            substitute(module("log4j:log4j")).using(module(log4jOverSlf4jGAV))
        }
    }
}

// Dependencies are managed with Gradle version catalog - read more: https://docs.gradle.org/current/userguide/version_catalogs.html
dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.opentest4j)

    // Remote Robot for UI tests (using a bundle from libs.versions.toml)
    testImplementation(libs.bundles.remoterobot)
    // Logging Network Calls for tests
    testImplementation(libs.okhttp.loggingInterceptor)
    // Video Recording for tests
    testImplementation(libs.video.recorder.junit5)

    // JUnit 5 (Jupiter)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.slf4j.simple)

    add("uiTestImplementation", libs.opentest4j)
    add("uiTestImplementation", libs.junit.jupiter.api)
    add("uiTestRuntimeOnly", libs.junit.jupiter.engine)
    add("uiTestRuntimeOnly", libs.junit.platform.launcher)
    add("uiTestRuntimeOnly", libs.slf4j.simple)
    add("uiTestImplementation", "org.kodein.di:kodein-di-jvm:7.31.0")
    add("uiTestImplementation", "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea(providers.gradleProperty("platformVersion"))

        // Plugin Dependencies. Uses `platformBundledPlugins` property from the gradle.properties file for bundled IntelliJ Platform plugins.
        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })

        // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file for plugin from JetBrains Marketplace.
        plugins(providers.gradleProperty("platformPlugins").map { it.split(',') })

        // Module Dependencies. Uses `platformBundledModules` property from the gradle.properties file for bundled IntelliJ Platform modules.
        bundledModules(providers.gradleProperty("platformBundledModules").map { it.split(',') })

        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Starter, configurationName = "uiTestImplementation")
        testFramework(TestFrameworkType.JUnit5, configurationName = "uiTestImplementation")
    }
}

// Configure IntelliJ Platform Gradle Plugin - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html
intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        description = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"

            with(it.lines()) {
                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
            }
        }

        val changelog = project.changelog // local variable for configuration cache compatibility
        // Get the latest available change notes from the changelog file
        changeNotes = providers.gradleProperty("pluginVersion").map { pluginVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
//            untilBuild = providers.gradleProperty("pluginUntilBuild")
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        // The pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
        // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
        // https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html#specifying-a-release-channel
        channels = providers.gradleProperty("pluginVersion").map { listOf(it.substringAfter('-', "").substringBefore('.').ifEmpty { "default" }) }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

// Configure Gradle Changelog Plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    groups.empty()
    repositoryUrl = providers.gradleProperty("pluginRepositoryUrl")
}

// Configure Gradle Kover Plugin - read more: https://kotlin.github.io/kotlinx-kover/gradle-plugin/#configuration-details
kover {
    currentProject {
        instrumentation.disabledForTestTasks.addAll("starterUiTest", "uiTest")
    }

    reports {
        total {
            xml {
                onCheck = true
            }
        }
    }
}

// Exclude the IDE Starter test bridge from the published plugin JAR by default.
// The bridge (@Service) is only needed when running starterUiTest.
// It is auto-included when starterUiTest is requested, or manually via -PincludeTestBridge=true.
val includeTestBridge = providers.gradleProperty("includeTestBridge")
    .map { it.toBoolean() }
    .orElse(gradle.startParameter.taskNames.any { it.contains("starterUiTest") })

if (!includeTestBridge.get()) {
    sourceSets.main {
        kotlin.exclude("com/github/uiopak/lstcrc/testing/**")
    }
}

// Resolve IntelliJ once via the Gradle plugin, then keep a single shared workspace copy for
// UI-related tasks. This avoids maintaining separate 4+ GB copies for Remote Robot and
// IDE Starter, while also keeping the running IDEs off the mutable Gradle transform cache.
val resolvedIdeHome = intellijPlatform.platformPath
val sharedUiIdeDir = layout.buildDirectory.dir("shared-ui-ide/idea")
val preparedSharedUiIdeDir = providers.provider {
    val sourceIdeDir = resolvedIdeHome.toFile()
    val targetIdeDir = sharedUiIdeDir.get().asFile
    val sourceBuild = sourceIdeDir.resolve("build.txt").takeIf { it.isFile }?.readText()?.trim().orEmpty()
    val targetBuild = targetIdeDir.resolve("build.txt").takeIf { it.isFile }?.readText()?.trim().orEmpty()

    if (!targetIdeDir.resolve("product-info.json").isFile || sourceBuild != targetBuild) {
        delete(targetIdeDir)
        copy {
            from(sourceIdeDir)
            into(targetIdeDir)
        }
    }

    sharedUiIdeDir.get()
}

tasks {
    val isCi = providers.environmentVariable("GITHUB_ACTIONS").orNull == "true"
    val defaultRobotServerUrl = "http://127.0.0.1:8082"
    val robotServerUrlProvider = providers.systemProperty("robot.server.url").orElse(defaultRobotServerUrl)
    val robotServerWaitTimeoutProvider = providers.systemProperty("ui.test.server.wait.timeout").orElse(if (isCi) "240" else "90")
    val robotConnectionTimeoutProvider = providers.systemProperty("ui.test.connection.timeout").orElse(if (isCi) "90" else "30")
    val uiTestTimeoutProvider = providers.systemProperty("ui.test.timeout").orElse(if (isCi) "900" else "600")

    fun Test.configureCommonTestTask() {
        useJUnitPlatform()

        testLogging {
            events("passed", "skipped", "failed", "standardOut", "standardError")
            showExceptions = true
            showCauses = true
            showStackTraces = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }

    fun Test.configureUiRobotTestTask() {
        useJUnitPlatform {
            includeTags("ui")
        }

        jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")

        maxParallelForks = 1

        systemProperty("runUiTests", "true")
        systemProperty("robot-server.auto.run", "false")
        systemProperty("robot.server.url", robotServerUrlProvider.get())
        systemProperty("ui.test.server.wait.timeout", robotServerWaitTimeoutProvider.get())
        systemProperty("ui.test.connection.timeout", robotConnectionTimeoutProvider.get())
        systemProperty("ui.test.timeout", uiTestTimeoutProvider.get())

        timeout.set(Duration.ofMinutes(20))
    }

    register("uiTestReady") {
        description = "Waits for the Remote Robot server exposed by runIdeForUiTests and fails fast with guidance if it never becomes reachable."
        group = "verification"

        doLast {
            val serverUrl = robotServerUrlProvider.get().trimEnd('/')
            val waitTimeout = Duration.ofSeconds(robotServerWaitTimeoutProvider.get().toLong())
            val deadline = System.nanoTime() + waitTimeout.toNanos()
            val client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build()

            logger.lifecycle("Waiting for Remote Robot server at $serverUrl for up to $waitTimeout.")

            var attempt = 0
            var lastFailure: String? = null
            while (System.nanoTime() < deadline) {
                attempt += 1
                try {
                    val rootRequest = HttpRequest.newBuilder(URI.create("$serverUrl/"))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build()
                    val rootResponse = client.send(rootRequest, HttpResponse.BodyHandlers.discarding())
                    if (rootResponse.statusCode() !in 200..299) {
                        lastFailure = "unexpected HTTP ${rootResponse.statusCode()}"
                        Thread.sleep(3000)
                        continue
                    }

                    val jsProbeRequest = HttpRequest.newBuilder(URI.create("$serverUrl/js/execute"))
                        .timeout(Duration.ofSeconds(5))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("""{"script":"true","runInEdt":false}"""))
                        .build()
                    val jsProbeResponse = client.send(jsProbeRequest, HttpResponse.BodyHandlers.ofString())
                    if (jsProbeResponse.statusCode() in 200..299) {
                        logger.lifecycle("Remote Robot server is ready for JS execution at $serverUrl after $attempt check(s).")
                        return@doLast
                    }

                    lastFailure = "JS probe returned HTTP ${jsProbeResponse.statusCode()}"
                } catch (exception: Exception) {
                    lastFailure = exception.message ?: exception.javaClass.simpleName
                }

                Thread.sleep(3000)
            }

            throw GradleException(
                buildString {
                    append("Remote Robot server at $serverUrl did not become ready within $waitTimeout. ")
                    append("Start './gradlew runIdeForUiTests' in another terminal, wait for this readiness task to pass, then run './gradlew uiTest'.")
                    if (!lastFailure.isNullOrBlank()) {
                        append(" Last error: $lastFailure")
                    }
                }
            )
        }
    }

    register("stopUiTestProcesses") {
        description = "Stops IDE and launcher processes started by runIdeForUiTests."
        group = "verification"

        doLast {
            val markers = listOf(
                "runideforuitests",
                "plugins_runideforuitests",
                "robot-server.port=8082",
                "shared-ui-ide",
            )

            fun runCommand(vararg command: String): String {
                val process = ProcessBuilder(*command)
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.bufferedReader().use { it.readText() }
                process.waitFor()
                return output.trim()
            }

            fun findMatchingProcesses(): List<Pair<String, String>> {
                val osName = System.getProperty("os.name").lowercase()
                return if (osName.contains("windows")) {
                    val script = $$"""
                        $regex = 'runIdeForUiTests|plugins_runIdeForUiTests|robot-server\.port=8082|shared-ui-ide'
                        Get-CimInstance Win32_Process |
                            Where-Object {
                                $_.CommandLine -and
                                $_.Name -ne 'powershell.exe' -and
                                $_.CommandLine -match $regex
                            } |
                            ForEach-Object { $_.ProcessId.ToString() + '|' + $_.Name }
                    """.trimIndent()

                    runCommand("powershell", "-NoProfile", "-Command", script)
                        .lineSequence()
                        .mapNotNull { line ->
                            val separatorIndex = line.indexOf('|')
                            if (separatorIndex <= 0) {
                                null
                            }
                            else {
                                line.substring(0, separatorIndex).trim() to line.substring(separatorIndex + 1).trim()
                            }
                        }
                        .toList()
                }
                else {
                    runCommand("ps", "-ax", "-o", "pid=,command=")
                        .lineSequence()
                        .mapNotNull { line ->
                            val trimmed = line.trim()
                            if (trimmed.isBlank()) {
                                return@mapNotNull null
                            }
                            val parts = trimmed.split(Regex("\\s+"), limit = 2)
                            if (parts.size < 2) {
                                return@mapNotNull null
                            }
                            val commandLine = parts[1].trim()
                            if (markers.none { commandLine.lowercase().contains(it) }) {
                                return@mapNotNull null
                            }
                            parts[0] to commandLine
                        }
                        .toList()
                }
            }

            val matchingProcesses = findMatchingProcesses()

            if (matchingProcesses.isEmpty()) {
                logger.lifecycle("No runIdeForUiTests processes found.")
                return@doLast
            }

            logger.lifecycle("Stopping ${matchingProcesses.size} runIdeForUiTests process(es).")
            val osName = System.getProperty("os.name").lowercase()
            matchingProcesses.forEach { (pid, commandLine) ->
                logger.lifecycle("Stopping PID $pid: $commandLine")
                if (osName.contains("windows")) {
                    runCommand("taskkill", "/PID", pid, "/T", "/F")
                }
                else {
                    runCommand("kill", pid)
                }
            }

            val gracefulDeadline = System.nanoTime() + Duration.ofSeconds(10).toNanos()
            while (System.nanoTime() < gracefulDeadline) {
                if (findMatchingProcesses().isEmpty()) {
                    logger.lifecycle("All runIdeForUiTests processes stopped.")
                    return@doLast
                }
                Thread.sleep(250)
            }

            val survivors = findMatchingProcesses()
            survivors.forEach { (pid, commandLine) ->
                logger.lifecycle("Force stopping PID $pid: $commandLine")
                if (osName.contains("windows")) {
                    runCommand("taskkill", "/PID", pid, "/T", "/F")
                }
                else {
                    runCommand("kill", "-9", pid)
                }
            }

            val forceDeadline = System.nanoTime() + Duration.ofSeconds(5).toNanos()
            while (System.nanoTime() < forceDeadline) {
                if (findMatchingProcesses().isEmpty()) {
                    logger.lifecycle("All runIdeForUiTests processes stopped.")
                    return@doLast
                }
                Thread.sleep(250)
            }

            val stillAlive = findMatchingProcesses()
            check(stillAlive.isEmpty()) {
                stillAlive.joinToString(
                    prefix = "Failed to stop runIdeForUiTests processes: ",
                    separator = "; "
                ) { (pid, commandLine) -> "PID $pid ($commandLine)" }
            }
        }
    }

    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }

    publishPlugin {
        dependsOn(patchChangelog)
    }

    // Configure the test task to use JUnit Platform (JUnit 5)
    test {
        useJUnitPlatform {
            excludeTags("ui")
        }

        configureCommonTestTask()
        failOnNoDiscoveredTests = false
        timeout.set(Duration.ofMinutes(10))
    }

    register<Test>("uiTest") {
        description = "Runs Remote Robot UI tests against an IDE started with runIdeForUiTests."
        group = "verification"

        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath

        configureCommonTestTask()
        configureUiRobotTestTask()
        filter {
            includeTestsMatching("com.github.uiopak.lstcrc.plugin.*")
        }
        dependsOn("uiTestReady")
        shouldRunAfter(test)
    }

    register<Test>("starterUiTest") {
        description = "Runs IntelliJ IDE Starter UI tests. The test bridge is auto-included in the plugin JAR."
        group = "verification"

        testClassesDirs = sourceSets["uiTest"].output.classesDirs
        classpath = sourceSets["uiTest"].runtimeClasspath

        configureCommonTestTask()
        useJUnitPlatform {
            includeTags("starter")
        }

        notCompatibleWithConfigurationCache("Starter UI test discovery is unstable when this task is restored from configuration cache.")

        maxParallelForks = 1
        minHeapSize = "1g"
        maxHeapSize = "4g"
        timeout.set(Duration.ofMinutes(30))

        systemProperty("path.to.build.plugin", buildPlugin.get().archiveFile.get().asFile.absolutePath)
        systemProperty("idea.home.path", prepareTestSandbox.get().getDestinationDir().parentFile.absolutePath)
        systemProperty("local.ide.path", preparedSharedUiIdeDir.get().asFile.absolutePath)
        systemProperty("lstcrc.starter.driver.jmx.port", System.getProperty("lstcrc.starter.driver.jmx.port") ?: "17777")
        systemProperty("lstcrc.starter.driver.rpc.port", System.getProperty("lstcrc.starter.driver.rpc.port") ?: "24000")
        systemProperty(
            "allure.results.directory",
            project.layout.buildDirectory.get().asFile.absolutePath + "/allure-results/starter-ui"
        )
        systemProperty("idea.test.cyclic.buffer.size", "0")

        jvmArgumentProviders += CommandLineArgumentProvider {
            mutableListOf(
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.desktop/javax.swing=ALL-UNNAMED"
            )
        }

        dependsOn(buildPlugin)
        shouldRunAfter(test)
    }

    register<Delete>("cleanStarterArtifacts") {
        description = "Removes IDE Starter artifacts plus obsolete copied IDE directories left by older UI test setups."
        group = "build"
        delete(
            rootProject.file("out/ide-tests"),
            layout.buildDirectory.dir("remote-ui-ide"),
            layout.buildDirectory.dir("starter-ui-ides"),
        )
    }
}

intellijPlatformTesting {
    runIde {
        register("runIdeForUiTests") {
            localPath.set(preparedSharedUiIdeDir)

            task {
                jvmArgumentProviders += CommandLineArgumentProvider {
                    listOf(
                        "-Drobot-server.port=8082",
                        "-Dide.mac.message.dialogs.as.sheets=false",
                        "-Djb.privacy.policy.text=<!--999.999-->",
                        "-Djb.consents.confirmation.enabled=false",
                        "-Didea.diagnostic.opentelemetry.metrics.file=",
                        "-Didea.diagnostic.opentelemetry.meters.file.json=",
                        "-Dide.newUsersOnboarding=false",
                        "-DNEW_USERS_ONBOARDING_DIALOG_SHOWN=true",
                        "-Dide.mac.file.chooser.native=false",
                        "-DjbScreenMenuBar.enabled=false",
                        "-Dapple.laf.useScreenMenuBar=false",
                        "-Didea.trust.all.projects=true",
                        "-Dide.show.tips.on.startup.default.value=false",
                    )
                }
            }

            plugins {
                robotServerPlugin()
            }
        }
    }
}

tasks.named("runIdeForUiTests") {
    doFirst {
        logger.lifecycle(
            "runIdeForUiTests starts the IDE and intentionally stays running. Use './gradlew uiTestReady' for an explicit readiness check, './gradlew uiTest' in another terminal to run the suite, and './gradlew stopUiTestProcesses' when you want to tear it down."
        )
    }
}

