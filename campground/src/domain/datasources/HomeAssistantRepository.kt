package xyz.deseteral.jaruga.campground.domain.datasources

import kotlinx.serialization.Serializable
import xyz.deseteral.jaruga.campground.configuration.Configuration

interface HomeAssistantRepository {
    suspend fun getHass(): Hass?
}

typealias HomeAssistantRepositoryProvider = (dataSource: Configuration.DataSource.HomeAssistant) -> HomeAssistantRepository

data class Hass(
    val states: List<HomeAssistantState>,
) {
    val hasUpdatesAvailable: Boolean
        get() = states.any { it.entityId.startsWith("update.") && it.state == "on" }
}

@Serializable
data class HomeAssistantState(
    val entityId: String,
    val state: String,
    // val lastChanged // TODO: Implement!
    // val attributes // TODO: Implement!
)