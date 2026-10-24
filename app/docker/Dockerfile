# The context is the app folder, after `./gradlew :app:dockerLayers` (see docker-build.sh in the root of the repo).
# The default target trains a JDK AOT cache during the build, so the image starts faster and uses less memory.
# `--target base` skips the training, for quick local builds.
# The layers are ordered from the least to the most often changing ones, so the build cache can reuse unchanged ones.
FROM eclipse-temurin:25-jre-alpine AS jre
ENV JAVA_TOOL_OPTIONS="-Xms64M -Xmx192M" \
    MICRONAUT_CONFIG_FILES=/config/kuvasz.yml
WORKDIR /home/app

# Only depends on the JRE, so it isn't rebuilt when the application changes
FROM jre AS training-tools
RUN apk add --no-cache postgresql curl
COPY docker/training/ /training/

FROM jre AS base
COPY docker/bootstrap/ /
COPY build/docker/layers/libs libs
COPY build/docker/layers/project-libs project-libs
COPY build/docker/layers/application.jar build/docker/layers/jvm-args ./
EXPOSE 8080
# The JVM options are in the entrypoint instead of an environment variable, so they stay when an operator sets
# JAVA_TOOL_OPTIONS or JDK_JAVA_OPTIONS. The AOT cache only works with the same options, see docker/training/train.sh.
# The main class is application.mainClass of app/build.gradle.kts.
ENTRYPOINT ["java", "-XX:+UseCompactObjectHeaders", "@/home/app/jvm-args", "com.kuvaszuptime.kuvasz.Application"]

# The same JRE, environment and application files (with their timestamps) as the final image, otherwise Java doesn't
# use the cache at runtime
FROM training-tools AS train
COPY --from=base /home/app /home/app
RUN /training/train.sh

FROM base
COPY --from=train /home/app/kuvasz.aot kuvasz.aot
ENTRYPOINT ["java", "-XX:+UseCompactObjectHeaders", "-XX:AOTCache=/home/app/kuvasz.aot", "@/home/app/jvm-args", \
    "com.kuvaszuptime.kuvasz.Application"]
ARG VERSION=dev
ARG REVISION=unknown
LABEL org.opencontainers.image.source="https://github.com/kuvasz-uptime/kuvasz" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${REVISION}" \
      org.opencontainers.image.description="Kuvasz (pronounce as [ˈkuvɒs]) is an open-source uptime and SSL monitoring service, with multiple notification channels, status pages, IAC support via YAML, Prometheus integration, a complete REST API and many more"
