@file:Suppress("SameParameterValue")

package com.github.uiopak.lstcrc.plugin.utils

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.ContainerFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.waitFor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.extension.AfterTestExecutionCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.reflect.Method
import java.time.Duration
import javax.imageio.ImageIO

class RemoteRobotExtension : AfterTestExecutionCallback, ParameterResolver {
    private val url: String = System.getProperty("robot.server.url")
        ?: System.getProperty("remote-robot-url")
        ?: "http://127.0.0.1:8082"
    private val connectionTimeoutSeconds = System.getProperty("ui.test.connection.timeout")?.toLongOrNull()
        ?: System.getProperty("ui.test.timeout")?.toLongOrNull()
        ?: 30L
    private val serverWaitTimeoutSeconds = System.getProperty("ui.test.server.wait.timeout")?.toLongOrNull() ?: 0L

    /** The first connection waits for the IDE to start. */
    private val startupTimeout: Duration = Duration.ofSeconds(maxOf(connectionTimeoutSeconds, serverWaitTimeoutSeconds))

    /**
     * Once the robot has answered, a robot that stops answering means a hung IDE. Failing that test after the
     * connection timeout keeps a hang from using up the `uiTest` task's time limit, which marks the rest SKIPPED.
     */
    private val connectionTimeout: Duration = Duration.ofSeconds(connectionTimeoutSeconds)
    private val remoteRobot = RemoteRobot(url)
    private val client = OkHttpClient()

    override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean {
        return parameterContext.parameter.type == RemoteRobot::class.java
    }

    override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any {
        waitForRemoteRobot()
        return remoteRobot
    }

    override fun afterTestExecution(context: ExtensionContext) {
        val testMethod: Method = context.requiredTestMethod
        val testMethodName = testMethod.name
        val testFailed: Boolean = context.executionException.isPresent
        if (testFailed) {
            saveIdeaFrames(testMethodName)
            saveHierarchy(testMethodName)
        }
    }


    private fun saveHierarchy(testName: String) {
        val hierarchySnapshot =
            saveFile(url, "build/reports", "hierarchy-$testName.html")
        if (File("build/reports/styles.css").exists().not()) {
            saveFile("$url/styles.css", "build/reports", "styles.css")
        }
        println("Hierarchy snapshot: ${hierarchySnapshot.absolutePath}")
    }

    private fun saveFile(url: String, folder: String, name: String): File {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            val bodyText = response.body.string()
            return File(folder).apply {
                mkdirs()
            }.resolve(name).apply {
                writeText(bodyText)
            }
        }
    }

    private fun BufferedImage.save(name: String) {
        val bytes = ByteArrayOutputStream().use { b ->
            ImageIO.write(this, "png", b)
            b.toByteArray()
        }
        File("build/reports").apply { mkdirs() }.resolve("$name.png").writeBytes(bytes)
    }

    private fun saveIdeaFrames(testName: String) {
        remoteRobot.findAll<ContainerFixture>(byXpath("//div[@class='IdeFrameImpl']")).forEachIndexed { n, frame ->
            val pic = try {
                frame.callJs<ByteArray>(
                    """
                        importPackage(java.io)
                        importPackage(javax.imageio)
                        importPackage(java.awt.image)
                        const screenShot = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
                        component.paint(screenShot.getGraphics())
                        let pictureBytes;
                        const baos = new ByteArrayOutputStream();
                        try {
                            ImageIO.write(screenShot, "png", baos);
                            pictureBytes = baos.toByteArray();
                        } finally {
                          baos.close();
                        }
                        pictureBytes;   
            """, true
                )
            } catch (e: Throwable) {
                e.printStackTrace()
                throw e
            }
            pic.inputStream().use {
                ImageIO.read(it)
            }.save(testName + "_" + n)
        }
    }


    private fun waitForRemoteRobot() {
        val timeout = if (robotAnswered) connectionTimeout else startupTimeout
        val endpoint = url.trimEnd('/') + "/"
        val ready = runCatching {
            waitFor(timeout, interval = Duration.ofSeconds(2)) {
                val response = runCatching {
                    client.newCall(Request.Builder().url(endpoint).build()).execute()
                }.getOrNull()
                val httpReady = response?.use { it.isSuccessful } == true
                httpReady && runCatching { remoteRobot.callJs<Boolean>("true") }.getOrDefault(false)
            }
            true
        }.getOrDefault(false)

        check(ready) {
            "Remote Robot server at $url was not ready within $timeout. " +
                "Start './gradlew runIdeForUiTests', wait for './gradlew uiTestReady' to pass, and then rerun the UI tests."
        }
        robotAnswered = true
    }

    private companion object {
        /** Shared by every test class in the JVM: set once the robot has answered any of them. */
        @Volatile
        var robotAnswered = false
    }
}

