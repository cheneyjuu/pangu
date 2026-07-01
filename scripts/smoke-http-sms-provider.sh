#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

PANGU_PORT="${PANGU_PORT:-18080}"
FAKE_SMS_PORT="${FAKE_SMS_PORT:-19090}"
FAKE_SMS_BEARER="${FAKE_SMS_BEARER:-local-token}"
FAKE_SMS_SIGNATURE_SECRET="${FAKE_SMS_SIGNATURE_SECRET:-local-secret}"
FAKE_SMS_LOG_FILE="${FAKE_SMS_LOG_FILE:-/tmp/pangu-fake-sms-provider.jsonl}"
PANGU_LOG_FILE="${PANGU_LOG_FILE:-/tmp/pangu-http-sms-smoke.log}"
DB_CONTAINER="${DB_CONTAINER:-pangu-postgres}"
DB_NAME="${DB_NAME:-pangu_db}"
DB_USER="${DB_USER:-postgres}"
CLEANUP_FIXTURE="${CLEANUP_FIXTURE:-false}"
DRY_RUN="${DRY_RUN:-false}"
START_FAKE_SMS="${START_FAKE_SMS:-true}"
SMS_PROVIDER_TIMEOUT_MILLIS="${SMS_PROVIDER_TIMEOUT_MILLIS:-3000}"
if [[ "${START_FAKE_SMS}" == "false" ]]; then
  SMS_PROVIDER_ENDPOINT="${SMS_PROVIDER_ENDPOINT-}"
  SMS_PROVIDER_BEARER_TOKEN="${SMS_PROVIDER_BEARER_TOKEN-}"
  SMS_PROVIDER_TEMPLATE_CODE="${SMS_PROVIDER_TEMPLATE_CODE-}"
  SMS_PROVIDER_MESSAGE_ID_FIELDS="${SMS_PROVIDER_MESSAGE_ID_FIELDS-}"
  SMS_PROVIDER_SIGNATURE_SECRET="${SMS_PROVIDER_SIGNATURE_SECRET-}"
  SMS_PROVIDER_SUCCESS_CODE_FIELD="${SMS_PROVIDER_SUCCESS_CODE_FIELD-}"
  SMS_PROVIDER_SUCCESS_CODE_VALUES="${SMS_PROVIDER_SUCCESS_CODE_VALUES-}"
else
  SMS_PROVIDER_ENDPOINT="${SMS_PROVIDER_ENDPOINT:-http://127.0.0.1:${FAKE_SMS_PORT}/sms}"
  SMS_PROVIDER_BEARER_TOKEN="${SMS_PROVIDER_BEARER_TOKEN:-${FAKE_SMS_BEARER}}"
  SMS_PROVIDER_TEMPLATE_CODE="${SMS_PROVIDER_TEMPLATE_CODE:-TPL_VOTE_REMINDER}"
  SMS_PROVIDER_MESSAGE_ID_FIELDS="${SMS_PROVIDER_MESSAGE_ID_FIELDS:-data.smsId}"
  SMS_PROVIDER_SIGNATURE_SECRET="${SMS_PROVIDER_SIGNATURE_SECRET:-${FAKE_SMS_SIGNATURE_SECRET}}"
  SMS_PROVIDER_SUCCESS_CODE_FIELD="${SMS_PROVIDER_SUCCESS_CODE_FIELD:-code}"
  SMS_PROVIDER_SUCCESS_CODE_VALUES="${SMS_PROVIDER_SUCCESS_CODE_VALUES:-0}"
fi
SMS_PROVIDER_SIGNATURE_HEADER="${SMS_PROVIDER_SIGNATURE_HEADER:-X-Pangu-Signature}"
SMS_PROVIDER_SIGNATURE_TIMESTAMP_HEADER="${SMS_PROVIDER_SIGNATURE_TIMESTAMP_HEADER:-X-Pangu-Timestamp}"
EXPECTED_PROVIDER_MESSAGE_ID="${EXPECTED_PROVIDER_MESSAGE_ID-fake-sms-990481}"

