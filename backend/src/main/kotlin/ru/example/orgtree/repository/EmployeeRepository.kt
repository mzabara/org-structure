package ru.example.orgtree.repository

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet

/** Читает сотрудников и линейное дерево через JdbcClient без ORM и скрытых запросов. */
@Repository
class EmployeeRepository(private val jdbc: JdbcClient) {
    fun findById(id: Long): EmployeeRecord? = jdbc.sql(
        "$EMPLOYEE_SELECT where e.id = :id",
    ).param("id", id).query { rs, _ -> record(rs) }.optional().orElse(null)

    fun findAll(): List<EmployeeRecord> = jdbc.sql("$EMPLOYEE_SELECT order by e.full_name, e.id")
        .query { rs, _ -> record(rs) }.list()

    /** Обход учитывает только LINE; DOTTED агрегируется отдельно и не размножает узлы. */
    fun findTree(rootId: Long?, depth: Int): List<EmployeeTreeRecord> = jdbc.sql(
        """
        with recursive tree as (
            select e.id, 0 as level, array[e.id] as visited from employee e
            where (:defaultRoot and not exists (
                select 1 from reporting_line r where r.employee_id = e.id and r.line_type = 'LINE'
            )) or e.id = :rootId
            union all
            select r.employee_id, t.level + 1, t.visited || r.employee_id
            from reporting_line r join tree t on r.manager_id = t.id
            where r.line_type = 'LINE' and t.level < :depth and not r.employee_id = any(t.visited)
        )
        select e.*, d.code as department_code, d.name as department_name,
            (select manager_id from reporting_line where employee_id = e.id and line_type = 'LINE') as parent_id,
            array(select manager_id from reporting_line where employee_id = e.id and line_type = 'DOTTED'
                order by manager_id) as dotted_ids
        from tree t join employee e on e.id = t.id join department d on d.id = e.department_id
        order by e.full_name, e.id
        """.trimIndent(),
    ).param("rootId", rootId ?: 0L).param("defaultRoot", rootId == null).param("depth", depth).query { rs, _ ->
        val ids = rs.getArray("dotted_ids")
        try {
            EmployeeTreeRecord(
                record(rs), rs.getObject("parent_id", java.lang.Long::class.java)?.toLong(),
                (ids.array as Array<*>).map { (it as Number).toLong() },
            )
        } finally {
            ids.free()
        }
    }.list()

    fun count(q: String?, departmentId: Long?, includeSubdepartments: Boolean): Long =
        filtered("select count(*) from employee e", q, departmentId, includeSubdepartments)
            .query(Long::class.java).single()

    fun findPage(q: String?, departmentId: Long?, includeSubdepartments: Boolean, page: Int, size: Int) =
        filtered(EMPLOYEE_SELECT, q, departmentId, includeSubdepartments, paged = true)
            .param("limit", size).param("offset", page.toLong() * size)
            .query { rs, _ -> record(rs) }.list()

    /**
     * Поддерево отделов вычисляется в БД; strpos ищет буквальную подстроку, а не LIKE-шаблон.
     * SQL-фрагменты здесь константны, пользовательские значения передаются только параметрами.
     */
    private fun filtered(
        select: String,
        q: String?,
        departmentId: Long?,
        includeSubdepartments: Boolean,
        paged: Boolean = false,
    ): JdbcClient.StatementSpec = jdbc.sql(
        """
        with recursive sub as (
            select id, 0 as level, array[id] as visited from department where id = :departmentId
            union all
            select d.id, s.level + 1, s.visited || d.id from department d join sub s on d.parent_id = s.id
            where :includeSubdepartments and s.level < :maxDepth and not d.id = any(s.visited)
        )
        $select
        where (:q = '' or strpos(lower(e.full_name), lower(:q)) > 0 or strpos(lower(e.position), lower(:q)) > 0)
          and (:allDepartments or e.department_id in (select id from sub))
        ${if (paged) "order by e.full_name, e.id limit :limit offset :offset" else ""}
        """.trimIndent(),
    ).param("departmentId", departmentId ?: 0L).param("allDepartments", departmentId == null).param("q", q ?: "")
        .param("includeSubdepartments", includeSubdepartments)
        .param("maxDepth", DepartmentRepository.MAX_TREE_DEPTH)

    /** Цепочка идёт вверх от непосредственного руководителя; корень даёт пустой список. */
    fun findManagementChain(id: Long): List<EmployeeRecord> = jdbc.sql(
        """
        with recursive chain as (
            select manager_id as id, 1 as level, array[employee_id, manager_id] as visited
            from reporting_line where employee_id = :id and line_type = 'LINE'
            union all
            select r.manager_id, c.level + 1, c.visited || r.manager_id
            from reporting_line r join chain c on r.employee_id = c.id
            where r.line_type = 'LINE' and c.level < :maxDepth and not r.manager_id = any(c.visited)
        )
        $EMPLOYEE_SELECT join chain c on c.id = e.id order by c.level
        """.trimIndent(),
    ).param("id", id).param("maxDepth", DepartmentRepository.MAX_TREE_DEPTH)
        .query { rs, _ -> record(rs) }.list()

    private fun record(rs: ResultSet) = EmployeeRecord(
        rs.getLong("id"), rs.getString("full_name"), rs.getString("position"), rs.getString("email"),
        rs.getString("phone"), rs.getLong("department_id"), rs.getString("department_code"),
        rs.getString("department_name"),
    )

    companion object {
        private const val EMPLOYEE_SELECT =
            "select e.*, d.code as department_code, d.name as department_name " +
                "from employee e join department d on d.id = e.department_id"
    }
}
