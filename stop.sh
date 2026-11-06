#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"
for service in frontend backend; do
  pidfile=".run/$service.pid"
  [[ -f "$pidfile" ]] || continue
  pid="$(<"$pidfile")"
  if [[ "$pid" =~ ^[0-9]+$ ]] && kill -0 "$pid" 2>/dev/null; then
    command_line="$(ps -p "$pid" -o command=)"
    if [[ "$service" == frontend && "$command_line" == ng\ serve* ]]; then
      process_directory="$(lsof -a -p "$pid" -d cwd -Fn 2>/dev/null || true)"
      if [[ "$process_directory" == *$'\n'"n$ROOT/frontend" ]]; then
        command_line="$ROOT/frontend/node_modules/@angular/cli/bin/ng.js"
      fi
    fi
    case "$command_line" in
      *"$ROOT/frontend/node_modules/@angular/cli/bin/ng.js"*|*"$ROOT/backend/build/libs/org-structure-0.1.0.jar"*)
        kill "$pid"
        for ((attempt = 1; attempt <= 30; attempt++)); do
          kill -0 "$pid" 2>/dev/null || break
          sleep 1
        done
        if kill -0 "$pid" 2>/dev/null; then
          printf 'Процесс %s ещё завершается; повторите ./stop.sh.\n' "$service" >&2
          exit 1
        fi
        ;;
      *)
        printf 'PID %s не принадлежит приложению, сигнал не отправлен.\n' "$pid" >&2
        exit 1
        ;;
    esac
  fi
  rm -f "$pidfile"
done
docker compose stop db
printf '%s\n' "Приложение остановлено. Данные PostgreSQL сохранены."
