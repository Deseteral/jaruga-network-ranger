package xyz.deseteral.jaruga.lookout.domain

import xyz.deseteral.jaruga.network.ranger.domain.SystemStatus
import xyz.deseteral.jaruga.network.ranger.domain.SystemStatus.CheckResult.Severity.MAJOR
import xyz.deseteral.jaruga.network.ranger.domain.SystemStatus.CheckResult.State.FAILURE
import xyz.deseteral.jaruga.network.ranger.domain.SystemStatus.CheckResult.State.SUCCESS

class TimelineService(
    private val timelineSize: Int,
) {
    init {
        require(timelineSize > 1) { "timelineSize must be greater than 1." }
    }

    private val checks: MutableMap<SystemStatus.CheckResult.CheckId, ArrayDeque<SystemStatus.CheckResult.State?>> =
        mutableMapOf()

    private val severity: MutableMap<SystemStatus.CheckResult.CheckId, SystemStatus.CheckResult.Severity> =
        mutableMapOf()

    fun insertLatest(systemStatusUpdate: SystemStatus?) {
        // systemStatus is null when we could not connect to Campground.
        // When that happens all existing known checks should be failed.
        if (systemStatusUpdate == null) {
            insertLatestAllFailsForExistingChecks()
            return
        }

        // Process all checks in update.
        // When update contains new check - add it with unknown (null) values and process latest update state.
        systemStatusUpdate.checks.forEach { check ->
            checks.getOrPut(check.name) { emptyTimeline() }.let {
                it.addFirst(check.state)
                it.removeLast()
            }

            // Update severity in case it changed.
            severity[check.name] = check.severity
        }

        // When update does not contain checks that were previously known, set their latest state to unknown (null).
        val missingInUpdate = checks.keys.toSet().minus(systemStatusUpdate.checks.map { it.name }.toSet())
        missingInUpdate.forEach { checkId ->
            checks.getOrPut(checkId) { emptyTimeline() }.let {
                it.addFirst(null)
                it.removeLast()
            }
        }

        // TODO: Remove.
        debugPrint()

        // TODO: Find checks that are null only and remove them.
    }

    fun findChecksThatFailedRecentUpdate(): Set<SystemStatus.CheckResult.CheckId> {
        return checks.filter {
            val (current, previous) = it.value
            current == FAILURE && (previous == SUCCESS || previous == null)
        }.map { it.key }.toSet()
    }

    fun getSeverityByCheckName(name: SystemStatus.CheckResult.CheckId): SystemStatus.CheckResult.Severity {
        return severity.getOrDefault(name, MAJOR)
    }

    private fun insertLatestAllFailsForExistingChecks() {
        checks.values.forEach {
            it.addFirst(FAILURE)
            it.removeLast()
        }
    }

    private fun emptyTimeline(): ArrayDeque<SystemStatus.CheckResult.State?> =
        ArrayDeque(List(timelineSize) { null })

    // TODO: Remove.
    private fun debugPrint() {
        val statusToChar = { s: SystemStatus.CheckResult.State? ->
            when(s) {
                SUCCESS -> '1'
                FAILURE -> '0'
                null -> '.'
            }
        }

        checks.forEach { (id, timeline) ->
            println("${id.value.padEnd(50, ' ')} | ${timeline.map { statusToChar(it) }.joinToString("")}")
        }
    }
}