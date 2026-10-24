#!/usr/bin/env bash
# Builds the Docker image locally, for this machine. Releases are built by .github/workflows/docker.yml, natively on
# each architecture (it can be started by hand too, also for a branch or a tag).
# Usage: ./docker-build.sh [--no-aot]
#   (none)    builds the image with its AOT cache, checks it, and tags it <version> and latest
#   --no-aot  skips the training of the AOT cache, for a quick image to try something out (tagged <version>-no-aot)
set -euo pipefail
cd "$(dirname "$0")"

IMAGE=kuvaszmonitoring/kuvasz
no_aot=false
for arg in "$@"; do
  case "$arg" in
    --no-aot) no_aot=true ;;
    *) echo "Unknown argument: $arg" >&2; exit 1 ;;
  esac
done

./gradlew :app:dockerLayers
version=$(./gradlew -q :app:properties --property version | sed -n 's/^version: //p')
build=(docker buildx build -f app/docker/Dockerfile --build-arg VERSION="$version"
  --build-arg REVISION="$(git rev-parse HEAD)" --load)
if $no_aot; then
  # Its own tag, so it can't be mistaken for a release
  "${build[@]}" --target base -t "$IMAGE:$version-no-aot" app
else
  "${build[@]}" -t "$IMAGE:$version" -t "$IMAGE:latest" app
  app/docker/check-image.sh "$IMAGE:$version"
fi
