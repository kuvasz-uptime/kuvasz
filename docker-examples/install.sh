#!/usr/bin/env bash
#
# Interactive installer of Kuvasz (https://kuvasz-uptime.dev) with Docker Compose.
#
# Usage:
#   curl -fsSL https://raw.githubusercontent.com/kuvasz-uptime/kuvasz/refs/heads/main/docker-examples/install.sh | bash
#
# It does the same as the manual Docker Compose guide (https://kuvasz-uptime.dev/setup/installation/), but asks for
# the credentials and settings, and adjusts the downloaded docker-compose.yml accordingly, before starting the stack.
#
# Internal environment variables (used by the tests, you shouldn't need them):
#   KUVASZ_INSTALL_TTY    - where the answers are read from (default: /dev/tty)
#   KUVASZ_COMPOSE_URL    - where the docker-compose.yml is downloaded from
#   KUVASZ_DOCKER_SOCKET  - the Docker socket used to detect its group ID (default: /var/run/docker.sock)

set -euo pipefail

COMPOSE_URL="${KUVASZ_COMPOSE_URL:-https://raw.githubusercontent.com/kuvasz-uptime/kuvasz/refs/heads/main/docker-examples/docker-compose.yml}"
INPUT="${KUVASZ_INSTALL_TTY:-/dev/tty}"
DOCKER_SOCKET="${KUVASZ_DOCKER_SOCKET:-/var/run/docker.sock}"
DOCS_URL="https://kuvasz-uptime.dev"

ADMIN_PASSWORD_MIN_LENGTH=12
API_KEY_MIN_LENGTH=16

LOCAL_DOCKER_HOST_YAML=$'docker-hosts:\n  - name: "local"\n    url: "unix:///var/run/docker.sock"'

DOCKER_OS=""
GENERATED_SECRETS=""
TMP_COMPOSE=""

if [ -t 1 ]; then
  BOLD=$'\033[1m'
  RED=$'\033[31m'
  YELLOW=$'\033[33m'
  GREEN=$'\033[32m'
  RESET=$'\033[0m'
else
  BOLD=""
  RED=""
  YELLOW=""
  GREEN=""
  RESET=""
fi

info() { printf '%s\n' "$*"; }
warn() { printf '%s! %s%s\n' "$YELLOW" "$*" "$RESET"; }
error() { printf '%s✗ %s%s\n' "$RED" "$*" "$RESET"; }
die() {
  printf '%sError: %s%s\n' "$RED" "$*" "$RESET" >&2
  exit 1
}
section() { printf '\n%s== %s ==%s\n' "$BOLD" "$*" "$RESET"; }

cleanup() {
  if [ -n "$TMP_COMPOSE" ]; then
    rm -f "$TMP_COMPOSE" "${TMP_COMPOSE}.new"
  fi
}

# --- Input helpers ---------------------------------------------------------------------------------------------------

trim() {
  local value="$1"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf '%s' "$value"
}

# Reads a line into the variable named $1, a second argument "silent" hides the input
read_line() {
  local line
  if [ "${2:-}" = "silent" ]; then
    IFS= read -r -s -u 3 line || die "Unexpected end of input"
    printf '\n'
  else
    IFS= read -r -u 3 line || die "Unexpected end of input"
  fi
  printf -v "$1" '%s' "$line"
}

# Usage: ask VAR "Question" "default" [validator] ["error message"]
ask() { prompt_for "$1" "$2" "$3" true "${4:-}" "${5:-Invalid value}"; }

# Usage: ask_optional VAR "Question" [validator] ["error message"], accepts an empty answer too
ask_optional() { prompt_for "$1" "$2" "" false "${3:-}" "${4:-Invalid value}"; }

prompt_for() {
  local var="$1" question="$2" default="$3" required="$4" validator="$5" message="$6"
  local answer
  while true; do
    if [ -n "$default" ]; then
      printf '%s [%s]: ' "$question" "$default"
    else
      printf '%s: ' "$question"
    fi
    read_line answer
    answer="$(trim "$answer")"
    if [ -z "$answer" ]; then
      answer="$default"
    fi
    if [ -z "$answer" ] && [ "$required" = "false" ]; then
      break
    fi
    if [ -z "$answer" ]; then
      error "A value is required"
      continue
    fi
    if [ -n "$validator" ] && ! "$validator" "$answer"; then
      error "$message"
      continue
    fi
    break
  done
  printf -v "$var" '%s' "$answer"
}

