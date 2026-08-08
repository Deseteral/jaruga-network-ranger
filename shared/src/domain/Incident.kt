package xyz.deseteral.jaruga.network.ranger.domain

import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class Incident(
    val id: Id = Id(),
    val checkId: CheckId,
    val triggeredOn: Instant,
    val resolvedOn: Instant?,
    val severity: CheckResult.Severity,
    val description: String?,
    val history: List<HistoryEntry>,
) {
    @JvmInline
    @Serializable
    value class Id(val value: Uuid = Uuid.random())

    @Serializable
    data class HistoryEntry(
        val timestamp: Instant,
        val state: CheckResult.State,
    )
}
