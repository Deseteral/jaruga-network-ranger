package xyz.deseteral.jaruga.campground.domain

import kotlinx.serialization.Serializable

@Serializable
data class Status(
    val checks: List<CheckResult>,
)

@Serializable
data class CheckResult(
    val name: String,
    val state: CheckState,
)

@Serializable
enum class CheckState {
    SUCCESS,
    FAILURE,
    ;
}