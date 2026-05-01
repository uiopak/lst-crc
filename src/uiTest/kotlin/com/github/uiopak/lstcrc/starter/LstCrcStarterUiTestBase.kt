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
import com.intellij.ide.starter.utils.PortUtil.getAvailablePort
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.performanceTesting.commands.MarshallableCommand
import com.intellij.tools.ide.starter.bus.EventsBus
import org.junit.jupiter.api.Assertions.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import java.security.MessageDigest
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

abstract class LstCrcStarterUiTestBase {

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
    private fun resolveIdeInfo() =
        System.getProperty("local.ide.path")?.let { localIdePath ->
            IdeProductProvider.IU.copy(
                getInstaller = { SharedLocalIdeInstaller(Path(localIdePath)) }
            )
        } ?: IdeProductProvider.IC

    protected fun runStarterUiTest(block: LstCrcStarterContext.() -> Unit) {
        val testName = CurrentTestMethod.hyphenateWithClass()
        val project = LstCrcStarterProject.create(testName)
        val context = createTestContext(project, testName)

        context.runLstCrcIdeWithDriver().useDriverAndCloseIde {
            waitForIndicators(5.minutes)
            val bridge = service<LstCrcUiTestBridgeRemote>()
            val starterContext = LstCrcStarterContext(project, bridge, this)
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
                TestCase(resolveIdeInfo(), LocalProjectInfo(project.path))
            )
            .apply {
                PluginConfigurator(this).installPluginFromPath(pluginPath)
            }
            .applyVMOptionsPatch {
                addSystemProperty("idea.trust.all.projects", true)
                addSystemProperty("jb.consents.confirmation.enabled", false)
                addSystemProperty("jb.privacy.policy.text", "<!--999.999-->")
                addSystemProperty("ide.show.tips.on.startup.default.value", false)
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
        val driverOptions = createDriverOptions()
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
                addVMOptionsPatch {
                    driverOptions.systemProperties.forEach { (key, value) ->
                        addSystemProperty(key, value)
                    }
                }
                configure()
            }
        }

        return BackgroundRun(runResult, driver, kotlinx.coroutines.runBlocking { process.await() })
    }

    private fun createDriverOptions(): DriverOptions {
        val jmxPort = getAvailablePort(proposedPort = configuredPort("lstcrc.starter.driver.jmx.port", 17777))
        val rpcPort = getAvailablePort(proposedPort = configuredPort("lstcrc.starter.driver.rpc.port", 24000))
        return DriverOptions(port = jmxPort, webServerPort = rpcPort)
    }

    private fun configuredPort(propertyName: String, defaultValue: Int): Int {
        return System.getProperty(propertyName)?.toIntOrNull() ?: defaultValue
    }

    private companion object {
        val starterDriverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    /**
     * Reuses the already installed local IDE directly instead of copying it into
     * `build/starter-ui-ides/`.
     *
     * The returned `installId` is stable across runs for the same IDE build, which keeps
     * the `out/ide-tests/` output paths stable while still invalidating them automatically
     * when the underlying IDE changes.
     */
    private class SharedLocalIdeInstaller(
        private val installedIdePath: java.nio.file.Path
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
            val installId = "shared-local-ide-$fingerprint"
            return installId to DefaultIdeDistributionFactory.installIDE(
                installedIdePath.parent.toFile(),
                ideInfo.executableFileName,
            )
        }
    }
}

class LstCrcStarterContext(
    val project: LstCrcStarterProject,
    val ui: LstCrcUiTestBridgeRemote,
    val driver: Driver
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

    fun deleteFileInRepo(repoRelativePath: String, relativePath: String) {
        project.deleteFileInRepo(repoRelativePath, relativePath)
        ui.refreshProjectAfterExternalChange()
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

    fun deleteBranchInRepo(repoRelativePath: String, branchName: String) {
        project.deleteBranchInRepo(repoRelativePath, branchName)
        ui.refreshProjectAfterExternalChange()
    }

    fun defaultBranchName(): String = project.defaultBranchName()

    fun defaultBranchNameInRepo(repoRelativePath: String): String = project.defaultBranchNameInRepo(repoRelativePath)

    fun gitRevision(reference: String): String = project.gitRevision(reference)

    fun gitRevisionInRepo(repoRelativePath: String, reference: String): String = project.gitRevisionInRepo(repoRelativePath, reference)

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