# Usage: ask_yes_no VAR "Question" y|n, sets VAR to true or false
ask_yes_no() {
  local var="$1" question="$2" default="$3"
  local hint answer
  if [ "$default" = "y" ]; then hint="Y/n"; else hint="y/N"; fi
  while true; do
    printf '%s [%s]: ' "$question" "$hint"
    read_line answer
    answer="$(trim "$answer")"
    case "${answer:-$default}" in
      [yY] | [yY][eE][sS])
        printf -v "$var" '%s' true
        return
        ;;
      [nN] | [nN][oO])
        printf -v "$var" '%s' false
        return
        ;;
      *) error "Please answer y or n" ;;
    esac
  done
}

lowercase() { printf '%s' "$1" | tr '[:upper:]' '[:lower:]'; }

generate_secret() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex 16
  else
    od -An -N16 -tx1 /dev/urandom | tr -d ' \n'
  fi
}

# Usage: ask_secret VAR "Label" min_length can_generate(true|false) [username it must differ from]
# The rules are the same as the ones Kuvasz validates the credentials with at startup
ask_secret() {
  local var="$1" label="$2" min_length="$3" can_generate="$4" must_differ="${5:-}"
  local first second
  while true; do
    if [ "$can_generate" = "true" ]; then
      printf '%s (hidden, leave it empty to generate one): ' "$label"
    else
      printf '%s (hidden): ' "$label"
    fi
    read_line first silent
    if [ -z "$first" ]; then
      if [ "$can_generate" = "true" ]; then
        first="$(generate_secret)"
        GENERATED_SECRETS="${GENERATED_SECRETS}  ${label%%,*}: ${first}"$'\n'
        info "A random value has been generated, it will be shown at the end."
        break
      fi
      error "A value is required"
      continue
    fi
    if [ -z "$(trim "$first")" ]; then
      error "It must not be blank"
      continue
    fi
    if [ "${#first}" -lt "$min_length" ]; then
      error "It must be at least ${min_length} characters long"
      continue
    fi
    if [ -n "$must_differ" ] && [ "$(lowercase "$first")" = "$(lowercase "$must_differ")" ]; then
      error "It must not be equal to the username (not even with a different case)"
      continue
    fi
    printf 'Repeat it: '
    read_line second silent
    if [ "$first" != "$second" ]; then
      error "The values don't match, try again"
      continue
    fi
    break
  done
  printf -v "$var" '%s' "$first"
}

# --- Validators ------------------------------------------------------------------------------------------------------

is_number() { [[ "$1" =~ ^[0-9]+$ ]]; }

is_port() { is_number "$1" && [ "$1" -ge 1 ] && [ "$1" -le 65535 ]; }

is_timezone() {
  [[ "$1" =~ ^[A-Za-z0-9_+-]+(/[A-Za-z0-9_+-]+)*$ ]] || return 1
  if [ -d /usr/share/zoneinfo ]; then
    [ -f "/usr/share/zoneinfo/$1" ]
  fi
}

is_image_tag() { [[ "$1" =~ ^[A-Za-z0-9_][A-Za-z0-9._-]*$ ]]; }

