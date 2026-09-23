package com.github.uiopak.lstcrc.utils

/**
 * Returns true when [revision] looks like a full or abbreviated commit hash (7–40 hex characters).
 *
 * Kept local instead of using `git4idea.GitUtil.isHashString`, which is not available in every
 * supported IDE version (removed in 263) and would fail with `NoSuchMethodError` at runtime.
 */
internal fun isCommitHash(revision: String): Boolean =
    revision.length in 7..40 && revision.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
