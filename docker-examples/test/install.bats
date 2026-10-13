#!/usr/bin/env bats
#
# Tests of install.sh. The answers are fed through KUVASZ_INSTALL_TTY (one line per prompt), docker is replaced by a
# stub that records its calls, and the real docker is only used to validate the generated compose files.
#
# Run with: bats docker-examples/test/install.bats

setup() {
  REPO_DIR="$(cd "${BATS_TEST_DIRNAME}/../.." && pwd)"
  SCRIPT="${REPO_DIR}/docker-examples/install.sh"
  REAL_DOCKER="$(command -v docker)"
  WORK_DIR="${BATS_TEST_TMPDIR}"
  TARGET="${WORK_DIR}/kuvasz"

  mkdir -p "${WORK_DIR}/bin"
  cat >"${WORK_DIR}/bin/docker" <<'EOF'
#!/usr/bin/env bash
echo "$*" >>"${DOCKER_STUB_LOG}"
if [ -n "${DOCKER_STUB_FAIL:-}" ] && [[ "$*" == "${DOCKER_STUB_FAIL}"* ]]; then
  exit 1
fi
case "$*" in
  "volume inspect "*) [[ " ${DOCKER_STUB_VOLUMES:-} " == *" $3 "* ]] ;;
  *--format*) echo "${DOCKER_STUB_OS:-Ubuntu 24.04 LTS}" ;;
esac
EOF
  chmod +x "${WORK_DIR}/bin/docker"

  export DOCKER_STUB_LOG="${WORK_DIR}/docker.log"
  export KUVASZ_COMPOSE_URL="file://${REPO_DIR}/docker-examples/docker-compose.yml"
  export KUVASZ_DOCKER_SOCKET="${WORK_DIR}/docker.sock"
  cd "$WORK_DIR"
}

install_with() {
  printf '%s\n' "$@" >"${WORK_DIR}/answers"
  run env PATH="${WORK_DIR}/bin:${PATH}" KUVASZ_INSTALL_TTY="${WORK_DIR}/answers" bash "$SCRIPT"
}

compose_json() {
  "$REAL_DOCKER" compose -f "${TARGET}/docker-compose.yml" config --format json
}

# Usage: compose_value <jq filter>, the config is rendered only once per test
compose_value() {
  local json="${WORK_DIR}/compose.json"
  if [ ! -s "$json" ]; then
    compose_json >"$json" || return 1
  fi
  jq -r "$1" "$json"
}

file_mode() {
  stat -c '%a' "$1" 2>/dev/null || stat -f '%Lp' "$1"
}

assert_started() {
  grep -qx "compose up -d" "$DOCKER_STUB_LOG"
}

refute_started() {
  refute_file_contains "$DOCKER_STUB_LOG" "compose up"
}

# Assertions are functions, because a failing [[ ]] or "! command" in the middle of a test doesn't fail it on bash 3.2

assert_output_contains() {
  if [[ "$output" != *"$1"* ]]; then
    printf 'Expected the output to contain: %s\n--- output ---\n%s\n' "$1" "$output" >&2
    return 1
  fi
}

refute_output_contains() {
  if [[ "$output" == *"$1"* ]]; then
    printf 'Expected the output not to contain: %s\n--- output ---\n%s\n' "$1" "$output" >&2
    return 1
  fi
}

assert_matches() {
  if [[ ! "$1" =~ $2 ]]; then
    printf 'Expected "%s" to match %s\n' "$1" "$2" >&2
    return 1
  fi
}

refute_file_contains() {
  if [ -e "$1" ] && grep -q "$2" "$1"; then
    printf 'Expected %s not to contain: %s\n' "$1" "$2" >&2
    return 1
  fi
}