is_pg_identifier() { [[ "$1" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; }

is_without_whitespace() { [[ ! "$1" =~ [[:space:]] ]]; }

is_uid_gid() { [[ "$1" =~ ^[0-9]+:[0-9]+$ ]]; }

is_http_url() { [[ "$1" =~ ^https?://[^[:space:]]+$ ]]; }

is_local_host() {
  case "$1" in
    localhost | localhost.* | 127.* | 0.0.0.0 | ::1 | "[::1]" | ::) return 0 ;;
    *) return 1 ;;
  esac
}

is_email_list() {
  local email emails
  IFS=',' read -r -a emails <<<"${1//[[:space:]]/}"
  [ "${#emails[@]}" -gt 0 ] || return 1
  for email in "${emails[@]}"; do
    [[ "$email" =~ ^[^@]+@[^@]+$ ]] || return 1
  done
}

# --- Compose file generation -----------------------------------------------------------------------------------------

# Single-quoted YAML scalar, with "$" escaped for the variable interpolation of Docker Compose
yaml_quote() {
  # The quote has to come from a variable, bash 3.2 doesn't handle an escaped one in the pattern
  local quote="'" value
  value="${1//$quote/$quote$quote}"
  value="${value//\$/\$\$}"
  printf "'%s'" "$value"
}

env_line() { printf '      %s: %s' "$1" "$(yaml_quote "$2")"; }

detect_socket_gid() {
  case "$DOCKER_OS" in
    *"Docker Desktop"*)
      printf '0'
      return
      ;;
  esac
  if [ -e "$DOCKER_SOCKET" ]; then
    stat -c '%g' "$DOCKER_SOCKET" 2>/dev/null || stat -f '%g' "$DOCKER_SOCKET" 2>/dev/null || true
    return
  fi
  if command -v getent >/dev/null 2>&1; then
    getent group docker 2>/dev/null | cut -d: -f3 || true
  fi
}

# Makes transform_compose replace the line of the anchor ($1) with $2, or drop it if $2 is empty
replace_anchor() { export "KI_$1=$2"; }

# Rewrites the downloaded compose file ($1) into $2. Every line the script relies on is an anchor: if any of them is
# missing (or present more than once), the format of the file has changed and the script refuses to continue.
# The lines of the anchors without a replace_anchor call are kept as they are.
transform_compose() {
  local source="$1" target="$2"
  (
    # Only the values set below may reach awk, not the KI_* variables inherited from the environment
    unset "${!KI_@}"
    # The target contains secrets, so it shouldn't be readable by others, not even for a moment
    umask 077

    export KI_OWN_DB="$OWN_DB"
    replace_anchor IMAGE "    image: kuvaszmonitoring/kuvasz:${KUVASZ_VERSION}"
    replace_anchor PORT "      - \"${KUVASZ_PORT}:8080\""
    replace_anchor APP_TZ "$(env_line TZ "$TIMEZONE")"

    if [ "$OWN_DB" = "false" ]; then
      replace_anchor POSTGRES_USER "$(env_line POSTGRES_USER "$DB_USER")"
      replace_anchor POSTGRES_PASSWORD "$(env_line POSTGRES_PASSWORD "$DB_PASSWORD")"
      replace_anchor DB_TZ "$(env_line TZ "$TIMEZONE")"
      replace_anchor PG_ISREADY "      test: [ \"CMD\", \"pg_isready\", \"-U\", \"${DB_USER}\" ]"
    fi

    local db_lines
    db_lines="$(env_line DATABASE_HOST "$DB_HOST")"
    if [ "$DB_PORT" != "5432" ]; then
      db_lines+=$'\n'"$(env_line DATABASE_PORT "$DB_PORT")"
    fi
    if [ "$DB_NAME" != "postgres" ]; then
      db_lines+=$'\n'"$(env_line DATABASE_NAME "$DB_NAME")"
    fi
    replace_anchor DATABASE_HOST "$db_lines"
    replace_anchor DATABASE_USER "$(env_line DATABASE_USER "$DB_USER")"
    replace_anchor DATABASE_PASSWORD "$(env_line DATABASE_PASSWORD "$DB_PASSWORD")"

    # Every authentication related setting takes the place of the ADMIN_PASSWORD line
    local auth_lines
    case "$AUTH_MODE" in
      builtin)
        replace_anchor ADMIN_USER "$(env_line ADMIN_USER "$ADMIN_USER")"
        auth_lines="$(env_line ADMIN_PASSWORD "$ADMIN_PASSWORD")"
        ;;
      oidc)
        replace_anchor ADMIN_USER ""
        auth_lines="$(env_line ENABLE_OIDC true)"
        auth_lines+=$'\n'"$(env_line OIDC_ISSUER "$OIDC_ISSUER")"
        auth_lines+=$'\n'"$(env_line OIDC_CLIENT_ID "$OIDC_CLIENT_ID")"
        auth_lines+=$'\n'"$(env_line OIDC_CLIENT_SECRET "$OIDC_CLIENT_SECRET")"
        if [ -n "$OIDC_ALLOWED_EMAILS" ]; then
          auth_lines+=$'\n'"$(env_line OIDC_ALLOWED_EMAILS "$OIDC_ALLOWED_EMAILS")"
        fi
        ;;
      disabled)
        replace_anchor ADMIN_USER ""
        auth_lines="$(env_line ENABLE_AUTH false)"
        ;;
    esac
    if [ -n "$API_KEY" ]; then
      auth_lines+=$'\n'"$(env_line ADMIN_API_KEY "$API_KEY")"
    fi
    if [ "$ENABLE_MCP" = "true" ]; then
      auth_lines+=$'\n'"$(env_line ENABLE_MCP_SERVER true)"
    fi
    if [ -n "$MCP_API_KEY" ]; then
      auth_lines+=$'\n'"$(env_line ADMIN_MCP_API_KEY "$MCP_API_KEY")"
    fi
    replace_anchor ADMIN_PASSWORD "$auth_lines"

    if [ "$USE_HOST_GATEWAY" = "true" ]; then
      replace_anchor APP_ENVIRONMENT $'    extra_hosts:\n      - "host.docker.internal:host-gateway"\n    environment:'
    fi

    if [ "$NON_ROOT" = "true" ]; then
      replace_anchor USER "    user: \"${USER_ID}\""
      replace_anchor CAP_DROP "    cap_drop:"
      replace_anchor CAP_DROP_ALL "      - ALL"
      replace_anchor SECURITY_OPT "    security_opt:"
      replace_anchor NO_NEW_PRIVILEGES "      - no-new-privileges:true"
    fi

    if [ "$MOUNT_DOCKER_SOCKET" = "true" ]; then
      replace_anchor DOCKER_SOCKET "      - /var/run/docker.sock:/var/run/docker.sock"
      if [ "$NON_ROOT" = "true" ]; then
        replace_anchor GROUP_ADD "    group_add:"
        replace_anchor GROUP_ADD_ID "      - \"${SOCKET_GID}\""
      fi
    fi

    awk '
      function anchor(section_name, key, regex) {
        anchors++
        anchor_section[anchors] = section_name
        anchor_key[anchors] = key
        anchor_regex[anchors] = regex
      }
      function find_anchor(section_name,    i) {
        for (i = 1; i <= anchors; i++) {
          if (anchor_section[i] == section_name && $0 ~ anchor_regex[i]) return anchor_key[i]
        }
        return ""
      }
      BEGIN {
        own_db = ENVIRON["KI_OWN_DB"] == "true"
        anchor("top", "DB_COMMENT_1", "^  # You can omit this service")
        anchor("top", "DB_COMMENT_2", "^  # but in this case make sure to adjust the connection details")
        anchor("top", "DB_SERVICE", "^  kuvasz-db:$")
        anchor("top", "APP_SERVICE", "^  kuvasz:$")
        anchor("top", "VOLUMES", "^volumes:$")
        anchor("db", "POSTGRES_USER", "^      POSTGRES_USER: ")
        anchor("db", "POSTGRES_PASSWORD", "^      POSTGRES_PASSWORD: ")
        anchor("db", "DB_TZ", "^      TZ: ")
        anchor("db", "PG_ISREADY", "^      test: \\[ \"CMD\", \"pg_isready\", \"-U\", \"kuvasz\" \\]$")
        anchor("app", "IMAGE", "^    image: kuvaszmonitoring/kuvasz:")
        anchor("app", "USER", "^    # user: \"1000:1000\"$")
        anchor("app", "CAP_DROP", "^    # cap_drop:$")
        anchor("app", "CAP_DROP_ALL", "^    #   - ALL$")
        anchor("app", "SECURITY_OPT", "^    # security_opt:$")
        anchor("app", "NO_NEW_PRIVILEGES", "^    #   - no-new-privileges:true$")
        anchor("app", "PORT", "^      - \"8080:8080\"$")
        anchor("app", "APP_ENVIRONMENT", "^    environment:$")
        anchor("app", "APP_TZ", "^      TZ: ")
        anchor("app", "DATABASE_HOST", "^      DATABASE_HOST: ")
        anchor("app", "DATABASE_USER", "^      DATABASE_USER: ")
        anchor("app", "DATABASE_PASSWORD", "^      DATABASE_PASSWORD: ")
        anchor("app", "ADMIN_USER", "^      ADMIN_USER: ")
        anchor("app", "ADMIN_PASSWORD", "^      ADMIN_PASSWORD: ")
        anchor("app", "DOCKER_SOCKET", "^      # - /var/run/docker\\.sock:/var/run/docker\\.sock$")
        anchor("app", "GROUP_ADD", "^    # group_add:$")
        anchor("app", "GROUP_ADD_ID", "^    #   - \"999\"$")
        anchor("app", "DEPENDS_ON", "^    depends_on:$")
        anchor("volumes", "DB_VOLUME", "^  kuvasz-db-data:$")
      }
      {
        key = find_anchor("top")
        if (key != "") {
          hit[key]++
          if (key == "DB_SERVICE") section = "db"
          else if (key == "APP_SERVICE") section = "app"
          else if (key == "VOLUMES") section = "volumes"
          if (key == "APP_SERVICE" || !own_db) print
          next
        }
      }
      skipping_depends_on {
        if ($0 ~ /^      /) next
        skipping_depends_on = 0
      }
      {
        key = find_anchor(section)
        if (key != "") hit[key]++
        # Everything that belongs to the bundled database goes away when an own one is used
        if (own_db && (section == "db" || section == "volumes" || key == "DEPENDS_ON")) {
          if (key == "DEPENDS_ON") skipping_depends_on = 1
          next
        }
        if (key == "" || !(("KI_" key) in ENVIRON)) print
        else if (ENVIRON["KI_" key] != "") print ENVIRON["KI_" key]
      }
      END {
        for (i = 1; i <= anchors; i++) {
          if (hit[anchor_key[i]] != 1) missing = missing " " anchor_key[i]
        }
        if (missing != "") {
          print "Missing or duplicated lines:" missing > "/dev/stderr"
          exit 1
        }
      }
    ' "$source" >"$target"
  ) || die "The downloaded docker-compose.yml has an unexpected format, please follow the manual guide: ${DOCS_URL}/setup/installation/"
}

