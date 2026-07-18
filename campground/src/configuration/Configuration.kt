package xyz.deseteral.jaruga.campground.configuration

import xyz.deseteral.jaruga.campground.domain.Context

data class Configuration(
    val sources: Map<String, DataSource>,
) {
    sealed class DataSource {
        data class HomeAssistant(
            val instanceUrl: String,
            val accessToken: String,
        ) : DataSource()

        data class TeslaMate(
            val instanceUrl: String,
        ): DataSource()
    }
}

// TODO: Add unique validation on name field.
data class CheckDefinition(
    val name: String,
    val description: ((ctx: Context) -> String)? = null,
    val severity: (ctx: Context) -> Severity,
    val isFailure: (ctx: Context) -> Boolean?,
) {
    enum class Severity {
        MAJOR,
        MINOR,
    }
}
