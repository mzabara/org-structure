# Backend (Kotlin + Spring Boot) — правила

Действуют правила корневого `AGENTS.md` и `docs/CODE-STYLE.md`.

- Каталог работы: только `backend/`.
- Слои: `api` → `service` → `repository`. Пакет: `ru.example.orgtree`.
- БД: Spring `JdbcClient`, SQL в репозиториях, параметры именованные. Схема — только Flyway.
- Контракт: `docs/openapi.yaml`. Не меняй без явной просьбы человека.
- Ошибки: единый обработчик → `application/problem+json` с русским `title`.
- Команды: `docker compose up -d db` (из корня), `./gradlew bootRun`, `./gradlew build`.
- Тесты — Testcontainers (нужен Docker) на данных миграции V2.
- Комментарии/KDoc — на русском, «почему», а не «что».
