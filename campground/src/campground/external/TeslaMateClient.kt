package xyz.deseteral.jaruga.ranger.campground.external

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.github.michaelbull.result.get
import com.github.michaelbull.result.onErr
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import xyz.deseteral.jaruga.ranger.campground.domain.datasources.TeslaMateError
import xyz.deseteral.jaruga.ranger.campground.domain.datasources.TeslaMateRepository
import xyz.deseteral.jaruga.ranger.campground.utils.PeriodicallyComputedValue
import xyz.deseteral.jaruga.ranger.trail.config.CampgroundConfig
import xyz.deseteral.jaruga.ranger.trail.domain.CampgroundContext
import kotlin.time.Duration.Companion.hours

private val logger = KotlinLogging.logger {}

class TeslaMateClient(
    private val config: CampgroundConfig.DataSource.TeslaMate,
) : TeslaMateRepository {
    private val latestVersion = PeriodicallyComputedValue(period = 1.hours) { getLatestVersion() }

    override suspend fun getState(): Result<CampgroundContext.TeslaMate, TeslaMateError> {
        val instanceVersion = getInstanceVersion() ?: return Err(TeslaMateError.Unavailable)
        val latestVersion = this.latestVersion.value ?: return Err(TeslaMateError.Unavailable)

        return Ok(
            CampgroundContext.TeslaMate(
                isUpdateAvailable = instanceVersion != latestVersion,
            ),
        )
    }

    private suspend fun getInstanceVersion(): String? {
        val url = URLBuilder(config.instanceUrl).apply {
            appendPathSegments("settings")
        }.toString()

        return runSuspendCatching {
            httpRawTextClient
                .get(url)
                .body<String>()
                .replace(" ", "").replace("\n", "")
                .let { VERSION_FROM_HTML_REGEX.find(it)?.groupValues?.get(1) }
        }
            .onErr { ex -> logger.error(ex) { "Error while getting TeslaMate instance version." } }
            .get()
    }

    private suspend fun getLatestVersion(): String? {
        return runSuspendCatching {
            githubApiClient
                .get("https://api.github.com/repos/teslamate-org/teslamate/releases/latest")
                .body<GitHubReleasesLatestResponse>()
                .name.drop(1)  // Drop "v" from "vX.Y.Z".
        }
            .onErr { ex -> logger.error(ex) { "Error while getting TeslaMate latest version from GitHub." } }
            .get()
    }

    companion object {
        private val VERSION_FROM_HTML_REGEX = """>Version</th><td>(\d+\.\d+\.\d+)<""".toRegex()

        private val httpRawTextClient = HttpClient(CIO) {
            expectSuccess = true
        }

        private val githubApiClient = HttpClient(CIO) {
            expectSuccess = true
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        namingStrategy = JsonNamingStrategy.SnakeCase
                    },
                )
            }
        }
    }
}

@Serializable
private data class GitHubReleasesLatestResponse(val name: String)
