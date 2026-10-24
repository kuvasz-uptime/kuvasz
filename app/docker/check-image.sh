#!/usr/bin/env bash
# Checks a built image: Java can use its AOT cache on a small machine (1 CPU, where Java picks the Serial GC) and on a
# bigger one (2 CPUs and 2 GB, where it picks G1), and the training tools didn't get into the image.
# Used by the CI workflows and docker-build.sh. Usage: app/docker/check-image.sh <image>
set -euo pipefail
IMAGE=$1

check_cache_with() {
  local gc=$1
  shift
  local output
  # The JVM options of the entrypoint, and -XX:AOTMode=on, which makes Java fail instead of starting without the cache
  if ! output=$(docker run --rm "$@" --entrypoint java "$IMAGE" -Xlog:gc -XX:+UseCompactObjectHeaders \
    -XX:AOTMode=on -XX:AOTCache=/home/app/kuvasz.aot @/home/app/jvm-args -version 2>&1); then
    echo "$output" >&2
    echo "Java can't use the AOT cache of $IMAGE with the $gc GC" >&2
    exit 1
  fi
  if ! grep -q "Using $gc" <<<"$output"; then
    echo "$output" >&2
    echo "Java didn't pick the $gc GC, so the check didn't test what it should" >&2
    exit 1
  fi
  echo "Java can use the AOT cache with the $gc GC"
}
check_cache_with Serial --cpus 1 --memory 512m
check_cache_with G1 --cpus 2 --memory 2g

if ! docker run --rm --entrypoint sh "$IMAGE" -c \
  'for tool in postgres initdb psql curl; do if command -v "$tool"; then exit 1; fi; done; test ! -e /training'; then
  echo "The training tools got into $IMAGE" >&2
  exit 1
fi
echo "No training tools in the image"
