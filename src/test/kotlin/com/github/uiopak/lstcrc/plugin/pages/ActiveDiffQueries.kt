package com.github.uiopak.lstcrc.plugin.pages

import com.intellij.remoterobot.RemoteRobot

/*
 * Read-only queries of the plugin's active comparison, through its public services (no test bridge).
 * They return the same line formats as the IDE Starter bridge (`LstCrcUiTestBridge`), so both suites
 * compare against the same `GitDiffOracle` expectations. Paths are relative to the project root.
 */

private const val PROJECT_AND_PLUGIN_LOOKUP = """
    var project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
    var plugin = com.intellij.ide.plugins.PluginManagerCore.getPlugin(
        com.intellij.openapi.extensions.PluginId.getId("com.github.uiopak.lstcrc"));
    var cl = plugin.getPluginClassLoader();
    var basePath = String(project.getBasePath()).split('\\').join('/');
    while (basePath.length > 1 && basePath.charAt(basePath.length - 1) == '/') basePath = basePath.substring(0, basePath.length - 1);
    function rel(path) {
        var normalized = String(path).split('\\').join('/');
        return normalized.indexOf(basePath + '/') == 0 ? normalized.substring(basePath.length + 1) : normalized;
    }
    function service(className) { return project.getService(cl.loadClass(className)); }
    var diffData = service("com.github.uiopak.lstcrc.services.ProjectActiveDiffDataService");
"""

/**
 * The active comparison, sorted: `A<TAB>path`, `M<TAB>path`, `D<TAB>path`, `R<TAB>old<TAB>new`, then
 * `S<TAB>before<TAB>after<TAB>added<TAB>removed`. The first line is `branch=<name>|lineStats=<included>`.
 */
fun RemoteRobot.activeDiffEntries(): String = callJs(
    """
    (function() {
        $PROJECT_AND_PLUGIN_LOOKUP
        var changes = diffData.getCategorizedChanges();
        var header = "branch=" + diffData.getActiveBranchName() + "|lineStats=" + (changes != null && changes.getLineStatsIncluded());
        if (changes == null) return header;

        var entries = [];
        var all = changes.getAllChanges();
        for (var i = 0; i < all.size(); i++) {
            var change = all.get(i);
            var before = change.getBeforeRevision() != null ? rel(change.getBeforeRevision().getFile().getPath()) : null;
            var after = change.getAfterRevision() != null ? rel(change.getAfterRevision().getFile().getPath()) : null;
            if (before == null) entries.push("A\t" + after);
            else if (after == null) entries.push("D\t" + before);
            else if (before == after) entries.push("M\t" + after);
            else entries.push("R\t" + before + "\t" + after);
        }
        entries.sort();

        var stats = [];
        var iterator = changes.getLineStatsByChange().entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var key = entry.getKey();
            stats.push("S\t" + (key.getBeforePath() != null ? rel(key.getBeforePath()) : "") +
                "\t" + (key.getAfterPath() != null ? rel(key.getAfterPath()) : "") +
                "\t" + entry.getValue().getAddedLines() + "\t" + entry.getValue().getRemovedLines());
        }
        stats.sort();

        return [header].concat(entries).concat(stats).join("\n");
    })();
    """.trimIndent(),
    false
)

/** The subset of [relativePaths] that the LSTCRC scope [scopeId] (e.g. `LSTCRC.Created`) contains, sorted. */
fun RemoteRobot.filesMatchingScope(scopeId: String, relativePaths: Collection<String>): Set<String> = callJs<String>(
    """
    (function() {
        $PROJECT_AND_PLUGIN_LOOKUP
        var scopes = cl.loadClass("com.github.uiopak.lstcrc.scopes.LstCrcProvidedScopes").getField("INSTANCE").get(null).getAllScopes();
        var scope = null;
        for (var i = 0; i < scopes.size(); i++) {
            if (String(scopes.get(i).getScopeId()) == ${jsString(scopeId)}) scope = scopes.get(i);
        }
        if (scope == null) throw new java.lang.IllegalStateException("Unknown LSTCRC scope " + ${jsString(scopeId)});

        // Deleted and revision-backed files are not on disk; the comparison holds them.
        var comparisonFiles = {};
        var lists = [diffData.getCreatedFiles(), diffData.getModifiedFiles(), diffData.getMovedFiles(), diffData.getDeletedFiles()];
        for (var l = 0; l < lists.length; l++) {
            for (var f = 0; f < lists[l].size(); f++) comparisonFiles[String(lists[l].get(f).getPath())] = lists[l].get(f);
        }

        var holder = com.intellij.psi.search.scope.packageSet.NamedScopeManager.getInstance(project);
        var fileSystem = com.intellij.openapi.vfs.LocalFileSystem.getInstance();
        var candidates = ${jsString(relativePaths.joinToString("\n"))}.split("\n");
        var matches = [];
        for (var c = 0; c < candidates.length; c++) {
            if (candidates[c].length == 0) continue;
            var absolutePath = basePath + "/" + candidates[c];
            var file = fileSystem.findFileByPath(absolutePath) || comparisonFiles[absolutePath];
            if (file != null && scope.getValue().contains(file, project, holder)) matches.push(candidates[c]);
        }
        matches.sort();
        return matches.join("\n");
    })();
    """.trimIndent(),
    true
).lines().filter(String::isNotBlank).toSet()

