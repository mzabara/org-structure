package ru.example.orgtree.api.employee

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import org.springframework.web.bind.annotation.*
import ru.example.orgtree.api.AssignmentKind
import ru.example.orgtree.api.ReportFilter
import ru.example.orgtree.repository.DepartmentRepository
import ru.example.orgtree.service.EmployeeService

/** Валидирует параметры чтения сотрудников до вызова предметного сервиса. */
@RestController
@RequestMapping("/api/v1/employees")
class EmployeeController(private val employees: EmployeeService) {
    @GetMapping
    fun list(
        @RequestParam(required = false) @Size(min = 2, max = 100) q: String?,
        @RequestParam(required = false) departmentId: Long?,
        @RequestParam(defaultValue = "false") includeSubdepartments: Boolean,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
    ) = employees.page(q, departmentId, includeSubdepartments, page, size)

    @GetMapping("/tree")
    fun tree(
        @RequestParam(required = false) rootId: Long?,
        @RequestParam(defaultValue = "20") @Min(0) @Max(20) depth: Int,
    ) = employees.tree(rootId, depth)

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long) = employees.get(id)

    @GetMapping("/{id}/management-chain")
    fun chain(@PathVariable id: Long) = employees.managementChain(id)

    @GetMapping("/{id}/direct-reports")
    fun reports(@PathVariable id: Long, @RequestParam(defaultValue = "ALL") type: ReportFilter) =
        employees.directReports(id, type)

    @GetMapping("/{id}/assignments")
    fun assignments(@PathVariable id: Long, @RequestParam(required = false) kind: AssignmentKind?) =
        employees.assignments(id, kind)
}