@test "installs with the default answers: bundled database, built-in authentication" {
  local answers=(
    "$TARGET"          # directory
    ""                 # own PostgreSQL -> no
    ""                 # DB user -> kuvasz
    ""                 # DB password -> generated
    ""                 # timezone -> UTC
    ""                 # version -> latest
    ""                 # port -> 8080
    ""                 # auth mode -> built-in
    "admin"            # admin username
    "AdminPassword123" # admin password
    "AdminPassword123" # repeated
    ""                 # API key -> no
    ""                 # MCP server -> no
    ""                 # non-root -> no
    ""                 # Docker socket -> no
    ""                 # proceed -> yes
  )
  install_with "${answers[@]}"

  [ "$status" -eq 0 ]
  assert_started
  refute_output_contains "volume of a previous installation"
  [ "$(compose_value '.services["kuvasz-db"].environment.POSTGRES_USER')" = "kuvasz" ]
  [ "$(compose_value '.services["kuvasz-db"].environment.TZ')" = "UTC" ]
  [ "$(compose_value '.services["kuvasz-db"].healthcheck.test | join(" ")')" = "CMD pg_isready -U kuvasz" ]
  [ "$(compose_value '.services.kuvasz.image')" = "kuvaszmonitoring/kuvasz:latest" ]
  [ "$(compose_value '.services.kuvasz.ports[0].published')" = "8080" ]
  [ "$(compose_value '.services.kuvasz.environment.TZ')" = "UTC" ]
  [ "$(compose_value '.services.kuvasz.environment.DATABASE_HOST')" = "kuvasz-db" ]
  [ "$(compose_value '.services.kuvasz.environment.DATABASE_USER')" = "kuvasz" ]
  [ "$(compose_value '.services.kuvasz.environment.ADMIN_USER')" = "admin" ]
  [ "$(compose_value '.services.kuvasz.environment.ADMIN_PASSWORD')" = "AdminPassword123" ]
  [ "$(compose_value '.services.kuvasz.environment | has("ADMIN_API_KEY")')" = "false" ]
  [ "$(compose_value '.services.kuvasz.environment | has("ADMIN_MCP_API_KEY") or has("ENABLE_MCP_SERVER")')" = "false" ]
  [ "$(compose_value '.services.kuvasz.environment | has("DATABASE_PORT")')" = "false" ]
  [ "$(compose_value '.services.kuvasz.environment | has("DATABASE_NAME")')" = "false" ]
  [ "$(compose_value '.services.kuvasz.depends_on["kuvasz-db"].condition')" = "service_healthy" ]
  [ "$(compose_value '.volumes | has("kuvasz-db-data")')" = "true" ]
  [ "$(compose_value '.services.kuvasz | has("user") or has("cap_drop") or has("group_add")')" = "false" ]
  [ "$(compose_value '.services.kuvasz.volumes | length')" = "1" ]

  local db_password
  db_password="$(compose_value '.services.kuvasz.environment.DATABASE_PASSWORD')"
  assert_matches "$db_password" '^[0-9a-f]{32}$'
  [ "$(compose_value '.services["kuvasz-db"].environment.POSTGRES_PASSWORD')" = "$db_password" ]
  assert_output_contains "Database password of Kuvasz: ${db_password}"

  [ "$(file_mode "${TARGET}/docker-compose.yml")" = "600" ]
  [ -f "${TARGET}/kuvasz.yml" ]
  [ ! -s "${TARGET}/kuvasz.yml" ]
  assert_output_contains "http://localhost:8080"
  assert_output_contains "Compose file:       ${TARGET}/docker-compose.yml"
  assert_output_contains "Configuration file: ${TARGET}/kuvasz.yml"
}

