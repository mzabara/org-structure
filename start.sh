#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"
mkdir -p .run

if ! mkdir .run/start.lock 2>/dev/null; then
  printf '%s\n' "Другой запуск уже выполняется. Дождитесь его завершения." >&2
  exit 1
fi
started=()
cleanup() {
  status=$?
  rmdir "$ROOT/.run/start.lock"
  if ((status != 0)); then
    for pid in "${started[@]}"; do kill "$pid" 2>/dev/null || true; done
  fi
}
trap cleanup EXIT
trap 'exit 130' INT TERM

for tool in docker java node npm curl lsof; do
  command -v "$tool" >/dev/null || { printf 'Не найдена команда: %s\n' "$tool" >&2; exit 1; }
done
if ! docker info >/dev/null 2>&1; then
  printf '%s\n' "Запустите Docker Desktop и повторите ./start.sh." >&2
  exit 1
fi
for service in backend frontend; do
  if [[ -f ".run/$service.pid" ]]; then
    pid="$(<".run/$service.pid")"
    if [[ "$pid" =~ ^[0-9]+$ ]] && kill -0 "$pid" 2>/dev/null; then
      printf 'В .run/%s.pid указан работающий процесс. Выполните ./stop.sh перед повторным запуском.\n' "$service" >&2
      exit 1
    fi
  fi
done

free_port() {
  local port="$1"
  while lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; do port=$((port + 1)); done
  printf '%s' "$port"
}
backend_port="$(free_port "${SERVER_PORT:-8080}")"
frontend_port="$(free_port "${FRONTEND_PORT:-4204}")"

printf '%s\n' "Запуск PostgreSQL..."
docker compose up -d --wait db
printf '%s\n' "Сборка backend (ошибки будут выведены сразу)..."
if ! (cd backend && ./gradlew bootJar --console=plain) > .run/backend-build.log 2>&1; then
  tail -n 60 .run/backend-build.log >&2
  exit 1
fi
printf '%s\n' "Подготовка frontend..."
(
  cd frontend
  if [[ -f package-lock.json ]]; then npm ci; else npm install; fi
  if [[ -f tools/openapi/package-lock.json ]]; then
    npm --prefix tools/openapi ci
  else
    npm --prefix tools/openapi install
  fi
  npm run generate:api
) > .run/frontend-build.log 2>&1 || { tail -n 60 .run/frontend-build.log >&2; exit 1; }

wait_http() {
  local name="$1" pid="$2" url="$3" log="$4"
  for ((attempt = 1; attempt <= 90; attempt++)); do
    if ! kill -0 "$pid" 2>/dev/null; then
      printf '%s завершился при запуске:\n' "$name" >&2
      tail -n 60 "$log" >&2
      return 1
    fi
    if curl --silent --fail --max-time 2 "$url" >/dev/null 2>&1; then return 0; fi
    sleep 1
  done
  printf 'Истекло время ожидания %s:\n' "$name" >&2
  tail -n 60 "$log" >&2
  return 1
}

printf 'Запуск backend на порту %s...\n' "$backend_port"
SPRING_PROFILES_ACTIVE=dev SERVER_PORT="$backend_port" \
  nohup java -jar "$ROOT/backend/build/libs/org-structure-0.1.0.jar" > .run/backend.log 2>&1 &
backend_pid=$!
started+=("$backend_pid")
printf '%s\n' "$backend_pid" > .run/backend.pid
wait_http backend "$backend_pid" "http://127.0.0.1:$backend_port/actuator/health" .run/backend.log

printf 'Запуск frontend на порту %s...\n' "$frontend_port"
(
  cd frontend
  export BACKEND_URL="http://127.0.0.1:$backend_port"
  exec nohup node "$ROOT/frontend/node_modules/@angular/cli/bin/ng.js" serve \
    --host 127.0.0.1 --port "$frontend_port" --proxy-config proxy.conf.cjs
) > .run/frontend.log 2>&1 &
frontend_pid=$!
started+=("$frontend_pid")
printf '%s\n' "$frontend_pid" > .run/frontend.pid
wait_http frontend "$frontend_pid" "http://127.0.0.1:$frontend_port/" .run/frontend.log
curl --silent --show-error --fail "http://127.0.0.1:$frontend_port/api/v1/departments/tree" > .run/proxy-check.json
printf 'Оргструктура: http://127.0.0.1:%s\nBackend: http://127.0.0.1:%s\nОстановка: ./stop.sh\n' "$frontend_port" "$backend_port"
