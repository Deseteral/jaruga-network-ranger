package xyz.deseteral.jaruga.campground.domain

import kotlinx.serialization.Serializable

@Serializable
data class SystemStatus(
    val checks: List<CheckResult>,
) {

    @Serializable
    data class CheckResult(
        val name: String,
        val state: State,
        val severity: Severity,
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
}
