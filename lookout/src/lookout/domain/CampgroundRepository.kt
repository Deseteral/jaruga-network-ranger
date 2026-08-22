package xyz.deseteral.jaruga.ranger.lookout.domain

import kotlinx.coroutines.flow.Flow
import xyz.deseteral.jaruga.network.ranger.domain.SystemStatus

interface CampgroundRepository {
    suspend fun getSystemStatusUpdates(): Flow<SystemStatus?>
}