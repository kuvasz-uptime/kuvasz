#!/bin/bash

# This script is necessary because of a bug in Gradle regarding
# environment variables not being passed correctly through with Java 21
# see: https://github.com/gradle/gradle/issues/10483

# Stop the Gradle daemon
./gradlew --stop

# Ensure /usr/local/bin is in the PATH (if necessary)
export PATH=$PATH:/usr/local/bin

# Build the Docker image using Jib
./gradlew jibDockerBuild