@test "applies custom values and escapes the secrets for YAML and Compose" {
  local answers=(
    "$TARGET"
    "n"                         # own PostgreSQL -> no
    "kuvasz_user"               # DB user
    "it's \$ecret"              # DB password
    "it's \$ecret"              # repeated
    "Europe/Budapest"           # timezone
    "4.5.0"                     # version
    "9090"                      # port
    "1"                         # auth mode -> built-in
    "ad'min"                    # admin username
    "\$uper'Secret#Password"    # admin password
    "\$uper'Secret#Password"    # repeated
    "n"                         # API key -> no
    "n"                         # MCP server -> no
    "n"                         # non-root -> no
    "n"                         # Docker socket -> no
    "y"                         # proceed
  )
  install_with "${answers[@]}"

  [ "$status" -eq 0 ]
  assert_started
  [ "$(compose_value '.services["kuvasz-db"].environment.POSTGRES_USER')" = "kuvasz_user" ]
  [ "$(compose_value '.services["kuvasz-db"].healthcheck.test | join(" ")')" = "CMD pg_isready -U kuvasz_user" ]
  [ "$(compose_value '.services["kuvasz-db"].environment.TZ')" = "Europe/Budapest" ]
  [ "$(compose_value '.services.kuvasz.environment.TZ')" = "Europe/Budapest" ]
  [ "$(compose_value '.services.kuvasz.image')" = "kuvaszmonitoring/kuvasz:4.5.0" ]
  [ "$(compose_value '.services.kuvasz.ports[0].published')" = "9090" ]
  [ "$(compose_value '.services.kuvasz.ports[0].target')" = "8080" ]
  [ "$(compose_value '.services.kuvasz.environment.ADMIN_USER')" = "ad'min" ]
  # "config" prints the resolved model as a compose file, so a literal "$" shows up re-escaped as "$$" (an unescaped
  # "$ecret" would have been interpolated to an empty string instead)
  [ "$(compose_value '.services["kuvasz-db"].environment.POSTGRES_PASSWORD')" = "it's \$\$ecret" ]
  [ "$(compose_value '.services.kuvasz.environment.DATABASE_PASSWORD')" = "it's \$\$ecret" ]
  [ "$(compose_value '.services.kuvasz.environment.ADMIN_PASSWORD')" = "\$\$uper'Secret#Password" ]
  refute_output_contains "Generated secrets"
  assert_output_contains "http://localhost:9090"
}

@test "uses an own PostgreSQL instance without the bundled database" {
  local answers=(
    "$TARGET"
    "y"               # own PostgreSQL
    "db.example.com"  # host
    "5433"            # port
    "kuvasz_db"       # database name
    "kuvasz.user"     # DB user
    ""                # DB password -> can't be generated, asked again
    "DbPassword"      # DB password
    "DbPassword"      # repeated
    "Europe/Berlin"   # timezone
    ""                # version
    ""                # port
    ""                # auth mode
    "admin"
    "AdminPassword123"
    "AdminPassword123"
    "" ""             # no API keys
    "" ""             # root, no socket
    ""                # proceed
  )
  install_with "${answers[@]}"

  [ "$status" -eq 0 ]
  assert_started
  assert_output_contains "A value is required"
  [ "$(compose_value '.services | keys | join(",")')" = "kuvasz" ]
  [ "$(compose_value '.services.kuvasz | has("depends_on")')" = "false" ]
  [ "$(compose_value 'has("volumes")')" = "false" ]
  [ "$(compose_value '.services.kuvasz.environment.DATABASE_HOST')" = "db.example.com" ]
  [ "$(compose_value '.services.kuvasz.environment.DATABASE_PORT')" = "5433" ]
  [ "$(compose_value '.services.kuvasz.environment.DATABASE_NAME')" = "kuvasz_db" ]
  [ "$(compose_value '.services.kuvasz.environment.DATABASE_USER')" = "kuvasz.user" ]
  [ "$(compose_value '.services.kuvasz.environment.DATABASE_PASSWORD')" = "DbPassword" ]
  [ "$(compose_value '.services.kuvasz.environment.TZ')" = "Europe/Berlin" ]
  [ "$(compose_value '.services.kuvasz | has("extra_hosts")')" = "false" ]
  refute_file_contains "${TARGET}/docker-compose.yml" "kuvasz-db"
  assert_output_contains "own PostgreSQL at db.example.com:5433/kuvasz_db"
}