write_kuvasz_config() {
  if [ -e kuvasz.yml ]; then
    info "Keeping the existing kuvasz.yml untouched."
    if [ "$MOUNT_DOCKER_SOCKET" = "true" ]; then
      warn "Add the local Docker host to your kuvasz.yml to monitor the containers of this machine:"
      printf '%s\n' "$LOCAL_DOCKER_HOST_YAML" | sed 's/^/    /'
    fi
    if [ "$NON_ROOT" = "true" ]; then
      warn "Make sure that kuvasz.yml is readable by the user ${USER_ID} (e.g. chmod 644 kuvasz.yml)."
    fi
    return
  fi
  if [ "$MOUNT_DOCKER_SOCKET" = "true" ]; then
    printf '%s\n' "$LOCAL_DOCKER_HOST_YAML" >kuvasz.yml
  else
    : >kuvasz.yml
  fi
  if [ "$NON_ROOT" = "true" ]; then
    chmod 644 kuvasz.yml
  fi
}

# --- Steps -----------------------------------------------------------------------------------------------------------

preflight() {
  command -v curl >/dev/null 2>&1 || die "curl is not installed"
  command -v docker >/dev/null 2>&1 || die "Docker is not installed, see https://docs.docker.com/engine/install/"
  docker compose version >/dev/null 2>&1 || die "Docker Compose (v2) is not installed, see https://docs.docker.com/compose/install/"
  DOCKER_OS="$(docker info --format '{{.OperatingSystem}}' 2>/dev/null)" ||
    die "The Docker daemon can't be reached. Is it running, and is your user allowed to use it (e.g. a member of the docker group)?"
}

