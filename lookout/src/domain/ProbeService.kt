package xyz.deseteral.jaruga.lookout.domain

class ProbeService(
    private val campgroundRepository: CampgroundRepository,
    private val timelineService: TimelineService,
    private val notificationService: NotificationService,
) {
    suspend fun startProbing() {
        campgroundRepository.getSystemStatusUpdates().collect { systemStatusUpdates ->
            timelineService.insertLatest(systemStatusUpdates)
            timelineService.findChecksThatFailedDuringRecentTick().forEach { failedCheck ->
                notificationService.sendNotification(failedCheck)
            }
        }
    }
}