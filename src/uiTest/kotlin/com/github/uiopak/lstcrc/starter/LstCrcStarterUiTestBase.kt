package com.github.uiopak.lstcrc.starter

import com.github.uiopak.lstcrc.starter.remote.LstCrcUiTestBridgeRemote
import com.intellij.driver.client.Driver
import com.intellij.driver.client.service
import com.intellij.driver.client.impl.JmxHost
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.driver.engine.DriverOptions
import com.intellij.ide.starter.ide.DefaultIdeDistributionFactory
import com.intellij.ide.starter.ide.IdeInstaller
import com.intellij.ide.starter.ide.InstalledIde
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.junit5.hyphenateWithClass
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.plugins.PluginConfigurator
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.runner.IDECommandLine
import com.intellij.ide.starter.runner.IDEHandle
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.runner.Starter
import com.intellij.ide.starter.runner.events.IdeLaunchEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ide.starter.utils.PortUtil.getAvailablePort
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.performanceTesting.commands.MarshallableCommand
import com.intellij.tools.ide.starter.bus.EventsBus
import com.intellij.util.ui.UIUtil
import org.junit.jupiter.api.Assertions.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import java.security.MessageDigest
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

abstract class LstCrcStarterUiTestBase {

    init {
        initializeTestApplicationManager()
    }

    /**
     * Returns the [com.intellij.ide.starter.models.IdeInfo] to use for the test IDE.
     *
     * When `local.ide.path` is set (passed by the Gradle `starterUiTest` task), we use
     * the locally resolved IDE via [SharedLocalIdeInstaller] instead of downloading from
     * the public JetBrains API (which fails for unreleased or expired EAP builds).
     *
     * The locally resolved IDE is reused directly across all tests and other Gradle tasks,
     * which avoids creating additional ~4 GB workspace copies for each UI stack.
     *
     * The locally resolved IDE is IntelliJ IDEA Ultimate (`IU`) because that is what
     * the IntelliJ Platform Gradle Plugin resolves via `intellijIdea(platformVersion)`.
     */
    private fun resolveIdeInfo(testName: String) =
        System.getProperty("local.ide.path")?.let { localIdePath ->
            IdeProductProvider.IU.copy(
                getInstaller = { SharedLocalIdeInstaller(Path(localIdePath), testName) }
            )
        } ?: IdeProductProvider.IC

    protected fun runStarterUiTest(block: LstCrcStarterContext.() -> Unit) {
        val testName = CurrentTestMethod.hyphenateWithClass()
        val project = LstCrcStarterProject.create(testName)
        val context = createTestContext(project, testName)

        context.runLstCrcIdeWithDriver().useDriverAndCloseIde {
            waitForIndicators(5.minutes)
            val bridge = service<LstCrcUiTestBridgeRemote>()
            val starterContext = LstCrcStarterContext(project, bridge)
            starterContext.waitForSmartMode()
            block(starterContext)
        }
    }

    protected fun createTestContext(
        project: LstCrcStarterProject,
        testName: String = CurrentTestMethod.hyphenateWithClass()
    ): IDETestContext {
        val pluginPath = Path(System.getProperty("path.to.build.plugin"))
        return Starter
            .newContext(
                testName,
                TestCase(resolveIdeInfo(testName), LocalProjectInfo(project.path))
            )
            .apply {
                PluginConfigurator(this).installPluginFromPath(pluginPath)
            }
            .applyVMOptionsPatch {
                addSystemProperty("idea.trust.all.projects", true)
                addSystemProperty("jb.consents.confirmation.enabled", false)
                addSystemProperty("jb.privacy.policy.text", "<!--999.999-->")
                addSystemProperty("ide.show.tips.on.startup.default.value", false)
                addSystemProperty("intellij.startup.wizard", false)
                addSystemProperty("junit.jupiter.extensions.autodetection.enabled", true)
                addSystemProperty("shared.indexes.download.auto.consent", true)
                addSystemProperty("ide.experimental.ui", true)
                addSystemProperty("ide.newUsersOnboarding", false)
                addSystemProperty("NEW_USERS_ONBOARDING_DIALOG_SHOWN", true)
                addSystemProperty("apple.laf.useScreenMenuBar", false)
                addSystemProperty("jbScreenMenuBar.enabled", false)
            }
            .addProjectToTrustedLocations()
    }

