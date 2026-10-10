import xyz.deseteral.jaruga.ranger.trail.config.CampgroundConfig
import xyz.deseteral.jaruga.ranger.trail.domain.CheckResult

CampgroundConfig(
    sources = mapOf(
        "ha" to CampgroundConfig.DataSource.HomeAssistant(
            instanceUrl = "http://localhost:9930/ha",
            accessToken = "hass-dev-token",
        ),
        "tm" to CampgroundConfig.DataSource.TeslaMate(
            instanceUrl = "http://localhost:9930/tm",
        ),
    ),
    checks = listOf(
        CampgroundConfig.CheckDefinition(
            name = "HomeAssistant has update available",
            isFailure = { ctx -> ctx.hass["ha"]?.states?.any { it.canBeUpdated } },
            description = { ctx -> "${ctx.hass["ha"]?.states?.count { it.canBeUpdated }} updates available." },
            severity = { CheckResult.Severity.MINOR },
        ),
        CampgroundConfig.CheckDefinition(
            name = "TeslaMate has update available",
            isFailure = { ctx -> ctx.teslaMate["tm"]?.isUpdateAvailable },
            severity = { CheckResult.Severity.MAJOR },
        ),
    ),
)
