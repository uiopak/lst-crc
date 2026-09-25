package com.github.uiopak.lstcrc.starter

import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.time.Duration

/**
 * Collects step timings from UI tests into `build/reports/lstcrc-performance/results.jsonl`
 * (one JSON object per line). The CI workflow turns the file into a job summary table.
 *
 * Timings are report-only: CI runners are slow and noisy, so tests only fail on hang-level limits.
 */
object LstCrcPerformanceReport {
    private val resultsFile: Path = Path.of("build", "reports", "lstcrc-performance", "results.jsonl")

    @Synchronized
    fun record(test: String, step: String, duration: Duration, detail: String = "") {
        resultsFile.parent.createDirectories()
        val os = System.getProperty("os.name").orEmpty()
        val line = """{"test":${json(test)},"step":${json(step)},"ms":${duration.inWholeMilliseconds},"detail":${json(detail)},"os":${json(os)}}"""
        resultsFile.writeText("$line\n", options = arrayOf(StandardOpenOption.CREATE, StandardOpenOption.APPEND))
        println("[lstcrc-perf] $test | $step | ${duration.inWholeMilliseconds} ms${if (detail.isEmpty()) "" else " | $detail"}")
    }

    private fun json(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                else -> append(char)
            }
        }
        append('"')
    }
}
