# DB — модель данных (PostgreSQL 16)

Схема создаётся миграцией `V1__init_schema.sql`, демо-данные — `V2__seed_demo_data.sql`.
**Схема меняется только новыми миграциями Flyway. Существующие миграции не правим.**

## Таблицы

### department
| Колонка | Тип | Примечание |
|---|---|---|
| id | bigint, PK, identity | |
| code | text, unique, not null | короткий код: `DEV-BE` |
| name | text, not null | название на русском |
| description | text | чем занимается подразделение |
| parent_id | bigint → department.id | null только у корня |
| head_employee_id | bigint → employee.id | руководитель подразделения |

### employee
| Колонка | Тип | Примечание |
|---|---|---|
| id | bigint, PK, identity | |
| full_name | text, not null | «Фамилия Имя Отчество» |
| position | text, not null | должность |
| email | text, unique, not null | синтетический: `@example.test` |
| phone | text | |
| department_id | bigint → department.id, not null | основное подразделение |

### reporting_line — подчинение (линейное и матричное)
| Колонка | Тип | Примечание |
|---|---|---|
| id | bigint, PK, identity | |
| employee_id | bigint → employee.id, not null | подчинённый |
| manager_id | bigint → employee.id, not null | руководитель |
| line_type | text, not null | `LINE` или `DOTTED` |
| comment | text | для DOTTED: в рамках чего подчинение |

Ограничения: `employee_id <> manager_id`; уникальна пара `(employee_id, manager_id)`;
частичный уникальный индекс — не более одной `LINE`-строки на сотрудника.

### assignment — задачи и зоны ответственности
| Колонка | Тип | Примечание |
|---|---|---|
| id | bigint, PK, identity | |
| employee_id | bigint → employee.id, not null | |
| kind | text, not null | `TASK` или `RESPONSIBILITY` |
| title | text, not null | краткая формулировка |
| description | text | подробности |
| sort_order | int, not null, default 0 | порядок показа |

## ER-схема (текстом)
```
department 1───* employee            (employee.department_id)
department *───1 department          (parent_id, дерево подразделений)
department *───0..1 employee         (head_employee_id)
employee 1───* reporting_line        (employee_id = подчинённый)
employee 1───* reporting_line        (manager_id = руководитель)
employee 1───* assignment
```

## Рекурсивные запросы (подсказка для агента)
Дерево задаётся «списком смежности» (id + parent/manager). Поддерево или цепочка
получаются через `WITH RECURSIVE`. Всегда ограничивайте глубину (`depth < 20`),
чтобы случайный цикл в данных не повесил запрос.

```sql
-- Все подразделения поддерева, начиная с :rootId
WITH RECURSIVE sub AS (
  SELECT id, parent_id, name, 0 AS depth FROM department WHERE id = :rootId
  UNION ALL
  SELECT d.id, d.parent_id, d.name, s.depth + 1
  FROM department d JOIN sub s ON d.parent_id = s.id
  WHERE s.depth < 20
)
SELECT * FROM sub;
```

## Проверки целостности (использовать в тестах)
```sql
-- Должна вернуть 0 строк: сотрудники без LINE-руководителя, кроме главы компании
SELECT e.id FROM employee e
WHERE NOT EXISTS (SELECT 1 FROM reporting_line r WHERE r.employee_id = e.id AND r.line_type = 'LINE');
-- ожидаем ровно 1 строку (глава компании)
```

## Правила для агента
- Нумерация миграций: `V1`, `V2`, ... Новая миграция берёт следующий свободный номер.
  При работе нескольких человек номера согласуются в чате команды, чтобы не было коллизий.
- Не используйте `spring.jpa.hibernate.ddl-auto`. Схема — только Flyway.
- Все SQL-запросы лежат в репозиториях (`*Repository`), не в контроллерах.