# Only resolves the directory, it's created right before writing the files (so declining the summary leaves no trace)
choose_target_dir() {
  local dir name
  ask dir "Directory of the docker-compose.yml and kuvasz.yml files" "."
  case "$dir" in
    \~) dir="$HOME" ;;
    \~/*) dir="${HOME}/${dir#\~/}" ;;
  esac
  if [ -d "$dir" ]; then
    TARGET_DIR="$(cd "$dir" && pwd)" || die "Couldn't enter the directory: $dir"
  elif [ -e "$dir" ]; then
    die "$dir already exists, but it's not a directory"
  else
    case "$dir" in
      /*) TARGET_DIR="$dir" ;;
      *) TARGET_DIR="${PWD}/${dir#./}" ;;
    esac
  fi
  for name in compose.yaml compose.yml docker-compose.yaml docker-compose.yml; do
    if [ -e "${TARGET_DIR}/${name}" ]; then
      die "${TARGET_DIR}/${name} already exists. Remove it, or choose another directory."
    fi
  done
}

# The same name Docker Compose derives from the directory, unless it's overridden
compose_project_name() {
  if [ -n "${COMPOSE_PROJECT_NAME:-}" ]; then
    printf '%s' "$COMPOSE_PROJECT_NAME"
    return
  fi
  basename "$TARGET_DIR" | tr '[:upper:]' '[:lower:]' | tr -cd 'a-z0-9_-' | sed 's/^[-_]*//'
}

