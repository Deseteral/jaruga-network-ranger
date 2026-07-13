package xyz.deseteral.jaruga.campground.external

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import xyz.deseteral.jaruga.campground.configuration.Configuration
import xyz.deseteral.jaruga.campground.domain.datasources.TeslaMateRepository
import xyz.deseteral.jaruga.campground.domain.datasources.TeslaMateState

private val logger = KotlinLogging.logger {}

class TeslaMateClient(
    private val config: Configuration.DataSource.TeslaMate,
) : TeslaMateRepository {
    override suspend fun getState(): TeslaMateState? {
        val instanceVersion = getInstanceVersion() ?: return null
        val latestVersion = getLatestVersion() ?: return null

        return TeslaMateState(
            isUpdateAvailable = instanceVersion != latestVersion,
        )
    }

    private suspend fun getInstanceVersion(): String? {
        return try {
            httpRawTextClient
                .get("${config.instanceUrl}/settings") { expectSuccess = true }
                .body<String>()
                .replace(" ", "").replace("\n", "")
                .let { VERSION_FROM_HTML_REGEX.find(it)?.groupValues?.get(1) }
        } catch (ex: Exception) {
            logger.error(ex) { "Error while getting TeslaMate instance version." }
            null
        }
    }

    private suspend fun getLatestVersion(): String? {
        return try {
            githubApiClient
                .get("https://api.github.com/repos/teslamate-org/teslamate/releases/latest")
                .body<GitHubReleasesLatestResponse>()
                .name.drop(1)  // Drop "v" from "vX.Y.Z".
        } catch (ex: Exception) {
            logger.error(ex) { "Error while getting TeslaMate latest version from GitHub." }
            null
        }
    }

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

    companion object {
        private val VERSION_FROM_HTML_REGEX = """>Version</th><td>(\d+\.\d+\.\d+)<""".toRegex()
    }
}

@Serializable
private data class GitHubReleasesLatestResponse(val name: String)