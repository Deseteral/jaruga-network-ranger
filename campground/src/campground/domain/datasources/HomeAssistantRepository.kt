package xyz.deseteral.jaruga.ranger.campground.domain.datasources

import com.github.michaelbull.result.Result
import xyz.deseteral.jaruga.ranger.trail.domain.CampgroundContext

interface HomeAssistantRepository {
    suspend fun getHass(): Result<CampgroundContext.Hass, HomeAssistantError>
}

sealed interface HomeAssistantError {
    data object Unavailable : HomeAssistantError
}
