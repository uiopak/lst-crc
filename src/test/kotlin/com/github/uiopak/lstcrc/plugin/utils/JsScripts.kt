package com.github.uiopak.lstcrc.plugin.utils

/*
 * JavaScript statements shared by the Remote Robot scripts. They declare their variables with `var`, so a script can
 * combine several of them (and repeat a name) inside one function.
 */

/** Declares [projectVariable]: the open test project, or undefined when none is open. */
fun jsOpenProject(projectVariable: String = "project"): String =
    "var $projectVariable = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];"

/** Declares [classLoaderVariable]: the LST-CRC plugin's class loader, or null when the plugin is not loaded. */
fun jsPluginClassLoader(classLoaderVariable: String = "cl"): String =
    """
    var lstCrcPlugin = com.intellij.ide.plugins.PluginManagerCore.getPlugin(
        com.intellij.openapi.extensions.PluginId.getId("com.github.uiopak.lstcrc"));
    var $classLoaderVariable = lstCrcPlugin ? lstCrcPlugin.getPluginClassLoader() : null;
    """.trimIndent()

/** Declares [projectVariable] and [toolWindowVariable]: the LST-CRC tool window, or null. */
fun jsToolWindow(projectVariable: String = "project", toolWindowVariable: String = "toolWindow"): String =
    """
    ${jsOpenProject(projectVariable)}
    var $toolWindowVariable = $projectVariable
        ? com.intellij.openapi.wm.ToolWindowManager.getInstance($projectVariable).getToolWindow("GitChangesView")
        : null;
    """.trimIndent()

/** Reloads the selected comparison and waits for the load to finish. Needs `project`. */
fun jsRefreshSelectedComparison(): String =
    """
    ${jsPluginClassLoader("refreshClassLoader")}
    if (project && refreshClassLoader) {
        var refreshStateService = project.getService(
            refreshClassLoader.loadClass("com.github.uiopak.lstcrc.services.ToolWindowStateService"));
        if (refreshStateService != null) refreshStateService.refreshDataForCurrentSelection().join();
    }
    """.trimIndent()

/**
 * Makes the IDE notice changes made outside it (git CLI, file writes), as it would on regaining focus: a VFS refresh
 * of the project and `.git`, and a dirty VCS scope. Needs `project`.
 */
fun jsNoticeExternalChanges(): String =
    """
    if (project) {
        var projectDir = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
            .refreshAndFindFileByPath(String(project.getBasePath()).split('\\').join('/'));
        if (projectDir != null) {
            projectDir.refresh(false, true);
            var gitDir = projectDir.findChild(".git");
            if (gitDir != null) gitDir.refresh(false, true);
        }
        com.intellij.openapi.vcs.changes.VcsDirtyScopeManager.getInstance(project).markEverythingDirty();
    }
    """.trimIndent()
