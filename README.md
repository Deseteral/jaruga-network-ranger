# Jaruga Network Ranger

A monitoring system for a homelab.

<p align="center">
  <img src="resources/campground_logo.svg" alt="Campground logo" height="200" /> <img src="resources/lookout_logo.svg" alt="Lookout logo" height="200" />
</p>


This system consists of two services:

- `Campground` reads data sources and evaluates checks.
- `Lookout` reads the Campground status, tracks incidents, and sends notifications.

You should run `Campground` inside your homelab, where it can freely access services it must monitor.

Host `Lookout` outside your homelab (for example, on a VPS) to prevent network problems from stopping
incident notifications.

## Configuration

The service configuration is defined in a Kotlin script file. This file is checked and evaluated
when the service starts. Any config issues prevent the respective services from starting.

### Environment variables

| Variable               | Function                          | Default                               |
|------------------------|-----------------------------------|---------------------------------------|
| `PORT`                 | The HTTP port for the service.    | 8080 for Campground, 8081 for Lookout |
| `CAMPGROUND_DATA_PATH` | The data directory of Campground. | `data`                                |
| `LOOKOUT_DATA_PATH`    | The data directory of Lookout.    | `data`                                |

The data directory is used to keep the user configuration and files required by the services for
their normal operation.

### Campground

Config location: `<data_path>/config.campground.kts`.

Example:

```kotlin
import xyz.deseteral.jaruga.ranger.trail.config.CampgroundConfig
import xyz.deseteral.jaruga.ranger.trail.domain.CheckResult

CampgroundConfig(
    sources = mapOf(
        "ha" to CampgroundConfig.DataSource.HomeAssistant(
            instanceUrl = "http://localhost:9930/ha",
            accessToken = "hass-dev-token",
        ),
    ),
    checks = listOf(
        CampgroundConfig.CheckDefinition(
            name = "HomeAssistant has update available",
            isFailure = { ctx -> ctx.hass["ha"]?.states?.any { it.canBeUpdated } },
            description = { ctx -> "${ctx.hass["ha"]?.states?.count { it.canBeUpdated }} updates available." },
            severity = { CheckResult.Severity.MINOR },
        ),
    ),
)
```

A check reads its source data from the context, for example `ctx.hass["ha"]`. An `isFailure` result of `true` or `null`
makes the check fail. The check names must be unique.

### Lookout

Config location: `<data_path>/config.lookout.kts`.

Example:

```kotlin
import xyz.deseteral.jaruga.ranger.trail.config.LookoutConfig
import kotlin.time.Duration.Companion.seconds

LookoutConfig(
    campground = LookoutConfig.Campground(
        instanceUrl = "http://localhost:8080",
        interval = 5.seconds,
    ),
    bufferSize = 12,
    notifier = LookoutConfig.Notifier(
        retryPolicy = LookoutConfig.Notifier.RetryPolicy(maxAttempts = 3),
        process = { incident ->
            LookoutConfig.NotificationChannel.DiscordWebhook("https://discord.com/api/webhooks/...")
        },
    ),
)
```

## Development

### Building

This Kotlin project is built with the [Kotlin Toolchain](https://kotlin-toolchain.org/).
Modules are declared in `project.yaml`, and the `kotlin` wrapper script in the repository root provisions and runs the toolchain.
For more information refer to Kotlin Toolchain documentation.

### Docker images

Both services ship as a Docker image. 
Read `campground/Dockerfile` and `lookout/Dockerfile` for more information about images.
Build them from the repository root:

```sh
docker build -t jaruga-network-ranger-campground -f campground/Dockerfile .
docker build -t jaruga-network-ranger-lookout -f lookout/Dockerfile .
```

Each service expects a data directory mounted at `/app/data` containing its configuration file.

### Versioning

All components - Campground, Lookout and the Trail library - share a single version number and are released together.
Identical versions are compatible with each other. Version numbers follow [Semantic Versioning](https://semver.org/).

### Releasing

1. Bump `settings.publishing.version` in `trail/module.yaml` and merge the change to `main`.
2. Create a matching tag and push it.

The "Publish" workflow verifies the tag matches the declared version, then publishes the Docker images
and the Trail library to GitHub Packages.

Between releases, merges to `main` keep publishing the Docker images with `latest` and `sha-<commit>` tags.


### Mock server

The mock server for local development replaces the need for real data sources.
There is a simple admin UI at <http://localhost:9930>.

```sh
cd mock-server
bun mock-server.ts
```

## License

This project is licensed under the [MIT license](LICENSE).