ask_database() {
  section "Database"
  ask_yes_no OWN_DB "Do you have your own PostgreSQL instance (otherwise one will be started alongside Kuvasz)?" n
  USE_HOST_GATEWAY=false
  DB_PORT=5432
  DB_NAME=postgres
  if [ "$OWN_DB" = "true" ]; then
    ask DB_HOST "Host of your PostgreSQL" "" is_without_whitespace "The host must not contain whitespace"
    if is_local_host "$DB_HOST"; then
      warn "\"${DB_HOST}\" would point to the Kuvasz container itself, not to this machine."
      ask_yes_no USE_HOST_GATEWAY "Use host.docker.internal instead, to reach the PostgreSQL of this machine?" y
      if [ "$USE_HOST_GATEWAY" = "true" ]; then
        DB_HOST="host.docker.internal"
        info "Make sure that your PostgreSQL listens on an address that is reachable from the containers too."
      fi
    fi
    ask DB_PORT "Port of your PostgreSQL" "5432" is_port "It must be a number between 1 and 65535"
    ask DB_NAME "Name of the database" "postgres" is_without_whitespace "The name must not contain whitespace"
    ask DB_USER "Database user of Kuvasz" "kuvasz" is_without_whitespace "The user must not contain whitespace"
    ask_secret DB_PASSWORD "Database password of Kuvasz" 1 false
  else
    DB_HOST="kuvasz-db"
    # Postgres only applies the user & password when it creates its data directory, a reused volume keeps the old ones
    local volume
    volume="$(compose_project_name)_kuvasz-db-data"
    if docker volume inspect "$volume" >/dev/null 2>&1; then
      warn "The ${volume} volume of a previous installation already exists, so its data will be reused,"
      warn "together with the database user & password it was created with. Enter the same ones (don't generate a"
      warn "new password), or remove the volume first with: docker volume rm ${volume}"
    fi
    ask DB_USER "Database user of Kuvasz" "kuvasz" is_pg_identifier \
      "Use only letters, digits and underscores, and don't start with a digit"
    ask_secret DB_PASSWORD "Database password of Kuvasz" 1 true
  fi
}

ask_general() {
  section "General"
  ask TIMEZONE "Timezone" "UTC" is_timezone "Unknown timezone, use a name like Europe/Budapest"
  ask KUVASZ_VERSION "Kuvasz version" "latest" is_image_tag "It must be a valid image tag, like latest or 4.5.0"
  ask KUVASZ_PORT "Port to expose Kuvasz on" "8080" is_port "It must be a number between 1 and 65535"
}

ask_authentication() {
  section "Authentication"
  local choice
  info "  1) Username & password (built-in login form)"
  info "  2) OIDC provider"
  info "  3) Disabled (no authentication at all)"
  ask choice "Authentication mode" "1" is_auth_choice "Choose 1, 2 or 3"
  ADMIN_USER=""
  ADMIN_PASSWORD=""
  OIDC_ISSUER=""
  OIDC_CLIENT_ID=""
  OIDC_CLIENT_SECRET=""
  OIDC_ALLOWED_EMAILS=""
  API_KEY=""
  MCP_API_KEY=""
  case "$choice" in
    1)
      AUTH_MODE=builtin
      ask ADMIN_USER "Admin username" ""
      ask_secret ADMIN_PASSWORD "Admin password, min. ${ADMIN_PASSWORD_MIN_LENGTH} characters" \
        "$ADMIN_PASSWORD_MIN_LENGTH" true "$ADMIN_USER"
      ;;
    2)
      AUTH_MODE=oidc
      ask OIDC_ISSUER "OIDC issuer URL" "" is_http_url "It must be an http(s):// URL"
      ask OIDC_CLIENT_ID "OIDC client ID" ""
      ask_secret OIDC_CLIENT_SECRET "OIDC client secret" 1 false
      ask_optional OIDC_ALLOWED_EMAILS "Email addresses allowed to sign in, comma-separated (empty: everyone)" \
        is_email_list "Use comma-separated email addresses, like alice@example.com,bob@example.com"
      OIDC_ALLOWED_EMAILS="${OIDC_ALLOWED_EMAILS//[[:space:]]/}"
      if [ -z "$OIDC_ALLOWED_EMAILS" ]; then
        warn "Everybody your OIDC provider can authenticate will get full access to Kuvasz, the REST API included."
        warn "Only do this with a dedicated OAuth client that is restricted to the intended users."
      fi
      ;;
    3)
      AUTH_MODE=disabled
      warn "Everybody who can reach Kuvasz will be able to see and change everything."
      ;;
  esac
  if [ "$AUTH_MODE" != "disabled" ]; then
    local wants_key
    ask_yes_no wants_key "Do you need an API key for the REST API?" n
    if [ "$wants_key" = "true" ]; then
      ask_secret API_KEY "API key, min. ${API_KEY_MIN_LENGTH} characters" "$API_KEY_MIN_LENGTH" true
    fi
  fi
  ask_yes_no ENABLE_MCP "Enable the MCP server, so that AI assistants can use Kuvasz?" n
  # The MCP server has its own API key, it's unusable without one (unless the authentication is disabled)
  if [ "$ENABLE_MCP" = "true" ] && [ "$AUTH_MODE" != "disabled" ]; then
    ask_secret MCP_API_KEY "MCP API key, min. ${API_KEY_MIN_LENGTH} characters" "$API_KEY_MIN_LENGTH" true
  fi
}

