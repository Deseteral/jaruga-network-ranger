package xyz.deseteral.jaruga.lookout.domain

import xyz.deseteral.jaruga.network.ranger.domain.SystemStatus
import xyz.deseteral.jaruga.network.ranger.domain.SystemStatus.CheckResult.State.FAILURE
import xyz.deseteral.jaruga.network.ranger.domain.SystemStatus.CheckResult.State.SUCCESS

class TimelineService(
    private val timelineSize: Int,
) {
    private typealias Timeline = ArrayDeque<SystemStatus.CheckResult?>

    init {
        require(timelineSize > 1) { "timelineSize must be greater than 1." }
    }

    /**
     * Keeps state of checks.
     * If each check is a row, then each column represents a point in time. Front of the queue is most recent.
     * Each row is a circular buffer of size `timelineSize`.
     */
    private val checks: MutableMap<SystemStatus.CheckResult.CheckId, Timeline> = mutableMapOf()

    private var ticks = 0

    fun insertLatest(systemStatusUpdate: SystemStatus?) {
        ticks += 1

        // systemStatus is null when we could not connect to Campground.
        // When that happens all existing known checks should be failed.
        if (systemStatusUpdate == null) {
            insertLatestAllFailsForExistingChecks()
            return
        }

        // Process all checks in update.
        // When update contains new check - add it with unknown (null) values and process latest update state.
        systemStatusUpdate.checks.forEach { check ->
            checks.getOrPut(check.name) { emptyTimeline() }
                .insertLatestCheck(check)
        }

        // When update does not contain checks that were previously known, set their latest state to unknown (null).
        val missingInUpdate = checks.keys.toSet().minus(systemStatusUpdate.checks.map { it.name }.toSet())
        missingInUpdate.forEach { checkId ->
            checks.getOrPut(checkId) { emptyTimeline() }
                .insertLatestCheck(null)
        }

        // TODO: Remove.
        debugPrint()
    }

    fun findChecksThatFailedDuringRecentTick(): List<SystemStatus.CheckResult> {
        val allCurrentInUnknownState = checks.values.all { it[0] == null }
        val allPreviousInUnknownState = checks.values.all { it[1] == null }

        return if (allCurrentInUnknownState && (!allPreviousInUnknownState || ticks == 1)) {
            listOf(allChecksInUnknownStateCheckResult())
        } else {
            checks.filter {
                val (current, previous) = it.value
                current?.state == FAILURE && (previous?.state == SUCCESS || previous == null)
            }.mapNotNull { it.value.first() }
        }
    }

    private fun insertLatestAllFailsForExistingChecks() =
        checks.values.forEach { it.insertLatestCheck(null) }

    private fun emptyTimeline(): Timeline =
        ArrayDeque(List(timelineSize) { null })

    private fun allChecksInUnknownStateCheckResult(): SystemStatus.CheckResult =
        SystemStatus.CheckResult(
            name = SystemStatus.CheckResult.CheckId("All checks in unknown state"),
            state = FAILURE,
            severity = SystemStatus.CheckResult.Severity.MAJOR,
            description = "Could not communicate with Campground instance."
        )

    // TODO: Remove.
    private fun debugPrint() {
        val statusToChar = { s: SystemStatus.CheckResult.State? ->
            when (s) {
                SUCCESS -> '1'
                FAILURE -> '0'
                null -> '.'
            }
        }

        checks.forEach { (id, timeline) ->
            println("${id.value.padEnd(50, ' ')} | ${timeline.map { statusToChar(it?.state) }.joinToString("")}")
        }
    }

    private fun Timeline.insertLatestCheck(check: SystemStatus.CheckResult?) {
        removeLast()
        addFirst(check)
    }
}