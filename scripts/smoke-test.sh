#!/usr/bin/env bash
# End-to-end check against a running CareDesk: finds a free slot, books it, checks that
# booking it again is refused with 409, logs in with the demo account and checks the
# home page was prerendered. CI runs it against the docker compose stack.
#
#   scripts/smoke-test.sh [base-url]      (default http://localhost:8081)
set -uo pipefail

BASE=${1:-http://localhost:8081}
RESP=$(mktemp)
trap 'rm -f "$RESP"' EXIT

fail() {
  echo "::error title=Smoke test::$1"
  echo "FAILED: $1" >&2
  exit 1
}

# call METHOD PATH [JSON]: sets CODE and leaves the response body in $RESP
call() {
  local method=$1 path=$2 body=${3:-}
  local args=(-sS -o "$RESP" -w '%{http_code}' -X "$method" "$BASE$path")
  if [ -n "$body" ]; then
    args+=(-H 'Content-Type: application/json' -d "$body")
  fi
  CODE=$(curl "${args[@]}") || fail "$method $path: could not connect"
  echo "$method $path -> $CODE"
}

expect() {
  [ "$CODE" = "$1" ] || fail "$2 returned $CODE (expected $1): $(head -c 400 "$RESP" | tr '\n' ' ')"
}

call GET /api/public/services
expect 200 "services"
service=$(jq '.[0].id' "$RESP")

slot=""
for i in $(seq 1 14); do
  day=$(python3 -c 'import datetime, sys; print(datetime.date.today() + datetime.timedelta(days=int(sys.argv[1])))' "$i")
  call GET "/api/public/slots?serviceId=$service&date=$day"
  expect 200 "slots for $day"
  slot=$(jq -c '.[0] // empty' "$RESP")
  [ -n "$slot" ] && break
done
[ -n "$slot" ] || fail "no free slot in the next 14 days"

body=$(jq -nc --argjson s "$slot" --argjson svc "$service" \
  '{serviceId: $svc, staffId: $s.staffId, start: $s.start, customerName: "Smoke Test", customerEmail: "smoke@example.com"}')

call POST /api/public/appointments "$body"
expect 201 "first booking"
echo "booked $(jq -r '.reference' "$RESP") at $(jq -r '.start' "$RESP")"

call POST /api/public/appointments "$body"
expect 409 "booking the same slot again"

call POST /api/auth/login '{"email":"demo@caredesk.test","password":"demo1234"}'
expect 200 "demo login"
jq -e '.user.roles[0] == "ADMIN"' "$RESP" > /dev/null || fail "login didn't return the ADMIN role"

call GET /
expect 200 "home page"
grep -q 'What do you need?' "$RESP" || fail "home page isn't prerendered"

echo "Smoke test passed"
