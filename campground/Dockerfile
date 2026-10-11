# syntax=docker/dockerfile:1
#
# Build context must be the repository root:
#   docker build -t jaruga-network-ranger-campground -f campground/Dockerfile .

# ---------- Stage 1: build the executable jar with the Kotlin Toolchain ----------
FROM eclipse-temurin:25-jdk-noble AS build

# curl is required by the Kotlin Toolchain bootstrap script
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /project

# Everything the build needs is whitelisted in .dockerignore
COPY . .

RUN chmod +x kotlin \
    && ./kotlin task :campground:executableJarJvm

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:25-jre-noble

# curl powers the healthcheck; unzip replaces the JDK's jar tool
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl unzip \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# The executable jar packs the application and its dependencies as nested jars.
# Unpack BOOT-INF/lib so every jar is a real file on disk: the Kotlin scripting
# host that compiles config.campground.kts at startup cannot read nested entries.
COPY --from=build /project/build/tasks/_campground_executableJarJvm/campground-jvm-executable.jar /tmp/app.jar
RUN unzip -q -j /tmp/app.jar "BOOT-INF/lib/*" -d /app/lib \
    && rm /tmp/app.jar

VOLUME /app/data
ENV CAMPGROUND_DATA_PATH=/app/data

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl --fail --silent "http://localhost:${PORT:-8080}/status" > /dev/null || exit 1

CMD ["java", "-cp", "lib/*", "xyz.deseteral.jaruga.ranger.campground.ApplicationKt"]
