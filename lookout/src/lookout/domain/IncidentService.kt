package xyz.deseteral.jaruga.ranger.lookout.domain

import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.onErr
import com.github.michaelbull.result.onOk
import io.github.oshai.kotlinlogging.KotlinLogging
import xyz.deseteral.jaruga.ranger.trail.domain.CheckId
import xyz.deseteral.jaruga.ranger.trail.domain.CheckResult
import xyz.deseteral.jaruga.ranger.trail.domain.CheckResult.State.FAILURE
import xyz.deseteral.jaruga.ranger.trail.domain.CheckResult.State.SUCCESS
import xyz.deseteral.jaruga.ranger.trail.domain.Incident
import kotlin.time.Clock
import kotlin.time.Instant

private val logger = KotlinLogging.logger {}

class IncidentService(
    private val notificationService: NotificationService,
    private val activeIncidentsCache: PersistentCache<MutableMap<CheckId, Incident>>,
    private val clock: Clock,
) {

    private val activeIncidents: MutableMap<CheckId, Incident> =
        activeIncidentsCache.read { mutableMapOf() }.getOrElse { mutableMapOf() }
    private val resolvedIncidents: MutableList<Incident> = mutableListOf()

    private var isActiveIncidentsCacheDirty = false

    suspend fun reconcile(snapshot: TimelineService.Snapshot) {
        for ((checkId, checkResult) in snapshot.latest) {
            when (checkResult?.state) {
                SUCCESS -> if (isIncidentOpenForCheck(checkId)) {
                    resolveIncident(checkId, checkResult.timestamp, checkResult.state, description = null)
                }

                FAILURE -> if (!isIncidentOpenForCheck(checkId)) {
                    openIncident(checkId, checkResult)
                }

                null -> continue
            }
        }

        snapshot.absent
            .filter { checkId -> isIncidentOpenForCheck(checkId) }
            .forEach { checkId ->
                resolveIncident(checkId, clock.now(), SUCCESS, description = REMOVED_CHECK_DESCRIPTION)
            }

        persistActiveIncidentsCache()
    }

    fun isIncidentOpenForCheck(checkId: CheckId): Boolean =
        activeIncidents.containsKey(checkId)

    private suspend fun openIncident(checkId: CheckId, checkResult: CheckResult) {
        val incident = Incident(
            checkId = checkId,
            triggeredOn = checkResult.timestamp,
            resolvedOn = null,
            severity = checkResult.severity,
            description = checkResult.description,
            history = listOf(Incident.HistoryEntry(timestamp = checkResult.timestamp, state = checkResult.state)),
        )

        activeIncidents.set(checkId, incident)
            .also { isActiveIncidentsCacheDirty = true }

        notifyForIncident(incident)
    }

    private suspend fun resolveIncident(
        checkId: CheckId,
        resolvedOn: Instant,
        state: CheckResult.State,
        description: String?,
    ) {
        val currentIncident = activeIncidents[checkId] ?: return

        val resolvedIncident = currentIncident.copy(
            resolvedOn = resolvedOn,
            description = description ?: currentIncident.description,
            history = currentIncident.history + Incident.HistoryEntry(timestamp = resolvedOn, state = state),
        )

        resolvedIncidents.add(resolvedIncident)
        activeIncidents.remove(checkId)
            .also { isActiveIncidentsCacheDirty = true }

        notifyForIncident(resolvedIncident)
    }

    private fun persistActiveIncidentsCache() {
        if (!isActiveIncidentsCacheDirty) return
        activeIncidentsCache.write(activeIncidents)
            .onOk { isActiveIncidentsCacheDirty = false }
            .onErr { logger.warn { "Could not persist active incidents cache ($it)." } }
    }

    private suspend fun notifyForIncident(incident: Incident) {
        notificationService.sendNotification(incident)
            .onErr { error -> logger.warn { "Could not send notification for incident ${incident.id} (check ${incident.checkId.value}): $error." } }
    }

    companion object {
        private const val REMOVED_CHECK_DESCRIPTION = "Check removed from configuration."
    }
}