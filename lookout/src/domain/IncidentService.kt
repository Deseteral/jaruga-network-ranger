package xyz.deseteral.jaruga.lookout.domain

import xyz.deseteral.jaruga.network.ranger.domain.CheckId
import xyz.deseteral.jaruga.network.ranger.domain.Incident

class IncidentService(
    private val timelineService: TimelineService,
) {

    private val activeIncidents: MutableMap<CheckId, Incident> = mutableMapOf()
    private val resolvedIncidents: MutableList<Incident> = mutableListOf()

    fun processTimelineDiff(diff: TimelineService.TimelinesUpdateDiff) {
        for (checkId in diff.degraded) {
            // Make sure we're only processing new incidents.
            if (activeIncidents.containsKey(checkId)) continue

            val checkResult = timelineService.getCheckResult(checkId) ?: continue

            activeIncidents[checkId] = Incident(
                checkId = checkId,
                triggeredOn = checkResult.timestamp,
                resolvedOn = null,
                severity = checkResult.severity,
                description = checkResult.description,
                history = listOf(Incident.HistoryEntry(timestamp = checkResult.timestamp, state = checkResult.state)),
            )
        }

        for (checkId in diff.healed) {
            // Make sure we're tracking incident for this ID.
            val currentIncident = activeIncidents[checkId] ?: continue
            val checkResult = timelineService.getCheckResult(checkId) ?: continue

            val resolvedIncident = currentIncident.copy(
                resolvedOn = checkResult.timestamp,
                history = currentIncident.history + Incident.HistoryEntry(timestamp = checkResult.timestamp, state = checkResult.state),
            )

            resolvedIncidents.add(resolvedIncident)
            activeIncidents.remove(checkId)
        }
    }
}