/**
 * Runs a real "Find in Files" search for [text] (case-sensitive, plain text) in the LSTCRC search scope
 * named [scopeDisplayName] and returns the matching files, sorted.
 */
fun RemoteRobot.findInFilesPaths(text: String, scopeDisplayName: String): List<String> = callJs<String>(
    """
    (function() {
        $PROJECT_AND_PLUGIN_LOOKUP
        var searchScopes = cl.loadClass("com.github.uiopak.lstcrc.scopes.LstCrcProvidedScopes").getField("INSTANCE").get(null).searchScopes(project);
        var scope = null;
        for (var i = 0; i < searchScopes.size(); i++) {
            if (String(searchScopes.get(i).getDisplayName()) == ${jsString(scopeDisplayName)}) scope = searchScopes.get(i);
        }
        if (scope == null) throw new java.lang.IllegalStateException("Unknown search scope " + ${jsString(scopeDisplayName)});

        var model = new com.intellij.find.FindModel();
        model.setStringToFind(${jsString(text)});
        model.setCaseSensitive(true);
        model.setProjectScope(false);
        model.setCustomScope(true);
        model.setCustomScope(scope);

        // A Java collector: the search calls the processor on pooled threads.
        var usages = java.util.Collections.synchronizedList(new java.util.ArrayList());
        com.intellij.find.impl.FindInProjectUtil.findUsages(
            model,
            project,
            new com.intellij.util.CommonProcessors.CollectProcessor(usages),
            new com.intellij.usages.FindUsagesProcessPresentation(new com.intellij.usages.UsageViewPresentation())
        );

        return com.intellij.openapi.application.ApplicationManager.getApplication().runReadAction(new com.intellij.openapi.util.Computable({
            compute: function() {
                var files = {};
                for (var u = 0; u < usages.size(); u++) {
                    var file = usages.get(u).getVirtualFile();
                    if (file != null) files[rel(file.getPath())] = true;
                }
                var paths = Object.keys(files);
                paths.sort();
                return paths.join("\n");
            }
        }));
    })();
    """.trimIndent(),
    false
).lines().filter(String::isNotBlank)

/**
 * Makes the IDE notice changes made outside it (git CLI, file writes), as it would on regaining focus:
 * a VFS refresh of the project and `.git`, and a dirty VCS scope. The plugin's own listeners must turn
 * that into a refresh; nothing here asks the plugin to reload.
 */
fun RemoteRobot.refreshAfterExternalChange() = runJs(
    """
    var project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
    var projectDir = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
        .refreshAndFindFileByPath(String(project.getBasePath()).split('\\').join('/'));
    if (projectDir != null) {
        projectDir.refresh(false, true);
        var gitDir = projectDir.findChild(".git");
        if (gitDir != null) gitDir.refresh(false, true);
    }
    com.intellij.openapi.vcs.changes.VcsDirtyScopeManager.getInstance(project).markEverythingDirty();
    """.trimIndent(),
    false
)

/** True once the plugin has found the project's Git repository. */
fun RemoteRobot.isGitRepositoryDetected(): Boolean = callJs(
    """
    (function() {
        $PROJECT_AND_PLUGIN_LOOKUP
        return !service("com.github.uiopak.lstcrc.services.GitService").getRepositories().isEmpty();
    })();
    """.trimIndent(),
    true
)

private fun jsString(value: String): String = buildString {
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
