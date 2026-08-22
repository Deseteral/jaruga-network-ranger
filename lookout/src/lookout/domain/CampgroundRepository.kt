package xyz.deseteral.jaruga.ranger.lookout.domain

import kotlinx.coroutines.flow.Flow
import xyz.deseteral.jaruga.ranger.trail.domain.SystemStatus

interface CampgroundRepository {
    suspend fun getSystemStatusUpdates(): Flow<SystemStatus?>
}