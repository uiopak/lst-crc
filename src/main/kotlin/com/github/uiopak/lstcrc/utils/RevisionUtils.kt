package com.github.uiopak.lstcrc.utils

object RevisionUtils {
    /**
     * A heuristic to identify what looks like a commit hash.
     * We explicitly check for "HEAD" to ensure it's never considered a hash. A full hash is
     * 40 hex characters, while a short hash is typically 7-12. We check for a minimum length
     * and that all characters are hexadecimal.
     *
     * @param revision The revision string to check.
     * @return `true` if the string resembles a commit hash, `false` otherwise.
     */
    fun isCommitHash(revision: String): Boolean {
        if (revision.equals("HEAD", ignoreCase = true)) return false
        return revision.length >= 7 && revision.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
    }
}