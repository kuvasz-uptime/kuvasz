#!/usr/bin/env bash
# Builds the Docker image locally. Releases are built by .github/workflows/docker.yml, natively on each architecture.
# Usage: ./docker-build.sh [--no-aot] [--push]
#   --no-aot  skips the training of the AOT cache, for a quick image to try something out (tagged <version>-no-aot)
#   --push    builds for amd64 and arm64, and pushes <version> and latest to Docker Hub. On Apple Silicon, the amd64
#             image is trained under emulation, which takes minutes and trains a weaker cache than the CI build
set -euo pipefail
cd "$(dirname "$0")"

IMAGE=kuvaszmonitoring/kuvasz
PLATFORMS=linux/amd64,linux/arm64
target=()
output=(--load)
for arg in "$@"; do
  case "$arg" in
    --no-aot) target=(--target base) ;;
    --push) output=(--platform "$PLATFORMS" --push) ;;
    *) echo "Unknown argument: $arg" >&2; exit 1 ;;
  esac
done
if [ ${#target[@]} -gt 0 ] && [ "${output[0]}" != "--load" ]; then
  echo "An image without the AOT cache must not be pushed" >&2
  exit 1
fi

./gradlew :app:dockerLayers
version=$(./gradlew -q :app:properties --property version | sed -n 's/^version: //p')
# A quick image gets its own tag, so it can't be mistaken for (or pushed as) a release
if [ ${#target[@]} -gt 0 ]; then
  tags=(-t "$IMAGE:$version-no-aot")
else
  tags=(-t "$IMAGE:$version" -t "$IMAGE:latest")
fi
# The ${target[@]+...} form keeps bash 3.2 (macOS) from failing on an empty array with set -u
docker buildx build ${target[@]+"${target[@]}"} "${output[@]}" "${tags[@]}" \
  --build-arg VERSION="$version" --build-arg REVISION="$(git rev-parse HEAD)" \
  -f app/docker/Dockerfile app
