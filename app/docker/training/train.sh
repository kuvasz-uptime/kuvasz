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

ui() { curl -fsS -o /dev/null -b "$COOKIES" "$BASE_URL$1" || fail "GET $1"; }
fragment() { curl -fsS -o /dev/null -b "$COOKIES" -H "HX-Request: true" "$BASE_URL$1" || fail "GET $1 (fragment)"; }
api() { curl -fsS -o /dev/null -H "X-API-KEY: $API_KEY" "$BASE_URL$1" || fail "GET $1 (API)"; }

monitor_id() {
  curl -fsS -H "X-API-KEY: $API_KEY" "$BASE_URL/api/v2/$1-monitors" | grep -o '"id":[0-9]*' | head -n 1 | cut -d: -f2
}

pass() {
  ui /
  fragment /fragments/dashboard
  for type in http tcp push dns; do
    id=$(monitor_id "$type")
    [ -n "$id" ] || fail "no $type monitor found"
    ui "/$type-monitors"
    fragment "/$type-monitors/fragments/list"
    ui "/$type-monitors/$id"
    fragment "/$type-monitors/fragments/details-heading/$id"
    fragment "/incidents/fragments/monitor/$type/$id"
    api "/api/v2/$type-monitors"
    api "/api/v2/$type-monitors/$id"
    api "/api/v2/$type-monitors/stats"
  done
  for type in http tcp dns; do
    ui "/api/internal/monitor-charts/$type/$(monitor_id "$type")?period=P1D"
  done
  fragment "/incidents/fragments/monitor/ssl/$(monitor_id http)"
  fragment "/dns-monitors/fragments/snapshot/$(monitor_id dns)"
  ui /icmp-monitors
  ui /docker-monitors
  ui /incidents
  fragment /incidents/fragments/list
  api /api/v2/incidents
  ui /status-pages
  fragment /status-pages/fragments/list
  ui /status
  ui /status/training
  api /api/v2/status-pages
  api /api/v2/status-pages/0/details
  ui /maintenance-windows
  ui /integrations
  ui /settings
  ui /api/v2/monitors/export/yaml
  curl -fsS -o /dev/null -X POST "$BASE_URL/api/v2/push-monitors/heartbeats/$PUSH_SECRET" || fail "push heartbeat"
}

curl -fsS -o /dev/null -c "$COOKIES" -d "username=training&password=training-password" "$BASE_URL/auth/login" \
  || fail "login"
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
