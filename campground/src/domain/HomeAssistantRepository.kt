package xyz.deseteral.jaruga.campground.domain

import kotlinx.serialization.Serializable

interface HomeAssistantRepository {
    suspend fun getHass(): Hass?
}

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