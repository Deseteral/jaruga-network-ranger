package xyz.deseteral.jaruga.campground.configuration

import xyz.deseteral.jaruga.campground.domain.Context

data class Configuration(
    val homeAssistant: HomeAssistant? = null,
) {
    data class HomeAssistant(
        val instanceUrl: String,
        val accessToken: String,
    )
}


data class CheckDefinition(
    val name: String,
    val severity: (ctx: Context) -> Severity,
    val isFailure: (ctx: Context) -> Boolean?,
) {
    enum class Severity {
        MAJOR,
        MINOR,
    }
}
