package ru.example.orgtree.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.example.orgtree.api.*
import ru.example.orgtree.repository.DepartmentRepository
import ru.example.orgtree.repository.EmployeeRepository

/** Возвращает ограниченные группы результатов с путями в соответствующих деревьях. */
@Service
@Transactional(readOnly = true)
class SearchService(
    private val departments: DepartmentRepository,
    private val employees: EmployeeRepository,
    private val mapper: ApiMapper,
) {
    fun search(q: String, limit: Int): SearchResult {
        val departmentRows = departments.findAll()
        val departmentParents = departmentRows.associate { it.id to it.parentId }
        val employeeParents = employees.findTree(null, DepartmentRepository.MAX_TREE_DEPTH)
            .associate { it.employee.id to it.parentId }
        return SearchResult(
            employees.findPage(q, null, false, 0, limit).map {
                EmployeeMatch(mapper.employee(it), path(it.id, employeeParents))
            },
            departmentRows.filter { it.name.contains(q, ignoreCase = true) }.take(limit).map {
                DepartmentMatch(mapper.department(it), path(it.id, departmentParents))
            },
        )
    }

    private fun path(id: Long, parents: Map<Long, Long?>): List<Long> {
        val visited = linkedSetOf<Long>()
        var current: Long? = id
        while (current != null && current !in visited && visited.size <= DepartmentRepository.MAX_TREE_DEPTH) {
            visited.add(current)
            current = parents[current]
        }
        return visited.toList().asReversed()
    }
}
