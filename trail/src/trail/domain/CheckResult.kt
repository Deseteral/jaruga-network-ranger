package xyz.deseteral.jaruga.ranger.trail.domain

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@JvmInline
@Serializable
value class CheckId(val value: String)

@Serializable
data class CheckResult(
    val description: String?,
    val state: State,
    val severity: Severity,
    val timestamp: Instant,
) {
    @Serializable
    enum class State {
        SUCCESS,
        FAILURE,
    }

    @Serializable
    enum class Severity {
        MAJOR,
        MINOR,
    }
}

object SystemChecks {
    val ALL_CHECKS_IN_UNKNOWN_STATE_CHECK_ID = CheckId("All checks in unknown state")

    val RESERVED_CHECK_NAMES: Set<String> = listOf(ALL_CHECKS_IN_UNKNOWN_STATE_CHECK_ID).map { it.value }.toSet()

    fun allChecksInUnknownStateResult(state: CheckResult.State, timestamp: Instant) = CheckResult(
        state = state,
        severity = CheckResult.Severity.MAJOR,
        timestamp = timestamp,
        description = when (state) {
            CheckResult.State.FAILURE -> "Could not acquire system status update. All checks are in unknown state. Check Campground instance."
            CheckResult.State.SUCCESS -> null
        },
    )
}