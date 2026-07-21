package xyz.deseteral.jaruga.network.ranger.domain

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class SystemStatus(
    val checks: Map<CheckResult.CheckId, CheckResult>,
) {

    @Serializable
    data class CheckResult(
        val description: String?,
        val state: State,
        val severity: Severity,
        val timestamp: Instant,
    ) {

        @JvmInline
        @Serializable
        value class CheckId(val value: String)

        @Serializable
        enum class State {
            SUCCESS,
            FAILURE,
            ;
        }

        @Serializable
        enum class Severity {
            MAJOR,
            MINOR,
        }
    }
}