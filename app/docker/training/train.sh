#!/bin/sh
# Trains the JDK AOT cache of the image: starts a throwaway PostgreSQL and Kuvasz with -XX:AOTCacheOutput, sends a fixed
# list of UI and API requests, then stops Kuvasz gracefully, which is when the JVM writes the cache.
# Java starts the same way as in the ENTRYPOINT of the image (@/home/app/jvm-args) and with the same environment,
# otherwise it doesn't use the cache at runtime.
set -eu

CACHE="${AOT_CACHE:-/home/app/kuvasz.aot}"
# At least this many passes over the request list, repeated until TRAINING_SECONDS have passed since Kuvasz is healthy.
# Measured on 2026-10-23: a cache trained for 30 seconds starts faster on slow machines than one trained with a single
# pass, while longer trainings gained nothing.
ROUNDS="${TRAINING_ROUNDS:-1}"
SECONDS_TOTAL="${TRAINING_SECONDS:-30}"
PGDATA=/tmp/training-pg
KUVASZ_LOG=/tmp/training-kuvasz.log
COOKIES=/tmp/training-cookies
BASE_URL=http://localhost:8080
API_KEY=training-api-key-0123456789
PUSH_SECRET=9b0c6a8e-7a3c-4d1e-9f53-2f0b8c1d4e6a

fail() {
  echo "AOT training failed: $1" >&2
  tail -n 100 "$KUVASZ_LOG" >&2 || true
  exit 1
}

mkdir -p /run/postgresql "$PGDATA"
chown postgres:postgres /run/postgresql "$PGDATA"
su postgres -s /bin/sh -c "initdb -D $PGDATA -U kuvasz --auth=trust >/dev/null"
su postgres -s /bin/sh -c "pg_ctl -D $PGDATA -l /tmp/training-pg.log -w start >/dev/null"

# Leaves out the small machine code part of the cache (call adapters): with JDK 25, Java prints a warning about it on
# almost every start ("Failed to link AdapterHandlerEntry"), and with the Serial GC of small machines it can't use it
# anyway. Only applies to the JVM that writes the cache, the image doesn't need a diagnostic flag at runtime.
JDK_AOT_VM_OPTIONS="-XX:+UnlockDiagnosticVMOptions -XX:-AOTAdapterCaching" \
  DATABASE_HOST=localhost DATABASE_USER=kuvasz DATABASE_PASSWORD=training \
  ADMIN_USER=training ADMIN_PASSWORD=training-password ADMIN_API_KEY="$API_KEY" \
  ENABLE_CHECK_UPDATES=false MICRONAUT_CONFIG_FILES=/training/kuvasz.yml \
  java -XX:AOTCacheOutput="$CACHE" @/home/app/jvm-args >"$KUVASZ_LOG" 2>&1 &
KUVASZ_PID=$!

started=$(date +%s)
until curl -fs -o /dev/null "$BASE_URL/api/v2/health"; do
  kill -0 "$KUVASZ_PID" 2>/dev/null || fail "Kuvasz exited before it became healthy"
  [ $(($(date +%s) - started)) -lt 600 ] || fail "Kuvasz didn't become healthy in 10 minutes"
  sleep 1
done
healthy=$(date +%s)
echo "Kuvasz is healthy after $((healthy - started))s"

# Only the expected status counts: without a valid session, pages answer with a redirect to the login page, which
# curl -f would accept
request() {
  expected=$1
  path=$2
  shift 2
  status=$(curl -sS -o /dev/null -w '%{http_code}' "$@" "$BASE_URL$path") || fail "$path: no response"
  [ "$status" = "$expected" ] || fail "$path: $status instead of $expected"
}
ui() { request 200 "$1" -b "$COOKIES"; }
fragment() { request 200 "$1" -b "$COOKIES" -H "HX-Request: true"; }
api() { request 200 "$1" -H "X-API-KEY: $API_KEY"; }

