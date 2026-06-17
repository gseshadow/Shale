#!/usr/bin/env bash
set -euo pipefail

: "${SPRING_PROFILES_ACTIVE:=azure}"
: "${SHALE_SERVER_JAR:=/home/site/wwwroot/shale-server.jar}"

export SPRING_PROFILES_ACTIVE

if [[ ! -f "$SHALE_SERVER_JAR" ]]; then
  echo "Shale startup failed: jar not found at $SHALE_SERVER_JAR" >&2
  exit 1
fi

exec java ${JAVA_OPTS:-} -jar "$SHALE_SERVER_JAR"
