package ru.example.orgtree.service

import org.springframework.stereotype.Component
import ru.example.orgtree.api.*
import ru.example.orgtree.repository.*

/** Держит преобразования JDBC-записей в контрактные модели в одном месте. */
@Component
class ApiMapper {
    fun department(row: DepartmentRecord) = DepartmentSummary(row.id, row.code, row.name)

    fun employee(row: EmployeeRecord) = EmployeeSummary(
        row.id, row.fullName, row.position,
        DepartmentSummary(row.departmentId, row.departmentCode, row.departmentName),
    )

    fun assignment(row: AssignmentRecord) =
        Assignment(row.id, AssignmentKind.valueOf(row.kind), row.title, row.description)

    fun relation(row: ReportingRecord, employee: EmployeeRecord) =
        ReportingRelation(employee(employee), ReportingType.valueOf(row.type), row.comment)
}
