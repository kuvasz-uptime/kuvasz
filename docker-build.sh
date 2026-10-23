#!/usr/bin/env bash
# Builds the Docker image locally. Releases are built by .github/workflows/docker.yml, natively on each architecture.
# Usage: ./docker-build.sh [--no-aot] [--push]
#   (none)    builds the image for this machine with its AOT cache, checks it, and tags it <version> and latest
#   --no-aot  skips the training of the AOT cache, for a quick image to try something out (tagged <version>-no-aot)
#   --push    only for a release without the CI: builds for amd64 and arm64, and pushes <version> and latest to
#             Docker Hub. Needs a clean checkout of a release tag (e.g. 4.6.0). On Apple Silicon, the amd64 image is
#             trained under emulation, which takes minutes and trains a weaker cache than the CI build
set -euo pipefail
cd "$(dirname "$0")"

IMAGE=kuvaszmonitoring/kuvasz
PLATFORMS=linux/amd64,linux/arm64
# The default builder of Docker Desktop can't build for two platforms at once (without the containerd image store),
# so a pushed image gets built by one with the docker-container driver, like in the CI
PUSH_BUILDER=kuvasz-release
no_aot=false
push=false
for arg in "$@"; do
  case "$arg" in
    --no-aot) no_aot=true ;;
    --push) push=true ;;
    *) echo "Unknown argument: $arg" >&2; exit 1 ;;
  esac
done
if $no_aot && $push; then
  echo "An image without the AOT cache must not be pushed" >&2
  exit 1
fi
if $push; then
  if [ -n "$(git status --porcelain)" ]; then
    echo "Only a clean checkout can be pushed, commit or stash your changes first" >&2
    exit 1
  fi
  if ! git describe --exact-match --tags HEAD 2>/dev/null | grep -qE '^[0-9]+\.[0-9]+\.[0-9]+$'; then
    echo "Only a release tag (e.g. 4.6.0) can be pushed, to test a branch, run the release workflow on it" >&2
    exit 1
  fi
fi

./gradlew :app:dockerLayers
version=$(./gradlew -q :app:properties --property version | sed -n 's/^version: //p')
build=(docker buildx build -f app/docker/Dockerfile --build-arg VERSION="$version"
  --build-arg REVISION="$(git rev-parse HEAD)")
if $no_aot; then
  # Its own tag, so it can't be mistaken for (or pushed as) a release
  "${build[@]}" --target base --load -t "$IMAGE:$version-no-aot" app
elif $push; then
  docker buildx inspect "$PUSH_BUILDER" >/dev/null 2>&1 \
    || docker buildx create --name "$PUSH_BUILDER" --driver docker-container >/dev/null
  "${build[@]}" --builder "$PUSH_BUILDER" --platform "$PLATFORMS" \
    --push -t "$IMAGE:$version" -t "$IMAGE:latest" app
else
  "${build[@]}" --load -t "$IMAGE:$version" -t "$IMAGE:latest" app
  app/docker/check-image.sh "$IMAGE:$version"
fi
