package xyz.deseteral.jaruga.ranger.lookout.domain

import xyz.deseteral.jaruga.ranger.trail.domain.CheckId
import xyz.deseteral.jaruga.ranger.trail.domain.CheckResult
import xyz.deseteral.jaruga.ranger.trail.domain.CheckResult.State.FAILURE
import xyz.deseteral.jaruga.ranger.trail.domain.CheckResult.State.SUCCESS
import xyz.deseteral.jaruga.ranger.trail.domain.SystemChecks.ALL_CHECKS_IN_UNKNOWN_STATE_CHECK_ID
import xyz.deseteral.jaruga.ranger.trail.domain.SystemChecks.allChecksInUnknownStateResult
import xyz.deseteral.jaruga.ranger.trail.domain.SystemStatus
import kotlin.time.Clock

/**
 * Keeps state of checks.
 * If each check is a row, then each column represents a point in time. Front of the queue is most recent.
 * Each row is a circular buffer of size [timelineSize].
 */
class TimelineService(
    private val timelineSize: Int,
    private val clock: Clock,
) {
    init {
        require(timelineSize > 1) { "timelineSize must be greater than 1." }
    }

    private val userDefinedChecks: MutableMap<CheckId, Timeline> = mutableMapOf()

    private val systemChecks: Map<CheckId, Timeline> = mapOf(
        ALL_CHECKS_IN_UNKNOWN_STATE_CHECK_ID to emptyTimeline(),
    )

    private val allChecks: Map<CheckId, Timeline>
        get() = systemChecks + userDefinedChecks

    private val internalStateLock = Any()

    /**
     * Returns timelines of all known checks.
     */
    fun getCheckTimelines(): Map<CheckId, Timeline> = synchronized(internalStateLock) {
        allChecks.mapValues { (_, timeline) -> ArrayDeque(timeline) }
    }

    /**
     * Returns latest [CheckResult] for given [CheckId].
     * Returns null if given ID is not recorded or the check's state is unknown.
     */
    fun getCheckResult(id: CheckId): CheckResult? = synchronized(internalStateLock) {
        (userDefinedChecks[id] ?: systemChecks[id])?.firstOrNull()
    }

    /**
     * Process latest system update.
     * Inserts new timeline entry for all known checks (both user defined and system checks).
     * If new update couldn't be obtained, then [[systemStatusUpdate]] should be null.
     *
     * Returns [Snapshot] which describes the current state of all known checks after the update was processed.
     */
    fun insertLatest(systemStatusUpdate: SystemStatus?): Snapshot {
        synchronized(internalStateLock) {
            // systemStatus is null when we could not connect to Campground.
            // When that happens all existing known checks should be failed.
            val isSuccessfulUpdate = systemStatusUpdate != null

            val missingInUpdate: Set<CheckId>

            if (isSuccessfulUpdate) {
                // Process all checks in update.
                // When update contains new check - add it with timeline full of unknown (null) values and process latest update state.
                systemStatusUpdate.checks.forEach { (checkId, check) ->
                    userDefinedChecks.getOrPut(checkId) { emptyTimeline() }
                        .insertLatestCheck(check)
                }

                missingInUpdate = userDefinedChecks.keys.toSet() - systemStatusUpdate.checks.keys.toSet()

                // When update does not contain checks that were previously known, set their latest state to unknown (null).
                missingInUpdate.forEach { checkId ->
                    userDefinedChecks.getOrPut(checkId) { emptyTimeline() }
                        .insertLatestCheck(null)
                }
            } else {
                insertLatestAllFailsForExistingChecks()
                missingInUpdate = emptySet()
            }

            systemChecks.getValue(ALL_CHECKS_IN_UNKNOWN_STATE_CHECK_ID).insertLatestCheck(
                allChecksInUnknownStateResult(state = if (isSuccessfulUpdate) SUCCESS else FAILURE, timestamp = clock.now()),
            )

            return Snapshot(
                latest = allChecks.mapValues { (_, timeline) -> timeline.firstOrNull() },
                absent = missingInUpdate,
                isCampgroundReachable = isSuccessfulUpdate,
            )
        }
    }

    private fun insertLatestAllFailsForExistingChecks() =
        userDefinedChecks.values.forEach { it.insertLatestCheck(null) }

    private fun emptyTimeline(): Timeline =
        ArrayDeque(List(timelineSize) { null })

    data class Snapshot(
        val latest: Map<CheckId, CheckResult?>,
        val absent: Set<CheckId>,
        val isCampgroundReachable: Boolean,
    )
}

private typealias Timeline = ArrayDeque<CheckResult?>

private fun Timeline.insertLatestCheck(check: CheckResult?) {
    removeLast()
    addFirst(check)
}
