package com.github.uiopak.lstcrc.plugin.pages

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.*
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.waitFor
import org.assertj.swing.core.MouseButton
import java.time.Duration

fun IdeaFrame.gitChangesView(function: GitChangesViewFixture.() -> Unit) {
    val timeout = if (System.getenv("GITHUB_ACTIONS") == "true") Duration.ofSeconds(30) else Duration.ofSeconds(10)
    val locator = byXpath("//div[@class='LstCrcChangesBrowser' and @visible='true']")
    waitFor(timeout, interval = Duration.ofMillis(250)) {
        findAll<GitChangesViewFixture>(locator).isNotEmpty()
    }
    findAll<GitChangesViewFixture>(locator).first().apply(function)
}

@FixtureName("GitChangesView")
class GitChangesViewFixture(remoteRobot: RemoteRobot, remoteComponent: RemoteComponent) :
    CommonContainerFixture(remoteRobot, remoteComponent) {

    private val branchSelectionPanelLocator = byXpath("//div[@class='BranchSelectionPanel']")

    private fun toJsStringLiteral(value: String): String {
        return buildString {
            append('"')
            value.forEach { character ->
                when (character) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(character)
                }
            }
            append('"')
        }
    }

    private fun tabLocator(tabName: String) = byXpath(
        "Tab '$tabName'",
        "//div[@class='ContentTabLabel' and (@text='$tabName' or @accessiblename='$tabName' or @visible_text='$tabName')]"
    )

    private fun hasContentTab(tabName: String): Boolean = remoteRobot.callJs(
        """
        const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
        if (!project) {
            false;
        } else {
            const toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("GitChangesView");
            const contentManager = toolWindow ? toolWindow.getContentManager() : null;
            if (!contentManager) {
                false;
            } else {
                const tabName = ${toJsStringLiteral(tabName)};
                java.util.Arrays.stream(contentManager.getContents())
                    .anyMatch(content => tabName.equals(content.getDisplayName()));
            }
        }
        """.trimIndent(),
        true
    )

    private fun selectContentTab(tabName: String): Boolean = remoteRobot.callJs(
        """
        const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
        if (!project) {
            false;
        } else {
            const toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("GitChangesView");
            const contentManager = toolWindow ? toolWindow.getContentManager() : null;
            if (!contentManager) {
                false;
            } else {
                const tabName = ${toJsStringLiteral(tabName)};
                const content = java.util.Arrays.stream(contentManager.getContents())
                    .filter(item => tabName.equals(item.getDisplayName()))
                    .findFirst()
                    .orElse(null);
                if (content == null) {
                    false;
                } else {
                    contentManager.setSelectedContent(content, true);
                    const browser = content.getComponent();
                    const stateServiceClass = browser.getClass().getClassLoader()
                        .loadClass("com.github.uiopak.lstcrc.services.ToolWindowStateService");
                    const stateService = project.getService(stateServiceClass);
                    if (stateService != null) {
                        const selectedIndex = java.util.Arrays.stream(contentManager.getContents())
                            .filter(item => item.isCloseable())
                            .toList()
                            .indexOf(content);
                        stateService.setSelectedTab(selectedIndex);
                        stateService.refreshDataForCurrentSelection();
                    }
                    true;
                }
            }
        }
        """.trimIndent(),
        true
    )

    private fun selectedContentTabName(): String = remoteRobot.callJs(
        """
        const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
        if (!project) {
            null;
        } else {
            const toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("GitChangesView");
            const contentManager = toolWindow ? toolWindow.getContentManager() : null;
            const selectedContent = contentManager ? contentManager.getSelectedContent() : null;
            selectedContent ? selectedContent.getDisplayName() : null;
        }
        """.trimIndent(),
        true
    )

    fun hasTab(tabName: String): Boolean {
        return hasContentTab(tabName)
    }

    fun selectTab(tabName: String) {
        step("Select tab '$tabName'") {
            waitFor(Duration.ofSeconds(10), interval = Duration.ofMillis(500)) {
                hasContentTab(tabName)
            }

            if (!selectContentTab(tabName)) {
                val visibleTabs = remoteRobot.findAll<ComponentFixture>(tabLocator(tabName))
                check(visibleTabs.isNotEmpty()) { "Could not find tab '$tabName'" }
                visibleTabs.first().click()
            }

            waitFor(Duration.ofSeconds(10), interval = Duration.ofMillis(250)) {
                selectedContentTabName() == tabName
            }

            runJs(
                """
                const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
                if (project) {
                    const pluginId = com.intellij.openapi.extensions.PluginId.getId("com.github.uiopak.lstcrc");
                    const plugin = com.intellij.ide.plugins.PluginManagerCore.getPlugin(pluginId);
                    if (plugin != null) {
                        const stateServiceClass = plugin.getPluginClassLoader()
                            .loadClass("com.github.uiopak.lstcrc.services.ToolWindowStateService");
                        const stateService = project.getService(stateServiceClass);
                        if (stateService != null) {
                            stateService.refreshDataForCurrentSelection().join();
                        }
                    }
                }
                """.trimIndent(),
                false
            )
        }
    }

    val changesTree: ContainerFixture
        get() = remoteRobot.find(byXpath("//div[@class='LstCrcAsyncChangesTree' or @class='ChangesTree']"))

    fun clickChange(fileName: String, button: MouseButton = MouseButton.LEFT_BUTTON) {
        step("Click '$fileName' with $button") {
            waitFor(Duration.ofSeconds(10), interval = Duration.ofMillis(250)) {
                changesTree.findAllText(fileName).isNotEmpty()
            }
            changesTree.findText(fileName).click(button)
        }
    }

    fun scrollChangeIntoView(fileName: String) {
        step("Scroll '$fileName' into view") {
            runJs(
                """
                (function() {
                    function findBrowser(root) {
                        var queue = new java.util.LinkedList();
                        queue.add(root);
                        while (!queue.isEmpty()) {
                            var current = queue.poll();
                            if (current == null) continue;
                            if (current.getClass().getName().endsWith("LstCrcChangesBrowser")) {
                                return current;
                            }
                            try {
                                var children = current.getComponents();
                                if (children) {
                                    for (var i = 0; i < children.length; i++) {
                                        queue.add(children[i]);
                                    }
                                }
                            } catch (ignored) {}
                        }
                        return null;
                    }

                    var browser = findBrowser(component);
                    if (!browser || typeof browser.scrollVisibleFileIntoViewForTest !== "function") {
                        return;
                    }

                    browser.scrollVisibleFileIntoViewForTest(${toJsStringLiteral(fileName)});
                })()
                """.trimIndent(),
                true
            )

            waitFor(Duration.ofSeconds(10), interval = Duration.ofMillis(250)) {
                changesTree.findAllText(fileName).isNotEmpty()
            }
        }
    }

    fun doubleClickChange(fileName: String) {
        step("Double click '$fileName'") {
            waitFor(Duration.ofSeconds(10), interval = Duration.ofMillis(250)) {
                changesTree.findAllText(fileName).isNotEmpty()
            }
            changesTree.findText(fileName).doubleClick()
        }
    }

    fun rightClickChange(fileName: String) {
        clickChange(fileName, MouseButton.RIGHT_BUTTON)
    }

    fun treeViewportPosition(): Pair<Int, Int> = step("Read changes tree viewport position") {
        callJs<String>(
            """
            (function() {
                var tree = null;
                var queue = new java.util.LinkedList();
                queue.add(component);
                while (!queue.isEmpty() && !tree) {
                    var current = queue.poll();
                    if (current == null) continue;
                    if (current.getClass().getName().endsWith("LstCrcAsyncChangesTree") || current.getClass().getName().endsWith("ChangesTree")) {
                        tree = current;
                        break;
                    }
                    try {
                        var children = current.getComponents();
                        if (children) {
                            for (var i = 0; i < children.length; i++) {
                                queue.add(children[i]);
                            }
                        }
                    } catch (ignored) {}
                }
                if (!tree) {
                    return java.util.Arrays.asList(0, 0);
                }
                var parent = tree.getParent();
                while (parent != null && !(parent instanceof javax.swing.JViewport)) {
                    parent = parent.getParent();
                }
                if (parent == null) {
                    return "0,0";
                }
                var point = parent.getViewPosition();
                return point.x + "," + point.y;
            })()
            """.trimIndent(),
            true
        ).split(',', limit = 2).let { parts ->
            (parts.getOrNull(0)?.toIntOrNull() ?: 0) to (parts.getOrNull(1)?.toIntOrNull() ?: 0)
        }
    }

    fun treeVerticalScrollBarValue(): Int = step("Read changes tree vertical scrollbar value") {
        callJs<String>(
            """
            (function() {
                var tree = null;
                var queue = new java.util.LinkedList();
                queue.add(component);
                while (!queue.isEmpty() && !tree) {
                    var current = queue.poll();
                    if (current == null) continue;
                    if (current.getClass().getName().endsWith("LstCrcAsyncChangesTree") || current.getClass().getName().endsWith("ChangesTree")) {
                        tree = current;
                        break;
                    }
                    try {
                        var children = current.getComponents();
                        if (children) {
                            for (var i = 0; i < children.length; i++) {
                                queue.add(children[i]);
                            }
                        }
                    } catch (ignored) {}
                }
                if (!tree) {
                    return "0";
                }
                var parent = tree.getParent();
                while (parent != null && !(parent instanceof javax.swing.JViewport)) {
                    parent = parent.getParent();
                }
                if (parent == null) {
                    return "0";
                }
                var scrollPane = parent.getParent();
                while (scrollPane != null && !(scrollPane instanceof javax.swing.JScrollPane)) {
                    scrollPane = scrollPane.getParent();
                }
                if (scrollPane == null) {
                    return "0";
                }
                var scrollBar = scrollPane.getVerticalScrollBar();
                return scrollBar == null ? "0" : String(scrollBar.getValue());
            })()
            """.trimIndent(),
            true
        ).toIntOrNull() ?: 0
    }

    fun dispatchTreeMouseWheelUp(notches: Int = 1) {
        step("Dispatch mouse wheel up over changes tree $notches time(s)") {
            runJs(
                """
                (function() {
                    var tree = null;
                    var queue = new java.util.LinkedList();
                    queue.add(component);
                    while (!queue.isEmpty() && !tree) {
                        var current = queue.poll();
                        if (current == null) continue;
                        if (current.getClass().getName().endsWith("LstCrcAsyncChangesTree") || current.getClass().getName().endsWith("ChangesTree")) {
                            tree = current;
                            break;
                        }
                        try {
                            var children = current.getComponents();
                            if (children) {
                                for (var i = 0; i < children.length; i++) {
                                    queue.add(children[i]);
                                }
                            }
                        } catch (ignored) {}
                    }
                    if (!tree) {
                        return;
                    }
                    var parent = tree.getParent();
                    while (parent != null && !(parent instanceof javax.swing.JViewport)) {
                        parent = parent.getParent();
                    }
                    if (parent == null) {
                        return;
                    }
                    var scrollPane = parent.getParent();
                    while (scrollPane != null && !(scrollPane instanceof javax.swing.JScrollPane)) {
                        scrollPane = scrollPane.getParent();
                    }
                    if (scrollPane == null) {
                        return;
                    }
                    var scrollBar = scrollPane.getVerticalScrollBar();
                    if (scrollBar == null) {
                        return;
                    }
                    for (var i = 0; i < $notches; i++) {
                        var beforeValue = scrollBar.getValue();
                        var x = Math.max(1, Math.floor(tree.getWidth() / 2));
                        var y = Math.max(1, Math.floor(tree.getHeight() / 2));
                        var treeEvent = new java.awt.event.MouseWheelEvent(
                            tree,
                            java.awt.event.MouseEvent.MOUSE_WHEEL,
                            java.lang.System.currentTimeMillis(),
                            0,
                            x,
                            y,
                            0,
                            false,
                            java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL,
                            3,
                            -1
                        );
                        tree.dispatchEvent(treeEvent);

                        var scrollEvent = new java.awt.event.MouseWheelEvent(
                            scrollPane,
                            java.awt.event.MouseEvent.MOUSE_WHEEL,
                            java.lang.System.currentTimeMillis(),
                            0,
                            Math.max(1, Math.floor(scrollPane.getWidth() / 2)),
                            Math.max(1, Math.floor(scrollPane.getHeight() / 2)),
                            0,
                            false,
                            java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL,
                            3,
                            -1
                        );
                        scrollPane.dispatchEvent(scrollEvent);

                        if (scrollBar.getValue() == beforeValue) {
                            var fallback = Math.max(scrollBar.getMinimum(), beforeValue - scrollBar.getUnitIncrement(-1) * 3);
                            scrollBar.setValue(fallback);
                        }
                    }
                })()
                """.trimIndent(),
                true
            )
        }
    }

    fun treeRowCount(): Int = step("Read changes tree row count") {
        callJs<String>(
            """
            (function() {
                var tree = null;
                var queue = new java.util.LinkedList();
                queue.add(component);
                while (!queue.isEmpty() && !tree) {
                    var current = queue.poll();
                    if (current == null) continue;
                    if (current.getClass().getName().endsWith("LstCrcAsyncChangesTree") || current.getClass().getName().endsWith("ChangesTree")) {
                        tree = current;
                        break;
                    }
                    try {
                        var children = current.getComponents();
                        if (children) {
                            for (var i = 0; i < children.length; i++) {
                                queue.add(children[i]);
                            }
                        }
                    } catch (ignored) {}
                }
                if (!tree) {
                    return "0";
                }
                return String(tree.getRowCount());
            })()
            """.trimIndent(),
            true
        ).toIntOrNull() ?: 0
    }

    fun selectedChangeEntry(): String = step("Read selected changes tree entry") {
        callJs<String>(
            """
            (function() {
                var tree = null;
                var queue = new java.util.LinkedList();
                queue.add(component);
                while (!queue.isEmpty() && !tree) {
                    var current = queue.poll();
                    if (current == null) continue;
                    if (current.getClass().getName().endsWith("LstCrcAsyncChangesTree") || current.getClass().getName().endsWith("ChangesTree")) {
                        tree = current;
                        break;
                    }
                    try {
                        var children = current.getComponents();
                        if (children) {
                            for (var i = 0; i < children.length; i++) {
                                queue.add(children[i]);
                            }
                        }
                    } catch (ignored) {}
                }
                if (!tree) {
                    return "";
                }
                var path = tree.getSelectionPath();
                if (path == null) {
                    return "";
                }
                return String(path.getLastPathComponent());
            })()
            """.trimIndent(),
            true
        )
    }

    fun beginTreeViewportTracking() {
        step("Start tracking changes tree viewport movement") {
            runJs(
                """
                (function() {
                    function findTree(root) {
                        var queue = new java.util.LinkedList();
                        queue.add(root);
                        while (!queue.isEmpty()) {
                            var current = queue.poll();
                            if (current == null) continue;
                            if (current.getClass().getName().endsWith("LstCrcAsyncChangesTree") || current.getClass().getName().endsWith("ChangesTree")) {
                                return current;
                            }
                            try {
                                var children = current.getComponents();
                                if (children) {
                                    for (var i = 0; i < children.length; i++) {
                                        queue.add(children[i]);
                                    }
                                }
                            } catch (ignored) {}
                        }
                        return null;
                    }

                    var tree = findTree(component);
                    if (!tree) return;

                    var viewport = tree.getParent();
                    while (viewport != null && !(viewport instanceof javax.swing.JViewport)) {
                        viewport = viewport.getParent();
                    }
                    if (viewport == null) return;

                    var previousListener = viewport.getClientProperty("lstcrc.viewport.listener");
                    if (previousListener != null) {
                        viewport.removeChangeListener(previousListener);
                    }

                    var history = new java.util.ArrayList();
                    function capture() {
                        var point = viewport.getViewPosition();
                        history.add(String(point.x) + "," + String(point.y));
                    }

                    var listener = new javax.swing.event.ChangeListener({
                        stateChanged: function(event) { capture(); }
                    });

                    capture();
                    viewport.addChangeListener(listener);
                    viewport.putClientProperty("lstcrc.viewport.listener", listener);
                    viewport.putClientProperty("lstcrc.viewport.history", history);
                })()
                """.trimIndent(),
                true
            )
        }
    }

    fun stopTreeViewportTracking(): List<Pair<Int, Int>> = step("Stop tracking changes tree viewport movement") {
        callJs<String>(
            """
            (function() {
                function findTree(root) {
                    var queue = new java.util.LinkedList();
                    queue.add(root);
                    while (!queue.isEmpty()) {
                        var current = queue.poll();
                        if (current == null) continue;
                        if (current.getClass().getName().endsWith("LstCrcAsyncChangesTree") || current.getClass().getName().endsWith("ChangesTree")) {
                            return current;
                        }
                        try {
                            var children = current.getComponents();
                            if (children) {
                                for (var i = 0; i < children.length; i++) {
                                    queue.add(children[i]);
                                }
                            }
                        } catch (ignored) {}
                    }
                    return null;
                }

                var tree = findTree(component);
                if (!tree) return "";

                var viewport = tree.getParent();
                while (viewport != null && !(viewport instanceof javax.swing.JViewport)) {
                    viewport = viewport.getParent();
                }
                if (viewport == null) return "";

                var listener = viewport.getClientProperty("lstcrc.viewport.listener");
                if (listener != null) {
                    viewport.removeChangeListener(listener);
                    viewport.putClientProperty("lstcrc.viewport.listener", null);
                }

                var history = viewport.getClientProperty("lstcrc.viewport.history");
                viewport.putClientProperty("lstcrc.viewport.history", null);
                if (history == null) return "";

                var parts = [];
                for (var i = 0; i < history.size(); i++) {
                    parts.push(String(history.get(i)));
                }
                return parts.join(";");
            })()
            """.trimIndent(),
            true
        ).split(';')
            .filter { it.isNotBlank() }
            .map { token ->
                token.split(',', limit = 2).let { parts ->
                    (parts.getOrNull(0)?.toIntOrNull() ?: 0) to (parts.getOrNull(1)?.toIntOrNull() ?: 0)
                }
            }
    }

    fun beginTopVisibleEntryTracking() {
        step("Start tracking top visible changes tree entry") {
            runJs(
                """
                (function() {
                    function findTree(root) {
                        var queue = new java.util.LinkedList();
                        queue.add(root);
                        while (!queue.isEmpty()) {
                            var current = queue.poll();
                            if (current == null) continue;
                            if (current.getClass().getName().endsWith("LstCrcAsyncChangesTree") || current.getClass().getName().endsWith("ChangesTree")) {
                                return current;
                            }
                            try {
                                var children = current.getComponents();
                                if (children) {
                                    for (var i = 0; i < children.length; i++) {
                                        queue.add(children[i]);
                                    }
                                }
                            } catch (ignored) {}
                        }
                        return null;
                    }

                    function topVisibleEntry(tree, viewport) {
                        var row = tree.getClosestRowForLocation(0, viewport.getViewPosition().y + 1);
                        if (row < 0) return "";
                        var path = tree.getPathForRow(row);
                        if (path == null) return "";
                        return String(path.getLastPathComponent());
                    }

                    var tree = findTree(component);
                    if (!tree) return;

                    var viewport = tree.getParent();
                    while (viewport != null && !(viewport instanceof javax.swing.JViewport)) {
                        viewport = viewport.getParent();
                    }
                    if (viewport == null) return;

                    var previousViewportListener = tree.getClientProperty("lstcrc.topVisible.viewportListener");
                    if (previousViewportListener != null) {
                        viewport.removeChangeListener(previousViewportListener);
                    }
                    var previousModelListener = tree.getClientProperty("lstcrc.topVisible.modelListener");
                    if (previousModelListener != null) {
                        tree.getModel().removeTreeModelListener(previousModelListener);
                    }

                    var history = new java.util.ArrayList();
                    function capture() {
                        history.add(topVisibleEntry(tree, viewport));
                    }

                    var viewportListener = new javax.swing.event.ChangeListener({
                        stateChanged: function(event) { capture(); }
                    });
                    var modelListener = new javax.swing.event.TreeModelListener({
                        treeNodesChanged: function(event) { capture(); },
                        treeNodesInserted: function(event) { capture(); },
                        treeNodesRemoved: function(event) { capture(); },
                        treeStructureChanged: function(event) { capture(); }
                    });

                    capture();
                    viewport.addChangeListener(viewportListener);
                    tree.getModel().addTreeModelListener(modelListener);
                    tree.putClientProperty("lstcrc.topVisible.history", history);
                    tree.putClientProperty("lstcrc.topVisible.viewportListener", viewportListener);
                    tree.putClientProperty("lstcrc.topVisible.modelListener", modelListener);
                })()
                """.trimIndent(),
                true
            )
        }
    }

    fun stopTopVisibleEntryTracking(): List<String> = step("Stop tracking top visible changes tree entry") {
        callJs<String>(
            """
            (function() {
                function findTree(root) {
                    var queue = new java.util.LinkedList();
                    queue.add(root);
                    while (!queue.isEmpty()) {
                        var current = queue.poll();
                        if (current == null) continue;
                        if (current.getClass().getName().endsWith("LstCrcAsyncChangesTree") || current.getClass().getName().endsWith("ChangesTree")) {
                            return current;
                        }
                        try {
                            var children = current.getComponents();
                            if (children) {
                                for (var i = 0; i < children.length; i++) {
                                    queue.add(children[i]);
                                }
                            }
                        } catch (ignored) {}
                    }
                    return null;
                }

                var tree = findTree(component);
                if (!tree) return "";

                var viewport = tree.getParent();
                while (viewport != null && !(viewport instanceof javax.swing.JViewport)) {
                    viewport = viewport.getParent();
                }
                if (viewport == null) return "";

                var viewportListener = tree.getClientProperty("lstcrc.topVisible.viewportListener");
                if (viewportListener != null) {
                    viewport.removeChangeListener(viewportListener);
                    tree.putClientProperty("lstcrc.topVisible.viewportListener", null);
                }
                var modelListener = tree.getClientProperty("lstcrc.topVisible.modelListener");
                if (modelListener != null) {
                    tree.getModel().removeTreeModelListener(modelListener);
                    tree.putClientProperty("lstcrc.topVisible.modelListener", null);
                }

                var history = tree.getClientProperty("lstcrc.topVisible.history");
                tree.putClientProperty("lstcrc.topVisible.history", null);
                if (history == null) return "";

                var parts = [];
                for (var i = 0; i < history.size(); i++) {
                    parts.push(String(history.get(i)).replace(/;/g, ","));
                }
                return parts.join(";");
            })()
            """.trimIndent(),
            true
        ).split(';').filter { it.isNotBlank() }
    }

    fun selectChangeInTree(fileName: String) {
        step("Select '$fileName' in changes tree without scrolling") {
            runJs(
                """
                (function() {
                    function findBrowser(root) {
                        var queue = new java.util.LinkedList();
                        queue.add(root);
                        while (!queue.isEmpty()) {
                            var current = queue.poll();
                            if (current == null) continue;
                            if (current.getClass().getName().endsWith("LstCrcChangesBrowser")) {
                                return current;
                            }
                            try {
                                var children = current.getComponents();
                                if (children) {
                                    for (var i = 0; i < children.length; i++) {
                                        queue.add(children[i]);
                                    }
                                }
                            } catch (ignored) {}
                        }
                        return null;
                    }

                    var browser = findBrowser(component);
                    if (!browser || typeof browser.selectVisibleFileForTest !== "function") {
                        return;
                    }

                    browser.selectVisibleFileForTest(${toJsStringLiteral(fileName)});
                })()
                """.trimIndent(),
                true
            )
        }
    }

    fun setTreeViewportPosition(x: Int = 0, y: Int) {
        step("Scroll changes tree viewport to x=$x y=$y") {
            runJs(
                """
                (function() {
                    var tree = null;
                    var queue = new java.util.LinkedList();
                    queue.add(component);
                    while (!queue.isEmpty() && !tree) {
                        var current = queue.poll();
                        if (current == null) continue;
                        if (current.getClass().getName().endsWith("LstCrcAsyncChangesTree") || current.getClass().getName().endsWith("ChangesTree")) {
                            tree = current;
                            break;
                        }
                        try {
                            var children = current.getComponents();
                            if (children) {
                                for (var i = 0; i < children.length; i++) {
                                    queue.add(children[i]);
                                }
                            }
                        } catch (ignored) {}
                    }
                    if (!tree) {
                        return;
                    }
                    var parent = tree.getParent();
                    while (parent != null && !(parent instanceof javax.swing.JViewport)) {
                        parent = parent.getParent();
                    }
                    if (parent != null) {
                        parent.setViewPosition(new java.awt.Point($x, $y));
                    }
                })()
                """.trimIndent(),
                true
            )
        }
    }

    fun rightClickTab(tabName: String) {
        step("Right click tab '$tabName'") {
            waitFor(Duration.ofSeconds(10), interval = Duration.ofMillis(250)) {
                remoteRobot.findAll<ComponentFixture>(tabLocator(tabName)).isNotEmpty()
            }
            remoteRobot.findAll<ComponentFixture>(tabLocator(tabName)).first().runJs(
                """
                const x = Math.max(1, Math.floor(component.getWidth() / 2));
                const y = Math.max(1, Math.floor(component.getHeight() / 2));
                const now = java.lang.System.currentTimeMillis();

                const pressed = new java.awt.event.MouseEvent(
                    component,
                    java.awt.event.MouseEvent.MOUSE_PRESSED,
                    now,
                    java.awt.event.InputEvent.BUTTON3_DOWN_MASK,
                    x,
                    y,
                    1,
                    true,
                    java.awt.event.MouseEvent.BUTTON3
                );
                const released = new java.awt.event.MouseEvent(
                    component,
                    java.awt.event.MouseEvent.MOUSE_RELEASED,
                    now + 1,
                    0,
                    x,
                    y,
                    1,
                    true,
                    java.awt.event.MouseEvent.BUTTON3
                );

                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(new java.lang.Runnable({
                    run: function() {
                        component.dispatchEvent(pressed);
                        component.dispatchEvent(released);
                    }
                }));
                """.trimIndent(),
                true
            )
        }
    }

    fun addTab() {
        step("Click 'Add Tab' button") {
            val addTabLocator = byXpath("//div[@accessiblename='Add Tab']")
            waitFor(Duration.ofSeconds(10), interval = Duration.ofMillis(250)) {
                remoteRobot.findAll<ComponentFixture>(addTabLocator).isNotEmpty()
            }

            remoteRobot.find<ComponentFixture>(addTabLocator).click()

            val branchSelectionOpenTimeout = if (System.getenv("GITHUB_ACTIONS") == "true") Duration.ofSeconds(30) else Duration.ofSeconds(10)
            val openedFromClick = runCatching {
                waitFor(branchSelectionOpenTimeout, interval = Duration.ofMillis(250)) {
                    remoteRobot.findAll<ComponentFixture>(branchSelectionPanelLocator).isNotEmpty()
                }
                true
            }.getOrDefault(false)

            if (!openedFromClick) {
                remoteRobot.runJs(
                    """
                    const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
                    if (project) {
                        const toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("GitChangesView");
                        const pluginId = com.intellij.openapi.extensions.PluginId.getId("com.github.uiopak.lstcrc");
                        const plugin = com.intellij.ide.plugins.PluginManagerCore.getPlugin(pluginId);
                        if (toolWindow != null && plugin != null) {
                            const helperClass = plugin.getPluginClassLoader()
                                .loadClass("com.github.uiopak.lstcrc.toolWindow.ToolWindowHelper");
                            const helper = helperClass.getField("INSTANCE").get(null);
                            helperClass
                                .getMethod(
                                    "openBranchSelectionTab",
                                    com.intellij.openapi.project.Project,
                                    com.intellij.openapi.wm.ToolWindow
                                )
                                .invoke(helper, project, toolWindow);
                        }
                    }
                    """.trimIndent(),
                    true
                )

                waitFor(branchSelectionOpenTimeout, interval = Duration.ofMillis(250)) {
                    remoteRobot.findAll<ComponentFixture>(branchSelectionPanelLocator).isNotEmpty()
                }
            }
        }
    }

    fun invokeRenameTabAction(tabName: String) {
        step("Invoke rename action for tab '$tabName'") {
            waitFor(Duration.ofSeconds(10), interval = Duration.ofMillis(250)) {
                remoteRobot.findAll<ComponentFixture>(tabLocator(tabName)).isNotEmpty()
            }
            val tab = remoteRobot.findAll<ComponentFixture>(tabLocator(tabName)).first()
            tab.click()
            tab.runJs(
                """
                const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
                if (!project) {
                    throw new java.lang.IllegalStateException("No open project available for RenameTabAction");
                }

                const toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("GitChangesView");
                if (!toolWindow) {
                    throw new java.lang.IllegalStateException("GitChangesView tool window is not available");
                }

                const action = com.intellij.openapi.actionSystem.ActionManager.getInstance()
                    .getAction("com.github.uiopak.lstcrc.RenameTabAction");
                if (!action) {
                    throw new java.lang.IllegalStateException("RenameTabAction is not registered");
                }

                const dataContext = com.intellij.openapi.actionSystem.impl.SimpleDataContext.builder()
                    .add(com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT, project)
                    .add(com.intellij.openapi.actionSystem.PlatformDataKeys.TOOL_WINDOW, toolWindow)
                    .add(com.intellij.openapi.actionSystem.PlatformCoreDataKeys.CONTEXT_COMPONENT, component)
                    .build();
                const event = com.intellij.openapi.actionSystem.AnActionEvent.createEvent(
                    action,
                    dataContext,
                    null,
                    "test",
                    com.intellij.openapi.actionSystem.ActionUiKind.NONE,
                    null
                );

                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(new java.lang.Runnable({
                    run: function() {
                        action.actionPerformed(event);
                    }
                }));
                """.trimIndent(),
                true
            )

            waitFor(Duration.ofSeconds(10), interval = Duration.ofMillis(250)) {
                remoteRobot.callJs(
                    """
                    (function() {
                        const focusOwner = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                        return focusOwner instanceof javax.swing.text.JTextComponent;
                    })();
                    """.trimIndent(),
                    true
                )
            }
        }
    }
}