@test "replaces localhost with host.docker.internal for an own PostgreSQL on the same machine" {
  local answers=(
    "$TARGET"
    "y"            # own PostgreSQL
    "localhost"    # host
    ""             # use host.docker.internal -> yes
    ""             # port -> 5432
    ""             # database name -> postgres
    ""             # DB user -> kuvasz
    "DbPassword"
    "DbPassword"
    "" "" ""       # timezone, version, port
    "3" ""   # auth mode -> disabled, MCP server -> no
    "" ""          # root, no socket
    ""             # proceed
  )
  install_with "${answers[@]}"

  [ "$status" -eq 0 ]
  assert_output_contains "would point to the Kuvasz container itself"
  [ "$(compose_value '.services.kuvasz.environment.DATABASE_HOST')" = "host.docker.internal" ]
  [ "$(compose_value '.services.kuvasz.extra_hosts | join(",")')" = "host.docker.internal=host-gateway" ]
  [ "$(compose_value '.services.kuvasz.environment | has("DATABASE_PORT")')" = "false" ]
  [ "$(compose_value '.services.kuvasz.environment | has("DATABASE_NAME")')" = "false" ]
}

@test "keeps a loopback database host if the operator insists" {
  local answers=(
    "$TARGET"
    "y" "127.0.1.1"
    "n"            # use host.docker.internal -> no
    "" "" ""
    "DbPassword"
    "DbPassword"
    "" "" ""
    "3" ""   # auth mode -> disabled, MCP server -> no
    "" ""
    ""
  )
  install_with "${answers[@]}"

  [ "$status" -eq 0 ]
  assert_output_contains "would point to the Kuvasz container itself"
  [ "$(compose_value '.services.kuvasz.environment.DATABASE_HOST')" = "127.0.1.1" ]
  [ "$(compose_value '.services.kuvasz | has("extra_hosts")')" = "false" ]
}

@test "configures OIDC authentication with API keys" {
  local answers=(
    "$TARGET"
    "" "" ""                        # bundled DB, generated password
    "" "" ""
    "2"                             # auth mode -> OIDC
    "auth.example.com"              # issuer without scheme -> asked again
    "https://auth.example.com/realms/kuvasz"
    "kuvasz-client"
    "OidcClientSecret"
    "OidcClientSecret"
    "alice"                         # not an email address -> asked again
    " alice@example.com, bob@example.com "
    "y"                             # API key
    "TooShortKey"                   # shorter than 16 characters -> asked again
    "ThisIsAVeryLongApiKey"
    "ThisIsAVeryLongApiKey"
    "y"                             # MCP server
    ""                              # MCP API key -> generated
    "" ""
    ""
  )
  install_with "${answers[@]}"

  [ "$status" -eq 0 ]
  assert_output_contains "It must be an http(s):// URL"
  assert_output_contains "It must be at least 16 characters long"
  [ "$(compose_value '.services.kuvasz.environment.ENABLE_OIDC')" = "true" ]
  [ "$(compose_value '.services.kuvasz.environment.OIDC_ISSUER')" = "https://auth.example.com/realms/kuvasz" ]
  [ "$(compose_value '.services.kuvasz.environment.OIDC_CLIENT_ID')" = "kuvasz-client" ]
  [ "$(compose_value '.services.kuvasz.environment.OIDC_CLIENT_SECRET')" = "OidcClientSecret" ]
  [ "$(compose_value '.services.kuvasz.environment.OIDC_ALLOWED_EMAILS')" = "alice@example.com,bob@example.com" ]
  assert_output_contains "Use comma-separated email addresses"
  assert_output_contains "Allowed emails:     alice@example.com,bob@example.com"
  refute_output_contains "Everybody your OIDC provider can authenticate"
  [ "$(compose_value '.services.kuvasz.environment.ADMIN_API_KEY')" = "ThisIsAVeryLongApiKey" ]
  [ "$(compose_value '.services.kuvasz.environment | has("ADMIN_USER") or has("ADMIN_PASSWORD")')" = "false" ]

  [ "$(compose_value '.services.kuvasz.environment.ENABLE_MCP_SERVER')" = "true" ]
  local mcp_key
  mcp_key="$(compose_value '.services.kuvasz.environment.ADMIN_MCP_API_KEY')"
  assert_matches "$mcp_key" '^[0-9a-f]{32}$'
  assert_output_contains "MCP API key: ${mcp_key}"
  assert_output_contains "MCP server:         enabled, API key: ********"
  assert_output_contains "#oidc-authentication-optional"
  assert_output_contains "/features/mcp-server/#connecting-ai-clients"
}

