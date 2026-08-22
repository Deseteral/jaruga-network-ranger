package xyz.deseteral.jaruga.ranger.trail.domain

import kotlinx.serialization.Serializable

@Serializable
data class SystemStatus(
    val checks: Map<CheckId, CheckResult>,
)