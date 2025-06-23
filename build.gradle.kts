import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import java.time.Duration

plugins {
    id("java") // Java support
    alias(libs.plugins.kotlin) // Kotlin support
    alias(libs.plugins.intelliJPlatform) // IntelliJ Platform Gradle Plugin
    alias(libs.plugins.changelog) // Gradle Changelog Plugin
    alias(libs.plugins.qodana) // Gradle Qodana Plugin
    alias(libs.plugins.kover) // Gradle Kover Plugin
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

// Set the JVM language level used to build the project.
kotlin {
    jvmToolchain(21)
}

// Configure project's dependencies
repositories {
    mavenCentral()

    // IntelliJ Platform Gradle Plugin Repositories Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html
    intellijPlatform {
        defaultRepositories()
    }
}

// Dependencies are managed with Gradle version catalog - read more: https://docs.gradle.org/current/userguide/platforms.html#sub:version-catalog
dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.opentest4j)

    // Remote Robot for UI tests
    testImplementation("com.intellij.remoterobot:remote-robot:0.11.23")
    testImplementation("com.intellij.remoterobot:remote-fixtures:0.11.23")

//    // Dependencies for UI testing
//    testImplementation("com.google.code.gson:gson:2.8.5")
//    testImplementation("com.squareup.okhttp3:okhttp:3.14.9")
//    testImplementation("com.squareup.retrofit2:retrofit:2.9.0")
//    testImplementation("com.squareup.retrofit2:converter-gson:2.9.0")
//
    // JUnit 5 (Jupiter)
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.2")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")

    // Logging Network Calls
    testImplementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Video Recording
    implementation("com.automation-remarks:video-recorder-junit5:2.0")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        create(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"))

        // Plugin Dependencies. Uses `platformBundledPlugins` property from the gradle.properties file for bundled IntelliJ Platform plugins.
        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })

        // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file for plugin from JetBrains Marketplace.
        plugins(providers.gradleProperty("platformPlugins").map { it.split(',') })

        testFramework(TestFrameworkType.Platform)
    }
}

//// Force specific versions for UI testing to avoid classpath conflicts with the IDE.
//// This is the most robust way to handle this.
//configurations.testImplementation {
//    resolutionStrategy.force(
//        "com.google.code.gson:gson:2.8.5",
//        "com.squareup.okhttp3:okhttp:3.14.9",
//        "com.squareup.retrofit2:retrofit:2.9.0",
//        "com.squareup.retrofit2:converter-gson:2.9.0"
//    )
//}
//

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
            untilBuild = providers.gradleProperty("pluginUntilBuild")
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
        // https://plugins.jetbrains.com/docs/intellij/deployment.html#specifying-a-release-channel
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

// Configure Gradle Kover Plugin - read more: https://github.com/Kotlin/kotlinx-kover#configuration
kover {
    reports {
        total {
            xml {
                onCheck = true
            }
        }
    }
}

tasks {
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }

    publishPlugin {
        dependsOn(patchChangelog)
    }

    //
//    // Configure the test task to use JUnit 4 for non-UI tests
//    test {
//        useJUnit()
//        exclude("**/ui/**")
//    }
//
//    // Create a separate task for UI tests that uses JUnit 5
//    val uiTest = register<Test>("uiTest") {
//        description = "Runs UI tests against an IDE with Robot Server"
//        group = "verification"
//
//        useJUnitPlatform()
//        include("**/ui/**")
//
//        // This is the key change: it tells the test task not to launch the IDE itself.
//        systemProperty("robot-server.auto.run", "false")
//
//        systemProperty("robot.server.url", "http://127.0.0.1:8082")
//        systemProperty("ui.test.timeout", "120")
//
//        testLogging {
//            events("passed", "skipped", "failed", "standardOut", "standardError")
//            showExceptions = true
//            showCauses = true
//            showStackTraces = true
//            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
//        }
//
//        timeout.set(Duration.ofMinutes(10))
//    }
//
//    // Add uiTest to the 'check' task to run it with ./gradlew check
//    named("check") {
//        dependsOn(uiTest)
//    }

    // Configure the test task to use JUnit Platform (JUnit 5)
    test {
        useJUnitPlatform()

        // Exclude UI tests that require a running IDE with Robot Server
//        exclude("**/plugin/CreateCommandLineKotlinTest.kt")
//        exclude("**/plugin/SayHelloKotlinTest.kt")

        testLogging {
            events("passed", "skipped", "failed", "standardOut", "standardError")
            showExceptions = true
            showCauses = true
            showStackTraces = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }

        systemProperty("robot-server.auto.run", "false")
        systemProperty("robot.server.url", "http://127.0.0.1:8082")
        systemProperty("ui.test.timeout", "120")

        timeout.set(Duration.ofMinutes(10))
    }
}

intellijPlatformTesting {
    runIde {
        register("runIdeForUiTests") {
            task {
                jvmArgumentProviders += CommandLineArgumentProvider {
                    listOf(
                        "-Drobot-server.port=8082",
                        "-Dide.mac.message.dialogs.as.sheets=false",
                        "-Djb.privacy.policy.text=<!--999.999-->",
                        "-Djb.consents.confirmation.enabled=false",
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
