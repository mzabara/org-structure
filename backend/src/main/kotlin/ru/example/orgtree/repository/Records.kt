package ru.example.orgtree.repository

/** Внутренняя запись подразделения не зависит от структуры HTTP-ответа. */
data class DepartmentRecord(
    val id: Long,
    val code: String,
    val name: String,
    val description: String?,
    val parentId: Long?,
    val headId: Long?,
    val employeeCount: Int,
    val totalEmployeeCount: Int,
)

/** Результат JDBC-проекции сотрудника и его основного подразделения. */
data class EmployeeRecord(
    val id: Long,
    val fullName: String,
    val position: String,
    val email: String,
    val phone: String?,
    val departmentId: Long,
    val departmentCode: String,
    val departmentName: String,
)

/** Плоский узел рекурсивного запроса для последующей сборки дерева. */
data class EmployeeTreeRecord(val employee: EmployeeRecord, val parentId: Long?, val dottedManagerIds: List<Long>)

/** Запись связи с явно заданным направлением: подчинённый — руководитель. */
data class ReportingRecord(val employeeId: Long, val managerId: Long, val type: String, val comment: String?)

/** Внутренняя запись задачи отделена от API-модели. */
data class AssignmentRecord(val id: Long, val kind: String, val title: String, val description: String?)
