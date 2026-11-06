# ARCHITECTURE — стек и устройство

## Стек
| Слой | Технологии |
|---|---|
| Frontend | Angular 22, TypeScript, standalone-компоненты, signals, SCSS, Taiga UI 5 |
| Backend | Kotlin, Spring Boot 3.x (Web, JDBC, Validation, Actuator), Gradle Kotlin DSL, JDK 21 |
| Доступ к БД | Spring `JdbcClient` + SQL в репозиториях (без ORM — рекурсивные запросы проще и прозрачнее) |
| Миграции | Flyway |
| БД | PostgreSQL 16 (docker compose) |
| Тесты backend | JUnit 5, Spring Boot Test, Testcontainers (PostgreSQL) |
| Тесты frontend | встроенный раннер Angular 22, тесты сервисов и ключевых компонентов |
| Контракт | `docs/openapi.yaml`; типы для frontend генерируются `openapi-typescript` (devDependency) |

Новые зависимости — только по согласованию с командой.

## Почему так
- **Нет ORM:** дерево — это рекурсивные SQL-запросы; на JPA они превращаются в магию, которую трудно читать и объяснять.
- **Taiga UI 5 как дизайн-система:** готовые доступные компоненты (OnPush, strict TypeScript) закрывают базовые элементы интерфейса — кнопки, поля, вкладки, бейджи, подсказки, уведомления, скелетоны. Меньше ручной вёрстки — больше времени на логику дня.
- **Контракт первым:** frontend и backend пишутся параллельно разными людьми и агентами, не ожидая друг друга.
- **Деревья без сторонних библиотек:** в Taiga UI нет древовидного компонента, поэтому дерево — собственный рекурсивный компонент + CSS. `d3-hierarchy` — только если нужен автоматический layout.

## Backend: слои и пакеты
```
ru.example.orgtree
├── OrgTreeApplication.kt
├── api/                 контроллеры (тонкие) и DTO по openapi.yaml
│   ├── department/      DepartmentController, DepartmentDto...
│   ├── employee/        EmployeeController, EmployeeDto...
│   └── search/          SearchController
├── service/             бизнес-логика: сборка деревьев, цепочки, поиск
├── repository/          SQL (JdbcClient): DepartmentRepository, EmployeeRepository, ...
├── security/            ApiKeyFilter, конфигурация ключей
└── error/               глобальный обработчик ошибок → problem+json
```
Правила: контроллер ничего не знает про SQL; сервис ничего не знает про HTTP; DTO отделены от внутренних моделей.

## Frontend: структура
```
src/app
├── core/                api-клиент (HttpClient), сгенерированные типы, обработка ошибок
├── features/
│   ├── departments/     дерево подразделений, карточка отдела
│   ├── employees/       дерево сотрудников, карточка сотрудника
│   └── search/          строка поиска
├── shared/              общие компоненты (TreeNode, Avatar, EmptyState, ErrorBanner)
└── app.routes.ts        маршруты: /departments, /employees, /departments/:id, /employees/:id
```

## Конфигурация
| Параметр | Назначение | По умолчанию |
|---|---|---|
| `spring.datasource.*` | подключение к БД | localhost:5432/orgtree, orgtree/orgtree |
| `app.security.api-keys` | список допустимых API-ключей | пусто (проверка отключена) |
| Профиль `dev` | локальная разработка, без ключей | включён |
| CORS | разрешить `http://localhost:4200` в dev | — |

## Порты
Backend: 8080. Frontend dev: 4200 (прокси `/api` → `localhost:8080`). БД: 5432.

## Безопасность
- Данные только синтетические.
- В логи не пишем API-ключи и персональные данные.
- Параметры SQL — только через плейсхолдеры, никакой конкатенации строк.
