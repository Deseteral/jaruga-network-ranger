package xyz.deseteral.jaruga.campground.domain

data class Configuration(
    val homeAssistant: HomeAssistantConfiguration? = null,
)

data class HomeAssistantConfiguration(
    val instanceUrl: String,
    val accessToken: String,
)

data class SystemHealthCheck(
    val checks: List<Check>,
)

data class Check(
    val name: String,
    val isFailure: (context: Context) -> Boolean?,
)