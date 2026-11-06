package ru.example.orgtree.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.example.orgtree.api.*
import ru.example.orgtree.error.EntityNotFoundException
import ru.example.orgtree.repository.EmployeeRepository
import ru.example.orgtree.repository.EmployeeTreeRecord
import ru.example.orgtree.repository.ReportingRecord
import ru.example.orgtree.repository.ReportingRepository

/** Отдаёт карточки и линейное дерево, сохраняя DOTTED как отдельные связи. */
@Service
@Transactional(readOnly = true)
class EmployeeService(
    private val employees: EmployeeRepository,
    private val reporting: ReportingRepository,
    private val departments: DepartmentService,
    private val mapper: ApiMapper,
) {
    fun tree(rootId: Long?, depth: Int): EmployeeTreeNode {
        val rows = employees.findTree(rootId, depth)
        val root = rows.firstOrNull { if (rootId == null) it.parentId == null else it.employee.id == rootId }
            ?: throw EntityNotFoundException("Сотрудник", rootId)
        val children = rows.groupBy { it.parentId }
        fun build(row: EmployeeTreeRecord): EmployeeTreeNode {
            val summary = mapper.employee(row.employee)
            return EmployeeTreeNode(
                summary.id, summary.fullName, summary.position, summary.department,
                row.dottedManagerIds, children[summary.id].orEmpty().map(::build),
            )
        }
        return build(root)
    }

    fun get(id: Long): Employee {
        val row = requireEmployee(id)
        val relations = reporting.findRelations(id)
        val people = employees.findAll().associateBy { it.id }
        val lineManagerId = relations.firstOrNull { it.employeeId == id && it.type == "LINE" }?.managerId
        return Employee(
            row.id, row.fullName, row.position, row.email, row.phone, mapper.employee(row).department,
            people[lineManagerId]?.let(mapper::employee),
            relations.filter { it.employeeId == id && it.type == "DOTTED" }
                .map { mapper.relation(it, people.getValue(it.managerId)) },
            reportRelations(id, relations),
            reporting.findAssignments(id, null).map(mapper::assignment),
        )
    }

    fun page(q: String?, departmentId: Long?, includeSubdepartments: Boolean, page: Int, size: Int): EmployeePage {
        val count = employees.count(q, departmentId, includeSubdepartments)
        return EmployeePage(
            employees.findPage(q, departmentId, includeSubdepartments, page, size).map(mapper::employee),
            page, size, count, ((count + size - 1) / size).toInt(),
        )
    }

    fun departmentPage(id: Long, includeSubdepartments: Boolean, page: Int, size: Int): EmployeePage {
        departments.requireDepartment(id)
        return page(null, id, includeSubdepartments, page, size)
    }

    fun managementChain(id: Long): List<EmployeeSummary> {
        requireEmployee(id)
        return employees.findManagementChain(id).map(mapper::employee)
    }

    fun directReports(id: Long, type: ReportFilter): List<ReportingRelation> {
        requireEmployee(id)
        val relations = reporting.findRelations(id).filter { type == ReportFilter.ALL || it.type == type.name }
        return reportRelations(id, relations)
    }

    fun assignments(id: Long, kind: AssignmentKind?): List<Assignment> {
        requireEmployee(id)
        return reporting.findAssignments(id, kind?.name).map(mapper::assignment)
    }

    private fun reportRelations(id: Long, relations: List<ReportingRecord>): List<ReportingRelation> {
        val people = employees.findAll().associateBy { it.id }
        return relations.filter { it.managerId == id }
            .map { mapper.relation(it, people.getValue(it.employeeId)) }
            .sortedWith(compareBy({ it.employee.fullName }, { it.employee.id }))
    }

    private fun requireEmployee(id: Long) = employees.findById(id) ?: throw EntityNotFoundException("Сотрудник", id)
}
