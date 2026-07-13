package xyz.deseteral.jaruga.network.ranger.domain

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class SystemStatus(
    val timestamp: Instant,
    val checks: List<CheckResult>,
) {

    @Serializable
    data class CheckResult(
        val name: CheckId,
        val state: State,
        val severity: Severity,
    ) {

        @JvmInline
        @Serializable
        value class CheckId(val id: String)

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