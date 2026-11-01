package com.example.localbackup.backup

import kotlin.coroutines.cancellation.CancellationException

/**
 * Cause used when the user explicitly cancels a backup. Any other cancellation (for example the
 * screen being closed) is not a user decision and is recorded as INTERRUPTED instead.
 */
class UserCancellationException : CancellationException("Backup cancelled by user")

/** True if this cancellation (or any of its causes) is an explicit user cancellation. */
fun Throwable.isUserCancellation(): Boolean =
    generateSequence(this) { it.cause }.take(10).any { it is UserCancellationException }