monitor_id() {
  id=$(curl -fsS -H "X-API-KEY: $API_KEY" "$BASE_URL/api/v3/$1-monitors" | grep -o '"id":[0-9]*' | head -n 1 | cut -d: -f2)
  [ -n "$id" ] || fail "no $1 monitor found"
  echo "$id"
}

id_of() {
  case "$1" in
    http) echo "$http_id" ;;
    tcp) echo "$tcp_id" ;;
    push) echo "$push_id" ;;
    dns) echo "$dns_id" ;;
  esac
}

pass() {
  ui /
  fragment /fragments/dashboard
  for type in http tcp push dns; do
    id=$(id_of "$type")
    ui "/$type-monitors"
    fragment "/$type-monitors/fragments/list"
    ui "/$type-monitors/$id"
    fragment "/$type-monitors/fragments/details-heading/$id"
    fragment "/incidents/fragments/monitor/$type/$id"
    api "/api/v2/$type-monitors"
    api "/api/v2/$type-monitors/$id"
    api "/api/v2/$type-monitors/stats"
    api "/api/v3/$type-monitors?sort=name"
    api "/api/v3/$type-monitors/$id/uptime-events"
  done
  for type in http tcp dns; do
    id=$(id_of "$type")
    ui "/api/internal/monitor-charts/$type/$id?period=P1D"
  done
  fragment "/incidents/fragments/monitor/ssl/$http_id"
  fragment "/dns-monitors/fragments/snapshot/$dns_id"
  api "/api/v3/http-monitors/$http_id/ssl-events"
  ui /icmp-monitors
  ui /docker-monitors
  ui /incidents
  fragment /incidents/fragments/list
  api /api/v2/incidents
  api /api/v3/incidents
  api /api/v3/incidents/http
  ui /status-pages
  fragment /status-pages/fragments/list
  ui /status
  ui /status/training
  api /api/v2/status-pages
  api /api/v2/status-pages/0/details
  api /api/v3/status-pages
  ui /maintenance-windows
  api /api/v3/maintenance-windows
  ui /integrations
  ui /settings
  ui /api/v2/monitors/export/yaml
  request 202 "/api/v2/push-monitors/heartbeats/$PUSH_SECRET" -X POST
}

curl -fsS -o /dev/null -c "$COOKIES" -d "username=training&password=training-password" "$BASE_URL/auth/login" \
  || fail "login"
http_id=$(monitor_id http)
tcp_id=$(monitor_id tcp)
push_id=$(monitor_id push)
dns_id=$(monitor_id dns)
round=0
while [ "$round" -lt "$ROUNDS" ] || [ $(($(date +%s) - healthy)) -lt "$SECONDS_TOTAL" ]; do
  pass
  round=$((round + 1))
done
echo "Sent $round rounds of requests, $(($(date +%s) - started))s after the start"

kill -TERM "$KUVASZ_PID"
wait "$KUVASZ_PID" || true
su postgres -s /bin/sh -c "pg_ctl -D $PGDATA -m fast -w stop >/dev/null"
[ -s "$CACHE" ] || fail "the AOT cache wasn't written"
echo "AOT cache written in $(($(date +%s) - started))s: $(du -h "$CACHE" | cut -f1)"

# Fails the build if Java can't use the cache, with the GC of small machines (Serial, below 2 CPUs or 1792 MB of memory)
# and with the one of bigger ones (G1). Without -XX:AOTMode=on, Java would only print a warning and start without it.
classpath=$(sed -n 's/^-cp //p' /home/app/jvm-args)
for gc in -XX:+UseSerialGC -XX:+UseG1GC; do
  if ! output=$(java "$gc" -XX:AOTMode=on -XX:AOTCache="$CACHE" -cp "$classpath" -version 2>&1); then
    echo "$output" >&2
    fail "Java can't use the AOT cache with $gc"
  fi
done
echo "Java can use the AOT cache with the Serial and the G1 GC"
