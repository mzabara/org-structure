# Оргструктура компании

Локальное приложение с двумя деревьями, связанными карточками и поиском.
Данные только синтетические: 13 отделов, 31 сотрудник, 7 матричных связей.
Контракт API: `docs/openapi.yaml`. Исходные миграции V1/V2 не изменяются.

## Требования

- macOS или Linux, Bash, `curl`, `lsof`.
- Docker с Compose v2 и запущенным Docker daemon (например, Docker Desktop).
- JDK 21, Node.js 24 LTS, npm. Gradle загружается через Wrapper.
- Доступ к Maven Central и npm при первой сборке.
- Порт 5432 для PostgreSQL. Не останавливайте чужую БД ради запуска.

## Запуск и остановка

Из корня репозитория:

```bash
./start.sh
```

Скрипт дожидается готовности PostgreSQL, собирает backend, устанавливает
зависимости frontend, генерирует типы, запускает оба сервера и проверяет
запрос `/api/v1/departments/tree` через frontend-прокси.
Если сборка или запуск завершается с ошибкой, выводится хвост соответствующего лога.

По умолчанию интерфейс: http://127.0.0.1:4204,
backend: http://127.0.0.1:8080, health: http://127.0.0.1:8080/actuator/health.
При занятых портах HTTP скрипт подбирает свободные и печатает фактические URL.
Можно задать начальные порты:

```bash
SERVER_PORT=8090 FRONTEND_PORT=4210 ./start.sh
```

Серверы работают в фоне. Логи сборки и запуска, PID и результат проверки
прокси находятся в `.run/`. Повторный запуск требует предварительной остановки:

```bash
./stop.sh
```

Останавливаются только процессы с PID и командой этого проекта, затем
Compose-сервис `db`. Том PostgreSQL и данные не удаляются.

## Проверки

### Windows

Для Windows используйте **PowerShell 7** (`pwsh`), JDK 21, Node.js и Docker
Desktop в режиме Linux containers. Bash, WSL, `curl` и `lsof` не нужны.
Из корня репозитория:

```powershell
pwsh -NoProfile -File .\start.ps1
pwsh -NoProfile -File .\stop.ps1
```

Скрипты ждут готовности БД и HTTP-сервисов, проверяют API через прокси,
выбирают свободные HTTP-порты и печатают URL. Порт БД остаётся 5432.
Запуск явно выбирает `dev`; ключи в frontend не передаются.
Остановка проверяет PID, время запуска и путь приложения в командной строке,
не удаляет том БД. Логи: `.run/windows.*.log`, служебные файлы
Windows отделены от файлов Bash-скриптов. Одновременный запуск обеих
версий в одной рабочей копии не поддерживается.

Настройка начальных HTTP-портов:

```powershell
$env:SERVER_PORT = '8090'
$env:FRONTEND_PORT = '4210'
pwsh -NoProfile -File .\start.ps1
```

Если локальная политика выполнения блокирует `.ps1`, после проверки содержимого
можно разрешить выполнение только для отдельного процесса, без изменения
системной политики:

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\start.ps1
pwsh -NoProfile -ExecutionPolicy Bypass -File .\stop.ps1
```

На Windows для проверок backend используйте `.\gradlew.bat` вместо `./gradlew`.
Скрипты Windows требуют проверки на Windows: текущая среда разработки macOS
не содержит PowerShell.

Backend с Docker и настоящим PostgreSQL в Testcontainers:

```bash
cd backend
./gradlew build
./gradlew test --rerun-tasks
cd ..
```

Frontend, включая генератор типов (устанавливается отдельно для изоляции
его CLI-зависимостей от приложения):

```bash
cd frontend
npm ci
npm --prefix tools/openapi ci
npm run generate:api
npm run typecheck
npm test
npm run build
cd ..
git diff --check
```

Проверка запущенного API (замените порт, если скрипт выбрал другой):

```bash
curl --fail http://127.0.0.1:8080/actuator/health
curl --fail http://127.0.0.1:4204/api/v1/departments/tree
curl -i http://127.0.0.1:8080/api/v1/employees/999
curl -i 'http://127.0.0.1:8080/api/v1/employees?size=500'
```

Последние два запроса должны вернуть 404 и 400, тип `application/problem+json`.
Чек-лист ручной приёмки: `docs/ACCEPTANCE.md`; автоматические результаты
фиксируются отдельно в `docs/PLAN.md`.

## Конфигурация и безопасность

`start.sh` явно выбирает профиль `dev`: API-ключ для локального UI не нужен.
Сервисы HTTP слушают `127.0.0.1`. Не используйте этот режим для публикации.
Секреты в frontend не передаются.

Без профиля `dev` API требует `X-API-Key`, даже если список ключей пуст.
Для отдельного защищённого запуска backend, после запуска БД:

```bash
cd backend
DB_PASSWORD=orgtree API_KEYS="$ORG_API_KEY" ./gradlew bootRun
```

`ORG_API_KEY` задаётся в вашем окружении, не в файлах frontend.
Параметры backend: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `API_KEYS`, `SERVER_PORT`.
Для отдельно запущенного frontend `BACKEND_URL` задаёт адрес dev-прокси:

```bash
cd frontend
BACKEND_URL=http://127.0.0.1:8080 npm start
```
