package ru.example.orgtree.repository

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

/** Читает явные связи подчинения и упорядоченные задачи сотрудника. */
@Repository
class ReportingRepository(private val jdbc: JdbcClient) {
    fun findRelations(id: Long): List<ReportingRecord> = jdbc.sql(
        """
        select employee_id, manager_id, line_type, comment from reporting_line
        where employee_id = :id or manager_id = :id
        order by line_type, employee_id, manager_id
        """.trimIndent(),
    ).param("id", id).query { rs, _ ->
        ReportingRecord(rs.getLong("employee_id"), rs.getLong("manager_id"), rs.getString("line_type"), rs.getString("comment"))
    }.list()

    fun findAssignments(id: Long, kind: String?): List<AssignmentRecord> = jdbc.sql(
        """
        select id, kind, title, description from assignment
        where employee_id = :id and (:kind = '' or kind = :kind)
        order by sort_order, id
        """.trimIndent(),
    ).param("id", id).param("kind", kind ?: "").query { rs, _ ->
        AssignmentRecord(rs.getLong("id"), rs.getString("kind"), rs.getString("title"), rs.getString("description"))
    }.list()
}
