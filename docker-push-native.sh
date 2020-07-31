#!/bin/sh
TAG=""
DIR="$(cd "$(dirname "$0")" && pwd)"

while getopts 't:' OPTION; do
  case "$OPTION" in
    t)
      TAG=$OPTARG
      ;;
  esac
done

echo "Pushing docker image with tag: ${TAG}..."
docker push kuvaszmonitoring/kuvasz:${TAG}
echo "Pushing docker image with tag: ${TAG}...OK"
