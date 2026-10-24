package com.spozon.guard

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * No source file may be longer than [MAX_LINES]. Files that already were are listed in `file-size-baseline.txt` (`path<space>lines`)
 * and may only shrink: the list is a ratchet, not a licence. Split a growing file by responsibility instead of raising a number.
 */
class SourceFileSizeGuardTest {

    private val root = File("src/main/kotlin")

    private fun baseline(): Map<String, Int> =
        File("src/test/resources/file-size-baseline.txt").readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .associate { line -> line.substringBeforeLast(' ') to line.substringAfterLast(' ').toInt() }

    @Test
    fun `no source file is over the line limit unless it is grandfathered and not growing`() {
        val allowed = baseline()
        val offenders = root.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .map { it.path.removePrefix("src/main/kotlin/") to it.useLines { lines -> lines.count() } }
            .filter { (path, lines) -> lines > MAX_LINES && lines > (allowed[path] ?: MAX_LINES) }
            .map { (path, lines) -> "$path has $lines lines (limit ${allowed[path] ?: MAX_LINES})" }
            .toList()
        assertTrue(offenders.isEmpty(), "Split these by responsibility:\n" + offenders.joinToString("\n"))
    }

    @Test
    fun `a grandfathered file that has shrunk under the limit is taken off the list`() {
        val stale = baseline().filter { (path, _) -> (File(root, path).takeIf { it.exists() }?.useLines { it.count() } ?: 0) <= MAX_LINES }
        assertTrue(stale.isEmpty(), "Remove from file-size-baseline.txt: ${stale.keys}")
    }

    private companion object {
        const val MAX_LINES = 800
    }
}
