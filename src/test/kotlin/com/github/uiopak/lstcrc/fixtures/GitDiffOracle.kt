package com.github.uiopak.lstcrc.fixtures

import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

/**
 * Expected comparison results computed with the git CLI in [repoDir], using the same diff options as
 * the plugin, so UI tests can check that the plugin reports exactly what git reports. Paths are
 * relative to [repoDir], with `/` separators.
 *
 * Every call runs git again: ask after a step's action (checkout, edits), not before.
 */
class GitDiffOracle(val repoDir: Path) {

    /** One change as the UI tests report it: `A path`, `M path`, `D path` or `R old new`. */
    data class Entry(val status: Char, val before: String?, val after: String?) {
        val line: String
            get() = when (status) {
                'A' -> "A\t$after"
                'D' -> "D\t$before"
                'M' -> "M\t$after"
                else -> "R\t$before\t$after"
            }
    }

    fun entries(target: String): List<Entry> =
        git("diff", "--name-status", "-M", "--diff-filter=ADCMRUXT", target)
            .lineSequence()
            .filter(String::isNotBlank)
            .mapNotNull { line ->
                val fields = line.split('\t')
                when (fields[0].first()) {
                    'A' -> Entry('A', null, fields[1])
                    'D' -> Entry('D', fields[1], null)
                    'M', 'T', 'U', 'X' -> Entry('M', fields[1], fields[1])
                    'R', 'C' -> Entry('R', fields[1], fields[2])
                    else -> null
                }
            }
            .sortedBy(Entry::line)
            .toList()

    /** `S<TAB>before<TAB>after<TAB>added<TAB>removed` lines, sorted; binary files are skipped. */
    fun lineStats(target: String): List<String> {
        val byPath = entries(target).flatMap { entry -> listOfNotNull(entry.before, entry.after).map { it to entry } }.toMap()
        return numstat(target)
            .filter { it.added != null && it.removed != null }
            .mapNotNull { record ->
                val entry = if (record.oldPath != null) Entry('R', record.oldPath, record.path) else byPath[record.path] ?: return@mapNotNull null
                "S\t${entry.before.orEmpty()}\t${entry.after.orEmpty()}\t${record.added}\t${record.removed}"
            }
            .sorted()
    }

    fun binaryPaths(target: String): Set<String> =
        numstat(target).filter { it.added == null }.flatMap { listOfNotNull(it.oldPath, it.path) }.toSet()

    /** Paths worth asking the plugin's scopes about: both sides of every change. */
    fun scopeCandidates(target: String): List<String> =
        entries(target).flatMap { listOfNotNull(it.before, it.after) }.distinct()

    /** Scope id to the paths it must contain (and, among [scopeCandidates], only those). */
    fun expectedScopes(target: String): Map<String, Set<String>> {
        val entries = entries(target)
        fun afterPaths(vararg statuses: Char) = entries.filter { it.status in statuses }.mapNotNull { it.after }.toSet()
        return linkedMapOf(
            "LSTCRC.Created" to afterPaths('A'),
            "LSTCRC.Modified" to afterPaths('M'),
            "LSTCRC.Moved" to afterPaths('R'),
            "LSTCRC.Deleted" to entries.filter { it.status == 'D' }.mapNotNull { it.before }.toSet(),
            "LSTCRC.Changed" to afterPaths('A', 'M', 'R'),
        )
    }

    /** Changed (non-deleted, text) files whose working copy contains [text], sorted. */
    fun expectedFindInFiles(target: String, text: String): List<String> {
        val binary = binaryPaths(target)
        return entries(target)
            .filter { it.status != 'D' }
            .mapNotNull { it.after }
            .filter { it !in binary }
            .filter { path -> repoDir.resolve(path).let { it.isRegularFile() && text in it.readText() } }
            .sorted()
    }

    /**
     * Runs git in [repoDir]. The IDE runs git in the same repository, so a command that finds the
     * index locked is retried.
     */
    fun git(vararg args: String): String {
        var attempt = 0
        while (true) {
            val process = ProcessBuilder(listOf("git", *args))
                .directory(repoDir.toFile())
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            if (process.waitFor() == 0) return output
            check("index.lock" in output && ++attempt < 20) { "git ${args.joinToString(" ")} failed:\n$output" }
            Thread.sleep(500)
        }
    }

    private data class NumstatRecord(val added: Int?, val removed: Int?, val oldPath: String?, val path: String)

    private fun numstat(target: String): List<NumstatRecord> {
        val fields = git("diff", "--numstat", "-z", "-M", "--diff-filter=ADCMRUXT", "--ignore-cr-at-eol", target)
            .split('\u0000')
            .iterator()
        val records = mutableListOf<NumstatRecord>()
        while (fields.hasNext()) {
            val header = fields.next().trim('\n').split('\t')
            if (header.size < 3) continue
            records += if (header[2].isEmpty()) {
                NumstatRecord(header[0].toIntOrNull(), header[1].toIntOrNull(), fields.next(), fields.next())
            } else {
                NumstatRecord(header[0].toIntOrNull(), header[1].toIntOrNull(), null, header[2])
            }
        }
        return records
    }
}
