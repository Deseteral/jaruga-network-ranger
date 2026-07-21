package xyz.deseteral.jaruga.network.ranger.domain

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
        ;
    }

    @Serializable
    enum class Severity {
        MAJOR,
        MINOR,
    }
}
