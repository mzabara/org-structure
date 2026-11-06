package ru.example.orgtree.api

/** Краткая ссылка на подразделение для карточек, списков и поиска. */
data class DepartmentSummary(val id: Long, val code: String, val name: String)

/** Краткая ссылка на сотрудника без раскрытия его связей. */
data class EmployeeSummary(
    val id: Long,
    val fullName: String,
    val position: String,
    val department: DepartmentSummary,
)

/** Узел дерева отделов со счётчиками, независимыми от запрошенной глубины. */
data class DepartmentTreeNode(
    val id: Long,
    val code: String,
    val name: String,
    val head: EmployeeSummary?,
    val employeeCount: Int,
    val totalEmployeeCount: Int,
    val children: List<DepartmentTreeNode>,
)

/** Карточка подразделения со ссылками на соседние сущности. */
data class Department(
    val id: Long,
    val code: String,
    val name: String,
    val description: String?,
    val parent: DepartmentSummary?,
    val head: EmployeeSummary?,
    val children: List<DepartmentSummary>,
    val employeeCount: Int,
    val totalEmployeeCount: Int,
)

/** Типы связей не смешивают основное дерево с матричным подчинением. */
enum class ReportingType { LINE, DOTTED }

/** Фильтр списка подчинённых, включая оба типа связей. */
enum class ReportFilter { LINE, DOTTED, ALL }

/** Разделяет выполняемую работу и область ответственности. */
enum class AssignmentKind { TASK, RESPONSIBILITY }

/** Сотрудник на другом конце связи и её контекст. */
data class ReportingRelation(val employee: EmployeeSummary, val type: ReportingType, val comment: String?)

/** Задача либо ответственность в порядке, заданном исходными данными. */
data class Assignment(val id: Long, val kind: AssignmentKind, val title: String, val description: String?)

/** Полная карточка сотрудника по внешнему контракту. */
data class Employee(
    val id: Long,
    val fullName: String,
    val position: String,
    val email: String,
    val phone: String?,
    val department: DepartmentSummary,
    val lineManager: EmployeeSummary?,
    val dottedManagers: List<ReportingRelation>,
    val directReports: List<ReportingRelation>,
    val assignments: List<Assignment>,
)

/** Линейное дерево хранит матричных руководителей отдельно от дочерних узлов. */
data class EmployeeTreeNode(
    val id: Long,
    val fullName: String,
    val position: String,
    val department: DepartmentSummary,
    val dottedManagerIds: List<Long>,
    val children: List<EmployeeTreeNode>,
)

/** Страница сотрудников с нумерацией от нуля. */
data class EmployeePage(
    val items: List<EmployeeSummary>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int,
)

/** Найденный сотрудник и линейный путь от корня для раскрытия дерева. */
data class EmployeeMatch(val employee: EmployeeSummary, val pathIds: List<Long>)

/** Найденный отдел и путь от корня для раскрытия дерева. */
data class DepartmentMatch(val department: DepartmentSummary, val pathIds: List<Long>)

/** Общий поиск ограничивает каждую группу результатов независимо. */
data class SearchResult(val employees: List<EmployeeMatch>, val departments: List<DepartmentMatch>)
