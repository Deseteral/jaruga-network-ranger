package xyz.deseteral.jaruga.ranger.trail.domain

data class CampgroundContext(
    val hass: Map<String, Hass?> = mutableMapOf(),
    val teslaMate: Map<String, TeslaMate?> = mutableMapOf(),
) {
    data class Hass(
        val states: List<EntityState>,
    ) {
        data class EntityState(
            val entityId: String,
            val state: String,
            // TODO: Implement lastChanged.
            // TODO: Implement attributes.
        ) {
            val canBeUpdated: Boolean = entityId.startsWith("update.") && state == "on"
        }
    }

    data class TeslaMate(
        val isUpdateAvailable: Boolean,
    )
}
