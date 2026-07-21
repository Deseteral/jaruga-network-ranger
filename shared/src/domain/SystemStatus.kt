package xyz.deseteral.jaruga.network.ranger.domain

import kotlinx.serialization.Serializable

@Serializable
data class SystemStatus(
    val checks: Map<CheckId, CheckResult>,
)