@test "warns about an OIDC setup without an email allowlist" {
  local answers=(
    "$TARGET"
    "" "" ""
    "" "" ""
    "2" "https://auth.example.com" "kuvasz-client" "OidcClientSecret" "OidcClientSecret"
    ""       # allowed emails -> everyone
    "" ""    # no API key, no MCP server
    "" ""
    ""
  )
  install_with "${answers[@]}"

  [ "$status" -eq 0 ]
  assert_output_contains "Everybody your OIDC provider can authenticate will get full access to Kuvasz"
  assert_output_contains "Allowed emails:     everyone"
  [ "$(compose_value '.services.kuvasz.environment | has("OIDC_ALLOWED_EMAILS")')" = "false" ]
}

@test "disables authentication without asking for credentials or API keys" {
  local answers=(
    "$TARGET"
    "" "" ""
    "" "" ""
    "3" ""   # auth mode -> disabled, MCP server -> no
    "" ""    # root, no socket
    ""
  )
  install_with "${answers[@]}"

  [ "$status" -eq 0 ]
  refute_output_contains "API key"
  [ "$(compose_value '.services.kuvasz.environment.ENABLE_AUTH')" = "false" ]
  [ "$(compose_value '.services.kuvasz.environment | has("ADMIN_USER") or has("ADMIN_PASSWORD")')" = "false" ]
  [ "$(compose_value '.services.kuvasz.environment | has("ENABLE_MCP_SERVER")')" = "false" ]
  assert_output_contains "Authentication:     disabled"
  assert_output_contains "MCP server:         disabled"
}

@test "enables the MCP server without an API key when the authentication is disabled" {
  local answers=(
    "$TARGET"
    "" "" ""
    "" "" ""
    "3"      # auth mode -> disabled
    "y"      # MCP server, no API key question
    "" ""
    ""
  )
  install_with "${answers[@]}"

  [ "$status" -eq 0 ]
  refute_output_contains "MCP API key"
  [ "$(compose_value '.services.kuvasz.environment.ENABLE_MCP_SERVER')" = "true" ]
  [ "$(compose_value '.services.kuvasz.environment | has("ADMIN_MCP_API_KEY")')" = "false" ]
  assert_output_contains "MCP server:         enabled"$'\n'
}

@test "asks again for invalid answers" {
  local answers=(
    "$TARGET"
    "maybe" ""                       # invalid yes/no answer
    "1kuvasz" "kuvasz-db" "kuvasz"   # invalid DB users for the bundled database
    "DbPassword" "Different"         # mismatched repetition
    "DbPassword" "DbPassword"
    "Mars/Olympus_Mons" "../etc" "Asia/Tokyo"
    "4.5.0 beta" "-latest" "latest"
    "0" "65536" "http" "8081"
    "4" "1"
    "administrator1"
    "short"                          # shorter than 12 characters
    "ADMINISTRATOR1"                 # equal to the username, ignoring the case
    "            "                   # blank
    "AdminPassword123"
    "AdminPassword123"
    "" ""
    "y"
    "1000" "user:group" "1001:1001"
    ""
    ""
  )
  install_with "${answers[@]}"

  [ "$status" -eq 0 ]
  assert_output_contains "Please answer y or n"
  assert_output_contains "Use only letters, digits and underscores"
  assert_output_contains "The values don't match"
  assert_output_contains "Unknown timezone"
  assert_output_contains "It must be a valid image tag"
  assert_output_contains "It must be a number between 1 and 65535"
  assert_output_contains "Choose 1, 2 or 3"
  assert_output_contains "It must be at least 12 characters long"
  assert_output_contains "It must not be equal to the username"
  assert_output_contains "It must not be blank"
  assert_output_contains "Use the UID:GID format"
  [ "$(compose_value '.services.kuvasz.environment.DATABASE_PASSWORD')" = "DbPassword" ]
  [ "$(compose_value '.services.kuvasz.environment.TZ')" = "Asia/Tokyo" ]
  [ "$(compose_value '.services.kuvasz.image')" = "kuvaszmonitoring/kuvasz:latest" ]
  [ "$(compose_value '.services.kuvasz.ports[0].published')" = "8081" ]
  [ "$(compose_value '.services.kuvasz.user')" = "1001:1001" ]
}