FAKE_PID=""
PANGU_PID=""

usage() {
  cat <<'EOF'
Usage:
  scripts/smoke-http-sms-provider.sh

Default local smoke:
  CLEANUP_FIXTURE=true scripts/smoke-http-sms-provider.sh

External SMS provider smoke:
  DRY_RUN=true \
  START_FAKE_SMS=false \
  SMS_PROVIDER_ENDPOINT=https://sms.example.com/send \
  SMS_PROVIDER_BEARER_TOKEN=... \
  SMS_PROVIDER_TIMEOUT_MILLIS=3000 \
  SMS_PROVIDER_SIGNATURE_SECRET=... \
  SMS_PROVIDER_SIGNATURE_HEADER=X-Pangu-Signature \
  SMS_PROVIDER_SIGNATURE_TIMESTAMP_HEADER=X-Pangu-Timestamp \
  SMS_PROVIDER_TEMPLATE_CODE=TPL_VOTE_REMINDER \
  SMS_PROVIDER_MESSAGE_ID_FIELDS=data.smsId \
  SMS_PROVIDER_SUCCESS_CODE_FIELD=code \
  SMS_PROVIDER_SUCCESS_CODE_VALUES=0 \
  EXPECTED_PROVIDER_MESSAGE_ID= \
  scripts/smoke-http-sms-provider.sh

  # After checking the dry-run output:
  START_FAKE_SMS=false \
  SMS_PROVIDER_ENDPOINT=https://sms.example.com/send \
  SMS_PROVIDER_BEARER_TOKEN=... \
  SMS_PROVIDER_TIMEOUT_MILLIS=3000 \
  SMS_PROVIDER_SIGNATURE_SECRET=... \
  SMS_PROVIDER_SIGNATURE_HEADER=X-Pangu-Signature \
  SMS_PROVIDER_SIGNATURE_TIMESTAMP_HEADER=X-Pangu-Timestamp \
  SMS_PROVIDER_TEMPLATE_CODE=TPL_VOTE_REMINDER \
  SMS_PROVIDER_MESSAGE_ID_FIELDS=data.smsId \
  SMS_PROVIDER_SUCCESS_CODE_FIELD=code \
  SMS_PROVIDER_SUCCESS_CODE_VALUES=0 \
  EXPECTED_PROVIDER_MESSAGE_ID= \
  CLEANUP_FIXTURE=true \
  scripts/smoke-http-sms-provider.sh

Environment:
  PANGU_PORT                         default: 18080
  FAKE_SMS_PORT                      default: 19090
  START_FAKE_SMS                     true starts local fake SMS provider; false uses SMS_PROVIDER_ENDPOINT
  SMS_PROVIDER_ENDPOINT              provider POST endpoint
  SMS_PROVIDER_BEARER_TOKEN          optional bearer token
  SMS_PROVIDER_TIMEOUT_MILLIS        HTTP connect/request timeout in milliseconds
  SMS_PROVIDER_TEMPLATE_CODE         template code sent as templateCode
  SMS_PROVIDER_MESSAGE_ID_FIELDS     comma-separated provider receipt fields; dot paths allowed
  SMS_PROVIDER_SUCCESS_CODE_FIELD    optional provider business success code field; dot paths allowed
  SMS_PROVIDER_SUCCESS_CODE_VALUES   comma-separated success values when success code field is configured
  SMS_PROVIDER_SIGNATURE_SECRET      optional HMAC-SHA256 secret
  SMS_PROVIDER_SIGNATURE_HEADER      signature header name
  SMS_PROVIDER_SIGNATURE_TIMESTAMP_HEADER timestamp header name
  EXPECTED_PROVIDER_MESSAGE_ID       expected receipt; empty means any non-empty receipt
  DRY_RUN                            true prints sanitized config and exits before DB/service work
  CLEANUP_FIXTURE                    true removes delivery_id=990481 fixture on exit
  DB_CONTAINER                       default: pangu-postgres
  DB_NAME                            default: pangu_db
  DB_USER                            default: postgres

Notes:
  The script prepares delivery_id=990481, starts pangu in HTTP provider mode,
  waits for CONFIRMED status, and stops the processes it starts.
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi
if [[ $# -gt 0 ]]; then
  echo "[smoke] unknown argument: $1" >&2
  usage >&2
  exit 2
fi

mask() {
  local value="$1"
  if [[ -z "${value}" ]]; then
    echo "<empty>"
  elif [[ ${#value} -le 6 ]]; then
    echo "***"
  else
    echo "${value:0:3}***${value: -3}"
  fi
}

require_boolean() {
  local name="$1"
  local value="$2"
  if [[ "${value}" != "true" && "${value}" != "false" ]]; then
    echo "[smoke] ${name} must be true or false, got: ${value}" >&2
    exit 2
  fi
}

require_positive_integer() {
  local name="$1"
  local value="$2"
  if [[ ! "${value}" =~ ^[1-9][0-9]*$ ]]; then
    echo "[smoke] ${name} must be a positive integer, got: ${value}" >&2
    exit 2
  fi
}

preflight() {
  require_boolean "DRY_RUN" "${DRY_RUN}"
  require_boolean "START_FAKE_SMS" "${START_FAKE_SMS}"
  require_boolean "CLEANUP_FIXTURE" "${CLEANUP_FIXTURE}"
  require_positive_integer "SMS_PROVIDER_TIMEOUT_MILLIS" "${SMS_PROVIDER_TIMEOUT_MILLIS}"
  if [[ -z "${SMS_PROVIDER_ENDPOINT}" ]]; then
    echo "[smoke] SMS_PROVIDER_ENDPOINT is required" >&2
    exit 2
  fi
  if [[ -n "${SMS_PROVIDER_SUCCESS_CODE_FIELD}" && -z "${SMS_PROVIDER_SUCCESS_CODE_VALUES}" ]]; then
    echo "[smoke] SMS_PROVIDER_SUCCESS_CODE_VALUES is required when SMS_PROVIDER_SUCCESS_CODE_FIELD is configured" >&2
    exit 2
  fi
  if [[ -z "${SMS_PROVIDER_SUCCESS_CODE_FIELD}" && -n "${SMS_PROVIDER_SUCCESS_CODE_VALUES}" ]]; then
    echo "[smoke] SMS_PROVIDER_SUCCESS_CODE_FIELD is required when SMS_PROVIDER_SUCCESS_CODE_VALUES is configured" >&2
    exit 2
  fi

  echo "[smoke] config:"
  echo "        START_FAKE_SMS=${START_FAKE_SMS}"
  echo "        SMS_PROVIDER_ENDPOINT=${SMS_PROVIDER_ENDPOINT}"
  echo "        SMS_PROVIDER_BEARER_TOKEN=$(mask "${SMS_PROVIDER_BEARER_TOKEN}")"
  echo "        SMS_PROVIDER_TIMEOUT_MILLIS=${SMS_PROVIDER_TIMEOUT_MILLIS}"
  echo "        SMS_PROVIDER_TEMPLATE_CODE=${SMS_PROVIDER_TEMPLATE_CODE:-<empty>}"
  echo "        SMS_PROVIDER_MESSAGE_ID_FIELDS=${SMS_PROVIDER_MESSAGE_ID_FIELDS:-<default>}"
  echo "        SMS_PROVIDER_SUCCESS_CODE_FIELD=${SMS_PROVIDER_SUCCESS_CODE_FIELD:-<disabled>}"
  echo "        SMS_PROVIDER_SUCCESS_CODE_VALUES=${SMS_PROVIDER_SUCCESS_CODE_VALUES:-<empty>}"
  echo "        SMS_PROVIDER_SIGNATURE_SECRET=$(mask "${SMS_PROVIDER_SIGNATURE_SECRET}")"
  echo "        SMS_PROVIDER_SIGNATURE_HEADER=${SMS_PROVIDER_SIGNATURE_HEADER}"
  echo "        SMS_PROVIDER_SIGNATURE_TIMESTAMP_HEADER=${SMS_PROVIDER_SIGNATURE_TIMESTAMP_HEADER}"
  echo "        EXPECTED_PROVIDER_MESSAGE_ID=${EXPECTED_PROVIDER_MESSAGE_ID:-<any-non-empty>}"
  echo "        DRY_RUN=${DRY_RUN}"
  echo "        CLEANUP_FIXTURE=${CLEANUP_FIXTURE}"
}

cleanup() {
  if [[ -n "${PANGU_PID}" ]] && kill -0 "${PANGU_PID}" 2>/dev/null; then
    kill "${PANGU_PID}" 2>/dev/null || true
    wait "${PANGU_PID}" 2>/dev/null || true
  fi
  if [[ -n "${FAKE_PID}" ]] && kill -0 "${FAKE_PID}" 2>/dev/null; then
    kill "${FAKE_PID}" 2>/dev/null || true
    wait "${FAKE_PID}" 2>/dev/null || true
  fi
  if [[ "${CLEANUP_FIXTURE}" == "true" ]]; then
    docker exec -i "${DB_CONTAINER}" psql -U "${DB_USER}" -d "${DB_NAME}" \
      < "${ROOT_DIR}/scripts/cleanup-http-sms-provider-smoke.sql" >/dev/null
  fi
}
trap cleanup EXIT

wait_for_http() {
  local url="$1"
  local label="$2"
  for _ in $(seq 1 60); do
    local code
    code="$(curl -s -o /dev/null -w '%{http_code}' "${url}" || true)"
    if [[ "${code}" != "000" ]]; then
      echo "[smoke] ${label} is reachable, http=${code}"
      return 0
    fi
    sleep 1
  done
  echo "[smoke] timed out waiting for ${label}: ${url}" >&2
  return 1
}

wait_for_delivery_confirmed() {
  for _ in $(seq 1 36); do
    local row
    row="$(docker exec "${DB_CONTAINER}" psql -U "${DB_USER}" -d "${DB_NAME}" -At -c \
      "SELECT delivery_status || ',' || COALESCE(provider_message_id, '') || ',' || attempts FROM t_voting_reminder_delivery WHERE delivery_id = 990481;" \
      | tr -d '\r')"
    echo "[smoke] delivery row: ${row}"
    if [[ -n "${EXPECTED_PROVIDER_MESSAGE_ID}" && "${row}" == "3,${EXPECTED_PROVIDER_MESSAGE_ID},"* ]]; then
      return 0
    fi
    if [[ -z "${EXPECTED_PROVIDER_MESSAGE_ID}" && "${row}" == 3,*,* && "${row}" != "3,,"* ]]; then
      return 0
    fi
    sleep 5
  done
  echo "[smoke] timed out waiting for delivery 990481 to confirm" >&2
  return 1
}

cd "${ROOT_DIR}"

preflight
if [[ "${DRY_RUN}" == "true" ]]; then
  echo "[smoke] dry run complete; no fixture, Maven build, fake provider, or pangu process was started"
  exit 0
fi

if curl -s -o /dev/null "http://127.0.0.1:${PANGU_PORT}/pangu/actuator/health"; then
  echo "[smoke] port ${PANGU_PORT} already serves HTTP; choose another PANGU_PORT" >&2
  exit 2
fi

rm -f "${FAKE_SMS_LOG_FILE}" "${PANGU_LOG_FILE}"

echo "[smoke] preparing HTTP SMS provider fixture"
docker exec -i "${DB_CONTAINER}" psql -U "${DB_USER}" -d "${DB_NAME}" \
  < "${ROOT_DIR}/scripts/prepare-http-sms-provider-smoke.sql"

echo "[smoke] installing current pangu reactor artifacts"
mvn -pl pangu-bootstrap -am -DskipTests install

if [[ "${START_FAKE_SMS}" == "true" ]]; then
  echo "[smoke] starting fake SMS provider on ${FAKE_SMS_PORT}"
  FAKE_SMS_PORT="${FAKE_SMS_PORT}" \
  FAKE_SMS_BEARER="${SMS_PROVIDER_BEARER_TOKEN}" \
  FAKE_SMS_SIGNATURE_SECRET="${SMS_PROVIDER_SIGNATURE_SECRET}" \
  FAKE_SMS_SIGNATURE_HEADER="${SMS_PROVIDER_SIGNATURE_HEADER}" \
  FAKE_SMS_TIMESTAMP_HEADER="${SMS_PROVIDER_SIGNATURE_TIMESTAMP_HEADER}" \
  FAKE_SMS_LOG_FILE="${FAKE_SMS_LOG_FILE}" \
  node "${ROOT_DIR}/scripts/fake-sms-provider.mjs" &
  FAKE_PID="$!"
  wait_for_http "http://127.0.0.1:${FAKE_SMS_PORT}/sms" "fake SMS provider"
else
  echo "[smoke] using external SMS provider endpoint: ${SMS_PROVIDER_ENDPOINT}"
fi

export SPRING_APPLICATION_JSON
SPRING_APPLICATION_JSON="$(cat <<JSON
{
  "server": { "port": ${PANGU_PORT} },
  "platform": {
    "voting": {
      "sms-provider-mode": "http",
      "reminder-delivery-cron": "*/5 * * * * *",
      "sms-provider": {
        "endpoint": "${SMS_PROVIDER_ENDPOINT}",
        "bearer-token": "${SMS_PROVIDER_BEARER_TOKEN}",
        "timeout-millis": ${SMS_PROVIDER_TIMEOUT_MILLIS},
        "template-code": "${SMS_PROVIDER_TEMPLATE_CODE}",
        "provider-message-id-fields": "${SMS_PROVIDER_MESSAGE_ID_FIELDS}",
        "success-code-field": "${SMS_PROVIDER_SUCCESS_CODE_FIELD}",
        "success-code-values": "${SMS_PROVIDER_SUCCESS_CODE_VALUES}",
        "signature-secret": "${SMS_PROVIDER_SIGNATURE_SECRET}",
        "signature-header": "${SMS_PROVIDER_SIGNATURE_HEADER}",
        "signature-timestamp-header": "${SMS_PROVIDER_SIGNATURE_TIMESTAMP_HEADER}"
      }
    }
  }
}
JSON
)"

echo "[smoke] starting pangu on ${PANGU_PORT} with HTTP SMS provider"
mvn -f pangu-bootstrap/pom.xml \
  org.springframework.boot:spring-boot-maven-plugin:3.2.5:run \
  -Dspring-boot.run.mainClass=com.pangu.bootstrap.PanguApplication \
  > "${PANGU_LOG_FILE}" 2>&1 &
PANGU_PID="$!"

wait_for_http "http://127.0.0.1:${PANGU_PORT}/pangu/actuator/health" "pangu"
wait_for_delivery_confirmed

echo "[smoke] HTTP SMS provider smoke passed"
if [[ "${START_FAKE_SMS}" == "true" ]]; then
  echo "[smoke] fake provider log: ${FAKE_SMS_LOG_FILE}"
fi
echo "[smoke] pangu log: ${PANGU_LOG_FILE}"
if [[ "${CLEANUP_FIXTURE}" == "true" ]]; then
  echo "[smoke] cleanup fixture will run on exit"
else
  echo "[smoke] cleanup fixture manually with:"
  echo "        docker exec -i ${DB_CONTAINER} psql -U ${DB_USER} -d ${DB_NAME} < scripts/cleanup-http-sms-provider-smoke.sql"
fi
