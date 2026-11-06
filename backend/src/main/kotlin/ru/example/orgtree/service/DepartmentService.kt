package ru.example.orgtree.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.example.orgtree.api.Department
import ru.example.orgtree.api.DepartmentTreeNode
import ru.example.orgtree.error.EntityNotFoundException
import ru.example.orgtree.repository.DepartmentRepository
import ru.example.orgtree.repository.EmployeeRepository

/** Собирает дерево и карточку отделов в согласованной read-only транзакции. */
@Service
@Transactional(readOnly = true)
class DepartmentService(
    private val departments: DepartmentRepository,
    private val employees: EmployeeRepository,
    private val mapper: ApiMapper,
) {
    fun tree(rootId: Long?, depth: Int): DepartmentTreeNode {
        val rows = departments.findTree(rootId, depth)
        val root = rows.firstOrNull { if (rootId == null) it.parentId == null else it.id == rootId }
            ?: throw EntityNotFoundException("Подразделение", rootId)
        val children = rows.groupBy { it.parentId }
        val heads = employees.findAll().associateBy { it.id }
        fun build(row: ru.example.orgtree.repository.DepartmentRecord): DepartmentTreeNode =
            DepartmentTreeNode(
                row.id, row.code, row.name, heads[row.headId]?.let(mapper::employee),
                row.employeeCount, row.totalEmployeeCount, children[row.id].orEmpty().map(::build),
            )
        return build(root)
    }

    fun get(id: Long): Department {
        val row = requireDepartment(id)
        val all = departments.findAll()
        return Department(
            row.id, row.code, row.name, row.description,
            all.firstOrNull { it.id == row.parentId }?.let(mapper::department),
            row.headId?.let(employees::findById)?.let(mapper::employee),
            all.filter { it.parentId == id }.map(mapper::department),
            row.employeeCount, row.totalEmployeeCount,
        )
    }

    fun requireDepartment(id: Long) = departments.findById(id)
        ?: throw EntityNotFoundException("Подразделение", id)
}