@test "runs as non-root with the Docker socket, using the detected group of the socket" {
  touch "$KUVASZ_DOCKER_SOCKET"
  local answers=(
    "$TARGET"
    "" "" ""
    "" "" ""
    "3" ""   # auth mode -> disabled, MCP server -> no
    "y" ""   # non-root -> 1000:1000
    "y"      # Docker socket
    ""       # GID -> detected
    ""
  )
  install_with "${answers[@]}"

  [ "$status" -eq 0 ]
  [ "$(compose_value '.services.kuvasz.user')" = "1000:1000" ]
  [ "$(compose_value '.services.kuvasz.cap_drop | join(",")')" = "ALL" ]
  [ "$(compose_value '.services.kuvasz.security_opt | join(",")')" = "no-new-privileges:true" ]
  [ "$(compose_value '.services.kuvasz.group_add | join(",")')" = "$(id -g)" ]
  [ "$(compose_value '.services.kuvasz.volumes[1].source')" = "/var/run/docker.sock" ]
  [ "$(compose_value '.services.kuvasz.volumes[1].target')" = "/var/run/docker.sock" ]
  refute_output_contains "doesn't exist on this machine"
  grep -q 'url: "unix:///var/run/docker.sock"' "${TARGET}/kuvasz.yml"
  [ "$(file_mode "${TARGET}/kuvasz.yml")" = "644" ]
  assert_output_contains "/management/docker-monitors/"
}

@test "suggests the root group for the Docker socket on Docker Desktop" {
  touch "$KUVASZ_DOCKER_SOCKET"
  export DOCKER_STUB_OS="Docker Desktop"
  local answers=(
    "$TARGET"
    "" "" ""
    "" "" ""
    "3" ""   # auth mode -> disabled, MCP server -> no
    "y" "1234:1234"
    "y"
    ""        # GID -> 0
    ""
  )
  install_with "${answers[@]}"

  [ "$status" -eq 0 ]
  [ "$(compose_value '.services.kuvasz.user')" = "1234:1234" ]
  [ "$(compose_value '.services.kuvasz.group_add | join(",")')" = "0" ]
}

@test "mounts the Docker socket without group_add when running as root" {
  local answers=(
    "$TARGET"
    "" "" ""
    "" "" ""
    "3" ""   # auth mode -> disabled, MCP server -> no
    "n"
    "y"       # Docker socket, no GID question
    ""
  )
  install_with "${answers[@]}"

  [ "$status" -eq 0 ]
  assert_output_contains "doesn't exist on this machine"
  assert_output_contains "rootless Docker and Podman use another socket"
  refute_output_contains "Group ID owning the Docker socket"
  [ "$(compose_value '.services.kuvasz.volumes[1].source')" = "/var/run/docker.sock" ]
  [ "$(compose_value '.services.kuvasz | has("group_add") or has("user")')" = "false" ]
  grep -q "docker-hosts:" "${TARGET}/kuvasz.yml"
}

