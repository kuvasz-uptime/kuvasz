#!/bin/sh
VERSION=""
TAG=""
DIR="$(cd "$(dirname "$0")" && pwd)"

while getopts 'v:' OPTION; do
  case "$OPTION" in
    v) VERSION=$OPTARG ;;
    *) exit 1 ;;
  esac
done

TAG="${VERSION}-native"

echo "Building docker image with tag: ${TAG}..."
docker build . -t kuvaszmonitoring/kuvasz:${TAG} -t kuvaszmonitoring/kuvasz:latest-native --build-arg VERSION=${VERSION}
echo "Building docker image with tag: ${TAG}...OK"