    protected fun IDETestContext.runLstCrcIdeWithDriver(
        commandLine: (IDERunContext) -> IDECommandLine = determineDefaultCommandLineArguments(),
        commands: Iterable<MarshallableCommand> = CommandChain(),
        runTimeout: Duration = 10.minutes,
        useStartupScript: Boolean = true,
        launchName: String = "",
        expectedKill: Boolean = false,
        expectedExitCode: Int = 0,
        collectNativeThreads: Boolean = false,
        configure: suspend IDERunContext.() -> Unit = {}
    ): BackgroundRun {
        val testName = CurrentTestMethod.hyphenateWithClass()
        val driverOptions = createDriverOptions()
        preparePerTestVmOptions(testName, driverOptions)
        val driver = Driver.create(JmxHost(address = driverOptions.address))
        val process = CompletableDeferred<IDEHandle>()

        EventsBus.subscribeOnce(process) { event: IdeLaunchEvent ->
            process.complete(event.ideProcess)
        }

        val runResult = starterDriverScope.async {
            runIdeSuspending(
                commandLine = commandLine,
                commands = commands,
                runTimeout = runTimeout,
                useStartupScript = useStartupScript,
                launchName = launchName,
                expectedKill = expectedKill,
                expectedExitCode = expectedExitCode,
                collectNativeThreads = collectNativeThreads,
            ) {
                configure()
            }
        }

        return BackgroundRun(runResult, driver, kotlinx.coroutines.runBlocking { process.await() })
    }

    private fun IDETestContext.preparePerTestVmOptions(testName: String, driverOptions: DriverOptions) {
        val vmOptionsDirectory = Path(System.getProperty("java.io.tmpdir"), "lstcrc-starter-ui-vmoptions")
            .createDirectories()
        val vmOptionsFile = vmOptionsDirectory.resolve("$testName.vmoptions")
        vmOptionsFile.parent.createDirectories()
        val ideaLogDirectory = Path(System.getProperty("user.dir"), "out", "ide-tests", "logs", testName)
            .createDirectories()

        ide.vmOptions.withEnv("IDEA_VM_OPTIONS", vmOptionsFile.toAbsolutePath().toString())
        ide.vmOptions.addSystemProperty("idea.log.path", ideaLogDirectory.toAbsolutePath().toString())
        driverOptions.systemProperties.forEach { (key, value) ->
            ide.vmOptions.addSystemProperty(key, value)
        }
        ide.vmOptions.writeIntelliJVmOptionFile(vmOptionsFile)
    }

    private fun createDriverOptions(): DriverOptions {
        val testName = CurrentTestMethod.hyphenateWithClass()
        val jmxPort = getAvailablePort(
            proposedPort = configuredPort("lstcrc.starter.driver.jmx.port", derivedPort(testName, 17777))
        )
        val rpcPort = getAvailablePort(
            proposedPort = configuredPort("lstcrc.starter.driver.rpc.port", derivedPort(testName, 24000))
        )
        return DriverOptions(port = jmxPort, webServerPort = rpcPort)
    }

    private fun configuredPort(propertyName: String, defaultValue: Int): Int {
        return System.getProperty(propertyName)?.toIntOrNull() ?: defaultValue
    }

    private fun derivedPort(testName: String, basePort: Int): Int {
        val offset = (testName.hashCode().toUInt().toLong() % 10_000L).toInt()
        return basePort + offset
    }

    private companion object {
        val starterDriverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        private fun initializeTestApplicationManager() {
            runCatching {
                Class.forName("com.intellij.testFramework.common.TestEnvironmentKt")
                    .getMethod("initializeTestEnvironment")
                    .invoke(null)
                Class.forName("com.intellij.testFramework.common.TestApplicationKt")
                    .getMethod("initTestApplication")
                    .invoke(null)
                val application = ApplicationManager.getApplication()
                if (application.isDispatchThread) {
                    UIUtil.getRegularPanelInsets()
                } else {
                    application.invokeAndWait { UIUtil.getRegularPanelInsets() }
                }
            }
        }
    }

    /**
     * Reuses the already installed local IDE directly instead of copying it into
     * `build/starter-ui-ides/`.
     *
     * The returned `installId` is stable for the same IDE build and test name, which keeps
     * a deterministic per-test `out/ide-tests/` workspace while isolating concurrent Starter
     * forks from one another.
     */
    private class SharedLocalIdeInstaller(
        private val installedIdePath: java.nio.file.Path,
        private val testName: String
    ) : IdeInstaller {
        override suspend fun install(ideInfo: IdeInfo): Pair<String, InstalledIde> {
            val sourceProductInfo = installedIdePath.resolve("product-info.json")
            val fingerprint = if (sourceProductInfo.exists()) {
                val digest = MessageDigest.getInstance("SHA-256")
                val hash = digest.digest(sourceProductInfo.readText().toByteArray())
                hash.joinToString("") { "%02x".format(it) }.take(12)
            } else {
                "unknown"
            }
            val installId = "shared-local-ide-$fingerprint-$testName"
            return installId to DefaultIdeDistributionFactory.installIDE(
                installedIdePath.parent.toFile(),
                ideInfo.executableFileName,
            )
        }
    }
}