@test "ignores the KI_* variables inherited from the environment" {
  export KI_USER='    user: "6666:6666"'
  export KI_ADMIN_USER='      ADMIN_USER: hijacked'
  local answers=(
    "$TARGET"
    "" "" ""
    "" "" ""
    "" "admin" "AdminPassword123" "AdminPassword123"
    "" ""
    "" ""
    ""
  )
  install_with "${answers[@]}"

  [ "$status" -eq 0 ]
  [ "$(compose_value '.services.kuvasz | has("user")')" = "false" ]
  [ "$(compose_value '.services.kuvasz.environment.ADMIN_USER')" = "admin" ]
}

@test "refuses to overwrite an existing compose file" {
  mkdir -p "$TARGET"
  echo "services: {}" >"${TARGET}/compose.yaml"
  install_with "$TARGET"

  [ "$status" -eq 1 ]
  assert_output_contains "${TARGET}/compose.yaml already exists"
  refute_output_contains "Database"
  [ ! -e "${TARGET}/docker-compose.yml" ]
  [ "$(cat "${TARGET}/compose.yaml")" = "services: {}" ]
  refute_started
}

@test "keeps an existing kuvasz.yml untouched" {
  mkdir -p "$TARGET"
  printf 'app:\n  language: hu\n' >"${TARGET}/kuvasz.yml"
  chmod 600 "${TARGET}/kuvasz.yml"
  local answers=(
    "$TARGET"
    "" "" ""
    "" "" ""
    "3" ""   # auth mode -> disabled, MCP server -> no
    "y" ""
    "y" "999"
    ""
  )
  install_with "${answers[@]}"

  [ "$status" -eq 0 ]
  [ "$(cat "${TARGET}/kuvasz.yml")" = "$(printf 'app:\n  language: hu\n')" ]
  [ "$(file_mode "${TARGET}/kuvasz.yml")" = "600" ]
  assert_output_contains "Keeping the existing kuvasz.yml untouched"
  assert_output_contains "Add the local Docker host to your kuvasz.yml"
  assert_output_contains "readable by the user 1000:1000"
  assert_started
}

@test "refuses a target that is not a directory" {
  touch "$TARGET"
  install_with "$TARGET"

  [ "$status" -eq 1 ]
  assert_output_contains "${TARGET} already exists, but it's not a directory"
}

@test "warns when the database volume of a previous installation exists" {
  export DOCKER_STUB_VOLUMES="other_kuvasz-db-data mykuvasz-2_kuvasz-db-data"
  local answers=(
    "${WORK_DIR}/-My.Kuvasz-2"   # compose normalizes it to "mykuvasz-2"
    "" "" ""
    "" "" ""
    "3" ""
    "" ""
    ""
  )
  install_with "${answers[@]}"

  [ "$status" -eq 0 ]
  assert_output_contains "The mykuvasz-2_kuvasz-db-data volume of a previous installation already exists"
  assert_output_contains "docker volume rm mykuvasz-2_kuvasz-db-data"
}

@test "uses COMPOSE_PROJECT_NAME to find the database volume of a previous installation" {
  export COMPOSE_PROJECT_NAME="custom"
  export DOCKER_STUB_VOLUMES="custom_kuvasz-db-data"
  local answers=(
    "$TARGET"
    "" "" ""
    "" "" ""
    "3" ""
    "" ""
    ""
  )
  install_with "${answers[@]}"

  [ "$status" -eq 0 ]
  assert_output_contains "The custom_kuvasz-db-data volume of a previous installation already exists"
}

@test "doesn't warn about the volume of the bundled database when using an own one" {
  export DOCKER_STUB_VOLUMES="kuvasz_kuvasz-db-data"
  local answers=(
    "$TARGET"
    "y" "db.example.com" "" "" "" "DbPassword" "DbPassword"
    "" "" ""
    "3" ""
    "" ""
    ""
  )
  install_with "${answers[@]}"

  [ "$status" -eq 0 ]
  refute_output_contains "volume of a previous installation"
}

@test "creates the target directory, expanding the tilde" {
  export HOME="${WORK_DIR}/home"
  local answers=(
    "~/apps/kuvasz"
    "" "" ""
    "" "" ""
    "3" ""   # auth mode -> disabled, MCP server -> no
    "" ""
    ""
  )
  install_with "${answers[@]}"

  [ "$status" -eq 0 ]
  [ -f "${HOME}/apps/kuvasz/docker-compose.yml" ]
  [ -f "${HOME}/apps/kuvasz/kuvasz.yml" ]
}