is_auth_choice() { [[ "$1" =~ ^[123]$ ]]; }

ask_container() {
  section "Container"
  ask_yes_no NON_ROOT "Run Kuvasz as a non-root user?" n
  USER_ID=""
  if [ "$NON_ROOT" = "true" ]; then
    ask USER_ID "UID:GID of the user" "1000:1000" is_uid_gid "Use the UID:GID format, like 1000:1000"
  fi
  ask_yes_no MOUNT_DOCKER_SOCKET "Mount the Docker socket to monitor the containers of this machine?" n
  SOCKET_GID=""
  if [ "$MOUNT_DOCKER_SOCKET" = "true" ]; then
    if [ ! -e "$DOCKER_SOCKET" ]; then
      # The mount is resolved where the daemon runs, so a daemon in a VM (e.g. colima) still has its socket there
      warn "${DOCKER_SOCKET} doesn't exist on this machine. It's fine if your Docker daemon runs in a VM (e.g. colima),"
      warn "but rootless Docker and Podman use another socket, so you'll have to adjust its mount in docker-compose.yml."
    fi
    if [ "$NON_ROOT" = "true" ]; then
      info "The non-root user needs the group of the socket to use it."
      ask SOCKET_GID "Group ID owning the Docker socket" "$(detect_socket_gid)" is_number "It must be a number"
    fi
  fi
}

mask() {
  if [ -n "$1" ]; then printf '********'; else printf -- '-'; fi
}

print_summary() {
  section "Summary"
  info "  Directory:          ${TARGET_DIR}"
  if [ "$OWN_DB" = "true" ]; then
    info "  Database:           own PostgreSQL at ${DB_HOST}:${DB_PORT}/${DB_NAME}"
  else
    info "  Database:           bundled PostgreSQL (kuvasz-db service)"
  fi
  info "  Database user:      ${DB_USER}"
  info "  Database password:  $(mask "$DB_PASSWORD")"
  info "  Timezone:           ${TIMEZONE}"
  info "  Image:              kuvaszmonitoring/kuvasz:${KUVASZ_VERSION}"
  info "  Port:               ${KUVASZ_PORT}"
  case "$AUTH_MODE" in
    builtin)
      info "  Authentication:     username & password"
      info "  Admin username:     ${ADMIN_USER}"
      info "  Admin password:     $(mask "$ADMIN_PASSWORD")"
      ;;
    oidc)
      info "  Authentication:     OIDC"
      info "  OIDC issuer:        ${OIDC_ISSUER}"
      info "  OIDC client ID:     ${OIDC_CLIENT_ID}"
      info "  OIDC client secret: $(mask "$OIDC_CLIENT_SECRET")"
      info "  Allowed emails:     ${OIDC_ALLOWED_EMAILS:-everyone}"
      ;;
    disabled)
      info "  Authentication:     disabled"
      ;;
  esac
  if [ "$AUTH_MODE" != "disabled" ]; then
    info "  API key:            $(mask "$API_KEY")"
  fi
  if [ "$ENABLE_MCP" = "true" ] && [ "$AUTH_MODE" != "disabled" ]; then
    info "  MCP server:         enabled, API key: $(mask "$MCP_API_KEY")"
  elif [ "$ENABLE_MCP" = "true" ]; then
    info "  MCP server:         enabled"
  else
    info "  MCP server:         disabled"
  fi
  if [ "$NON_ROOT" = "true" ]; then
    info "  Run as:             ${USER_ID}"
  else
    info "  Run as:             root"
  fi
  if [ "$MOUNT_DOCKER_SOCKET" = "true" ] && [ -n "$SOCKET_GID" ]; then
    info "  Docker socket:      mounted (group ${SOCKET_GID})"
  elif [ "$MOUNT_DOCKER_SOCKET" = "true" ]; then
    info "  Docker socket:      mounted"
  else
    info "  Docker socket:      not mounted"
  fi
  printf '\n'
}

