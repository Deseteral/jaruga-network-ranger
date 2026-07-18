package xyz.deseteral.jaruga.campground.domain.datasources

import kotlinx.serialization.Serializable
import xyz.deseteral.jaruga.campground.configuration.Configuration

interface HomeAssistantRepository {
    suspend fun getHass(): Hass?
}

typealias HomeAssistantRepositoryProvider = (dataSource: Configuration.DataSource.HomeAssistant) -> HomeAssistantRepository

data class Hass(
    val states: List<HomeAssistantState>,
)

@Serializable
data class HomeAssistantState(
    val entityId: String,
    val state: String,
    // val lastChanged // TODO: Implement!
    // val attributes // TODO: Implement!
) {
    val canBeUpdated: Boolean
        get() = entityId.startsWith("update.") && state == "on"
}