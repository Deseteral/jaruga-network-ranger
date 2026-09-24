package xyz.deseteral.jaruga.ranger.campground.domain.datasources

import xyz.deseteral.jaruga.ranger.trail.domain.CampgroundContext

interface HomeAssistantRepository {
    suspend fun getHass(): CampgroundContext.Hass?
}