install() {
  section "Installing"
  TMP_COMPOSE="$(mktemp "${TMPDIR:-/tmp}/kuvasz-compose.XXXXXX")"
  info "Downloading docker-compose.yml..."
  curl -fsSL "$COMPOSE_URL" -o "$TMP_COMPOSE" || die "Couldn't download the docker-compose.yml from ${COMPOSE_URL}"
  transform_compose "$TMP_COMPOSE" "${TMP_COMPOSE}.new"
  mkdir -p "$TARGET_DIR" || die "Couldn't create the directory: ${TARGET_DIR}"
  cd "$TARGET_DIR" || die "Couldn't enter the directory: ${TARGET_DIR}"
  mv "${TMP_COMPOSE}.new" docker-compose.yml
  write_kuvasz_config
  info "Starting Kuvasz..."
  docker compose up -d ||
    die "Couldn't start the stack. Check the error above, adjust ${TARGET_DIR}/docker-compose.yml if needed, then run: docker compose up -d"
}

print_result() {
  section "Done"
  printf '%s✓ Kuvasz is starting up at http://localhost:%s%s\n' "$GREEN" "$KUVASZ_PORT" "$RESET"
  info "  (if it runs on a remote server, use the address of the server instead of localhost)"
  printf '\n'
  info "Your files, if you'd like to change something later (restart the stack after editing them):"
  info "  Compose file:       ${TARGET_DIR}/docker-compose.yml"
  info "  Configuration file: ${TARGET_DIR}/kuvasz.yml"
  if [ -n "$GENERATED_SECRETS" ]; then
    printf '\n'
    warn "Generated secrets (they won't be shown again, but you can find them in the compose file):"
    printf '%s' "$GENERATED_SECRETS"
  fi
  printf '\n'
  info "Follow the logs with: cd \"${TARGET_DIR}\" && docker compose logs -f"
  info "Next steps:"
  info "  Configuration reference: ${DOCS_URL}/setup/configuration/"
  info "  Setting up integrations: ${DOCS_URL}/management/integrations/"
  if [ "$AUTH_MODE" = "oidc" ]; then
    info "  OIDC options:            ${DOCS_URL}/setup/configuration/#oidc-authentication-optional"
  fi
  if [ "$MOUNT_DOCKER_SOCKET" = "true" ]; then
    info "  Docker monitors:         ${DOCS_URL}/management/docker-monitors/"
  fi
  if [ "$ENABLE_MCP" = "true" ]; then
    info "  Connecting AI clients:   ${DOCS_URL}/features/mcp-server/#connecting-ai-clients"
  fi
}

main() {
  trap cleanup EXIT
  # Reading the answers from the terminal, instead of stdin, keeps the script working with "curl ... | bash"
  { exec 3<"$INPUT"; } 2>/dev/null || die "This script needs an interactive terminal"

  printf '%sKuvasz installer%s\n' "$BOLD" "$RESET"
  info "Press Enter to accept the default values in [brackets]."
  preflight
  printf '\n'
  choose_target_dir
  ask_database
  ask_general
  ask_authentication
  ask_container
  print_summary

  local proceed
  ask_yes_no proceed "Proceed with the installation?" y
  if [ "$proceed" = "false" ]; then
    info "Aborted, nothing has been written."
    exit 0
  fi

  install
  print_result
}

# Everything runs from here, so a partially downloaded script can't run half of the steps
main "$@"
