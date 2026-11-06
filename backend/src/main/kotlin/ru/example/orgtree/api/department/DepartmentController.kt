package ru.example.orgtree.api.department

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.web.bind.annotation.*
import ru.example.orgtree.repository.DepartmentRepository
import ru.example.orgtree.service.DepartmentService
import ru.example.orgtree.service.EmployeeService

/** Публикует только чтение отделов по неизменяемому OpenAPI-контракту. */
@RestController
@RequestMapping("/api/v1/departments")
class DepartmentController(private val departments: DepartmentService, private val employees: EmployeeService) {
    @GetMapping("/tree")
    fun tree(
        @RequestParam(required = false) rootId: Long?,
        @RequestParam(defaultValue = "20") @Min(0) @Max(20) depth: Int,
    ) = departments.tree(rootId, depth)

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long) = departments.get(id)

    @GetMapping("/{id}/employees")
    fun employees(
        @PathVariable id: Long,
        @RequestParam(defaultValue = "false") includeSubdepartments: Boolean,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
    ) = employees.departmentPage(id, includeSubdepartments, page, size)
}