@test "installs into the current directory by default" {
  local answers=(
    ""
    "" "" ""
    "" "" ""
    "3" ""   # auth mode -> disabled, MCP server -> no
    "" ""
    ""
  )
  install_with "${answers[@]}"

  [ "$status" -eq 0 ]
  [ -f "${WORK_DIR}/docker-compose.yml" ]
  [ -f "${WORK_DIR}/kuvasz.yml" ]
}

@test "writes nothing when the installation is not confirmed" {
  local answers=(
    "$TARGET"
    "" "" ""
    "" "" ""
    "3" ""   # auth mode -> disabled, MCP server -> no
    "" ""
    "n"
  )
  install_with "${answers[@]}"

  [ "$status" -eq 0 ]
  assert_output_contains "Aborted, nothing has been written."
  [ ! -e "$TARGET" ]
  refute_started
}

@test "fails when the downloaded compose file has an unexpected format" {
  grep -v '#   - "999"' "${REPO_DIR}/docker-examples/docker-compose.yml" >"${WORK_DIR}/changed-compose.yml"
  export KUVASZ_COMPOSE_URL="file://${WORK_DIR}/changed-compose.yml"
  local answers=(
    "$TARGET"
    "" "" ""
    "" "" ""
    "3" ""   # auth mode -> disabled, MCP server -> no
    "" ""
    ""
  )
  install_with "${answers[@]}"

  [ "$status" -eq 1 ]
  assert_output_contains "Missing or duplicated lines: GROUP_ADD_ID"
  assert_output_contains "has an unexpected format"
  [ ! -e "$TARGET" ]
  refute_started
}

@test "fails when the compose file can't be downloaded" {
  export KUVASZ_COMPOSE_URL="file://${WORK_DIR}/missing.yml"
  local answers=(
    "$TARGET"
    "" "" ""
    "" "" ""
    "3" ""   # auth mode -> disabled, MCP server -> no
    "" ""
    ""
  )
  install_with "${answers[@]}"

  [ "$status" -eq 1 ]
  assert_output_contains "Couldn't download the docker-compose.yml"
  [ ! -e "$TARGET" ]
  refute_started
}

@test "reports when the stack can't be started" {
  export DOCKER_STUB_FAIL="compose up"
  local answers=(
    "$TARGET"
    "" "" ""
    "" "" ""
    "3" ""   # auth mode -> disabled, MCP server -> no
    "" ""
    ""
  )
  install_with "${answers[@]}"

  [ "$status" -eq 1 ]
  assert_output_contains "Couldn't start the stack"
  assert_output_contains "${TARGET}/docker-compose.yml"
  [ -f "${TARGET}/docker-compose.yml" ]
}

@test "fails early when the Docker daemon can't be reached" {
  export DOCKER_STUB_FAIL="info"
  install_with "$TARGET"

  [ "$status" -eq 1 ]
  assert_output_contains "The Docker daemon can't be reached"
  [ ! -e "$TARGET" ]
}

@test "fails early when Docker Compose is missing" {
  export DOCKER_STUB_FAIL="compose version"
  install_with "$TARGET"

  [ "$status" -eq 1 ]
  assert_output_contains "Docker Compose (v2) is not installed"
}

@test "fails without an interactive terminal" {
  run env PATH="${WORK_DIR}/bin:${PATH}" KUVASZ_INSTALL_TTY="${WORK_DIR}/missing-tty" bash "$SCRIPT"

  [ "$status" -eq 1 ]
  assert_output_contains "This script needs an interactive terminal"
}

@test "fails instead of looping when the input ends" {
  install_with "$TARGET" "maybe"

  [ "$status" -eq 1 ]
  assert_output_contains "Unexpected end of input"
  refute_started
}
