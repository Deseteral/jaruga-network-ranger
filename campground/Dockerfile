# syntax=docker/dockerfile:1
#
# Build context must be the repository root:
#   docker build -t jaruga-network-ranger-campground -f campground/Dockerfile .

# ---------- Stage 1: build the executable jar with the Kotlin Toolchain ----------
FROM eclipse-temurin:25-jdk-noble AS build

# curl is required by the Kotlin Toolchain bootstrap script.
# unzip is required to unpack the runtime dependencies from the executable jar.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl unzip \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /project

# Everything the build needs is whitelisted in .dockerignore
COPY . .

RUN chmod +x kotlin \
    && ./kotlin task :campground:executableJarJvm \
    && mkdir -p /out/lib \
    && unzip -q -j build/tasks/_campground_executableJarJvm/campground-jvm-executable.jar "BOOT-INF/lib/*" -d /out/lib

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

# The executable jar packs the application and its dependencies as nested jars.
# The Kotlin scripting host that compiles user configuration at startup needs
# them as real files on disk, so BOOT-INF/lib was unpacked in the build stage.
COPY --from=build /out/lib /app/lib

VOLUME /app/data
ENV CAMPGROUND_DATA_PATH=/app/data

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD wget -q -O /dev/null "http://localhost:${PORT:-8080}/health"

CMD ["java", "-cp", "lib/*", "xyz.deseteral.jaruga.ranger.campground.ApplicationKt"]
