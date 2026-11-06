package ru.example.orgtree.repository

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet

/** Выполняет параметризованные запросы к иерархии отделов, не изменяя данные. */
@Repository
class DepartmentRepository(private val jdbc: JdbcClient) {
    /**
     * Два независимых обхода: tree ограничивает выдачу, descendants считает весь штат.
     * Ограничение глубины и массив посещённых id защищают от повреждённых циклов.
     */
    fun findTree(rootId: Long?, depth: Int): List<DepartmentRecord> = jdbc.sql(
        """
        with recursive tree as (
            select id, 0 as level, array[id] as visited from department
            where (:defaultRoot and parent_id is null) or id = :rootId
            union all
            select d.id, t.level + 1, t.visited || d.id
            from department d join tree t on d.parent_id = t.id
            where t.level < :depth and not d.id = any(t.visited)
        ), descendants as (
            select id as ancestor_id, id, 0 as level, array[id] as visited from department
            union all
            select s.ancestor_id, d.id, s.level + 1, s.visited || d.id
            from department d join descendants s on d.parent_id = s.id
            where s.level < :maxDepth and not d.id = any(s.visited)
        )
        select d.*,
            (select count(*) from employee e where e.department_id = d.id) as employee_count,
            (select count(*) from employee e join descendants s on s.id = e.department_id
                where s.ancestor_id = d.id) as total_employee_count
        from department d join tree t on t.id = d.id
        order by d.name, d.id
        """.trimIndent(),
    ).param("rootId", rootId ?: 0L).param("defaultRoot", rootId == null)
        .param("depth", depth).param("maxDepth", MAX_TREE_DEPTH)
        .query { rs, _ -> record(rs) }.list()

    fun findById(id: Long): DepartmentRecord? = findTree(id, 0).singleOrNull()

    fun findAll(): List<DepartmentRecord> = findTree(null, MAX_TREE_DEPTH)

    private fun record(rs: ResultSet) = DepartmentRecord(
        rs.getLong("id"), rs.getString("code"), rs.getString("name"), rs.getString("description"),
        rs.getObject("parent_id", java.lang.Long::class.java)?.toLong(),
        rs.getObject("head_employee_id", java.lang.Long::class.java)?.toLong(),
        rs.getInt("employee_count"), rs.getInt("total_employee_count"),
    )

    companion object {
        // Максимальная глубина из контракта ограничивает все рекурсивные обходы.
        const val MAX_TREE_DEPTH = 20
    }
}