class LstCrcStarterContext(
    val project: LstCrcStarterProject,
    val ui: LstCrcUiTestBridgeRemote
) {

    fun prepareLstCrc() {
        waitForSmartMode()
        ui.resetGitChangesViewState()
    }

    fun initializeGitRepository() {
        project.initializeGitRepository()
        ui.activateGitVcsIntegration()
        waitUntil(30.seconds) { ui.isGitVcsActive() }
        ui.refreshProjectAfterExternalChange()
    }

    fun initializeGitRepositoryAt(relativeRepoPath: String) {
        project.initializeGitRepositoryAt(relativeRepoPath)
        ui.activateGitVcsIntegrationFor(relativeRepoPath)
        ui.refreshProjectAfterExternalChange()
        val expectedRepoPath = project.path.resolve(relativeRepoPath).toString().replace('\\', '/')
        waitUntil(30.seconds) {
            ui.knownGitRepositoriesSnapshot().contains(expectedRepoPath)
        }
    }

    fun createLinkedWorktree(sourceRepoRelativePath: String, worktreeRelativePath: String, branchName: String, startPoint: String = "HEAD") {
        project.addLinkedWorktree(sourceRepoRelativePath, worktreeRelativePath, branchName, startPoint)
        ui.activateGitVcsIntegrationFor(worktreeRelativePath)
        ui.refreshProjectAfterExternalChange()
        val expectedRepoPath = project.path.resolve(worktreeRelativePath).toString().replace('\\', '/')
        waitUntil(30.seconds) {
            ui.knownGitRepositoriesSnapshot().contains(expectedRepoPath)
        }
    }

    fun createNewFile(relativePath: String, content: String) {
        ui.writeProjectFile(relativePath, content)
    }

    fun createNewFileInRepo(repoRelativePath: String, relativePath: String, content: String) {
        project.writeFileInRepo(repoRelativePath, relativePath, content)
        ui.refreshProjectAfterExternalChange()
    }

    fun modifyFile(relativePath: String, content: String) {
        ui.writeProjectFile(relativePath, content)
    }

    fun modifyFileInRepo(repoRelativePath: String, relativePath: String, content: String) {
        project.writeFileInRepo(repoRelativePath, relativePath, content)
        ui.refreshProjectAfterExternalChange()
    }

    fun renameFile(oldPath: String, newPath: String) {
        ui.renameProjectFile(oldPath, newPath)
    }

    fun deleteFile(relativePath: String) {
        ui.deleteProjectFile(relativePath)
    }


    fun commitChanges(message: String) {
        project.commitAll(message)
        ui.refreshProjectAfterExternalChange()
    }

    fun commitChangesInRepo(repoRelativePath: String, message: String) {
        project.commitAllInRepo(repoRelativePath, message)
        ui.refreshProjectAfterExternalChange()
    }

    fun createBranch(branchName: String) {
        project.createBranch(branchName)
        ui.refreshProjectAfterExternalChange()
    }

    fun createBranchInRepo(repoRelativePath: String, branchName: String) {
        project.createBranchInRepo(repoRelativePath, branchName)
        ui.refreshProjectAfterExternalChange()
    }

    fun checkoutBranch(branchName: String) {
        project.checkout(branchName)
        ui.refreshProjectAfterExternalChange()
    }

    fun checkoutBranchInRepo(repoRelativePath: String, branchName: String) {
        project.checkoutInRepo(repoRelativePath, branchName)
        ui.refreshProjectAfterExternalChange()
    }

    fun deleteBranch(branchName: String) {
        project.deleteBranch(branchName)
        ui.refreshProjectAfterExternalChange()
    }


    fun defaultBranchName(): String = project.defaultBranchName()

    fun defaultBranchNameInRepo(repoRelativePath: String): String = project.defaultBranchNameInRepo(repoRelativePath)

    fun gitRevision(reference: String): String = project.gitRevision(reference)


    fun waitForSmartMode(timeout: Duration = 5.minutes) {
        waitUntil(timeout) { !ui.isDumbMode() }
    }

    fun openGitChangesView() {
        ui.openGitChangesView()
    }

    fun openBranchSelectionTab() {
        ui.openBranchSelectionTab()
    }

    fun setRepoComparisonForRoot(repoRelativePath: String, targetRevision: String) {
        ui.setRepoComparisonForRoot(repoRelativePath, targetRevision)
    }

    fun waitForSelectedTab(tabName: String, timeout: Duration = 20.seconds) {
        waitUntil(timeout) { ui.selectedTabName() == tabName }
    }

    fun waitForTreeContains(vararg texts: String, timeout: Duration = 20.seconds) {
        waitUntil(timeout) {
            val snapshot = ui.selectedChangesTreeSnapshot()
            texts.all(snapshot::contains)
        }
    }

    @Suppress("SameParameterValue")
    fun waitForTreeNotContains(vararg texts: String, timeout: Duration = 20.seconds) {
        waitUntil(timeout) {
            val snapshot = ui.selectedChangesTreeSnapshot()
            texts.none(snapshot::contains)
        }
    }

    fun waitUntil(timeout: Duration = 20.seconds, interval: Duration = 250.milliseconds, condition: () -> Boolean) {
        val deadline = System.nanoTime() + timeout.inWholeNanoseconds
        while (System.nanoTime() < deadline) {
            if (runCatching(condition).getOrDefault(false)) {
                return
            }
            Thread.sleep(interval.inWholeMilliseconds)
        }
        assertTrue(condition(), "Condition did not become true within $timeout")
    }
}