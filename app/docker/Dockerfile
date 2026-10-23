# The context is the app folder, after `./gradlew :app:dockerLayers` (see docker-build.sh in the root of the repo).
# The default target trains a JDK AOT cache during the build, so the image starts faster and uses less memory.
# `--target base` skips the training, for quick local builds.
# The layers are ordered from the least to the most often changing ones, so the unchanged ones can be reused.
FROM eclipse-temurin:25-jre-alpine AS jre
# JDK_JAVA_OPTIONS is read after JAVA_TOOL_OPTIONS, so it stays when an operator replaces that one to change the heap
ENV JAVA_TOOL_OPTIONS="-Xms64M -Xmx192M" \
    JDK_JAVA_OPTIONS="-XX:+UseCompactObjectHeaders" \
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
ENTRYPOINT ["java", "@/home/app/jvm-args"]

# The same JRE, environment and application files (with their timestamps) as the final image, otherwise Java doesn't
# use the cache at runtime
FROM training-tools AS train
COPY --from=base /home/app /home/app
RUN /training/train.sh

FROM base
COPY --from=train /home/app/kuvasz.aot kuvasz.aot
# In the entrypoint instead of an environment variable, so it stays when an operator replaces the JVM options
ENTRYPOINT ["java", "-XX:AOTCache=/home/app/kuvasz.aot", "@/home/app/jvm-args"]
ARG VERSION=dev
ARG REVISION=unknown
LABEL org.opencontainers.image.source="https://github.com/kuvasz-uptime/kuvasz" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${REVISION}" \
      org.opencontainers.image.description="Kuvasz (pronounce as [ˈkuvɒs]) is an open-source uptime and SSL monitoring service, with multiple notification channels, status pages, IAC support via YAML, Prometheus integration, a complete REST API and many more"
