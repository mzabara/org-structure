package ru.example.orgtree.integration

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.io.File

/** Проверяет контракт и предметные инварианты на неизменённых Flyway-миграциях PostgreSQL. */
@Testcontainers
@SpringBootTest(properties = ["app.security.api-keys=integration-only-key"])
@AutoConfigureMockMvc
class ApiIntegrationTest @Autowired constructor(
    private val mvc: MockMvc,
    private val json: ObjectMapper,
    private val jdbc: JdbcClient,
) {
    private val contract = ObjectMapper(YAMLFactory()).readTree(File(System.getProperty("orgtree.contract")))
    private val schemas = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)

    @Test
    fun `все GET операции соответствуют исходному OpenAPI`() {
        val requests = mapOf(
            "/departments/tree" to "/departments/tree",
            "/departments/{id}" to "/departments/5",
            "/departments/{id}/employees" to "/departments/2/employees?includeSubdepartments=true",
            "/employees" to "/employees?page=0&size=5",
            "/employees/tree" to "/employees/tree",
            "/employees/{id}" to "/employees/13",
            "/employees/{id}/management-chain" to "/employees/11/management-chain",
            "/employees/{id}/direct-reports" to "/employees/19/direct-reports?type=DOTTED",
            "/employees/{id}/assignments" to "/employees/12/assignments?kind=TASK",
            "/search" to "/search?q=поддерж",
        )
        requests.forEach { (path, request) -> validate(request(request), path, 200) }
        assertThat(requests.keys).containsExactlyInAnyOrderElementsOf(contract["paths"].fieldNames().asSequence().toList())
    }

    @Test
    fun `миграции и деревья сохраняют все инварианты демо набора`() {
        assertThat(jdbc.sql("select count(*) from flyway_schema_history where success").query(Int::class.java).single()).isEqualTo(2)
        assertThat(jdbc.sql("select count(*) from department").query(Int::class.java).single()).isEqualTo(13)
        assertThat(jdbc.sql("select count(*) from employee").query(Int::class.java).single()).isEqualTo(31)
        assertThat(jdbc.sql("select count(*) from reporting_line where line_type = 'LINE'").query(Int::class.java).single()).isEqualTo(30)
        assertThat(jdbc.sql("select count(*) from reporting_line where line_type = 'DOTTED'").query(Int::class.java).single()).isEqualTo(7)
        val departments = request("/departments/tree")
        assertThat(departments["name"].asText()).isEqualTo("Генеральная дирекция")
        assertThat(departments["children"].size()).isEqualTo(3)
        assertThat(departments["totalEmployeeCount"].asInt()).isEqualTo(31)
        assertThat(flatten(departments).map { it["id"].asLong() }.toSet()).hasSize(13)
        val tree = request("/employees/tree")
        assertThat(tree["id"].asLong()).isEqualTo(1)
        assertThat(flatten(tree).map { it["id"].asLong() }.toSet()).hasSize(31)
        assertThat(flatten(tree).single { it["id"].asInt() == 13 }["dottedManagerIds"].map { it.asInt() }).containsExactly(19)
    }

    @Test
    fun `глубина поддерево и счётчики согласованы`() {
        for (resource in listOf("departments", "employees")) {
            assertThat(request("/$resource/tree?depth=0")["children"].size()).isZero()
            assertThat(request("/$resource/tree?depth=1")["children"].all { it["children"].isEmpty }).isTrue()
            assertThat(request("/$resource/tree?rootId=5&depth=0")["id"].asInt()).isEqualTo(5)
        }
        val development = request("/departments/tree?rootId=2&depth=0")
        assertThat(development["totalEmployeeCount"].asInt()).isEqualTo(14)
        val backend = request("/departments/5")
        assertThat(backend["employeeCount"].asInt()).isEqualTo(4)
        assertThat(backend["head"]["id"].asInt()).isEqualTo(5)
        assertThat(backend["parent"]["id"].asInt()).isEqualTo(2)
        assertThat(request("/departments/tree?rootId=2")["children"].size()).isEqualTo(4)
    }

    @Test
    fun `карточки связи цепочки и пустые назначения корректны`() {
        val employee = request("/employees/11")
        assertThat(employee["lineManager"]["id"].asInt()).isEqualTo(9)
        assertThat(employee["department"]["code"].asText()).isEqualTo("DEV-BE")
        assertThat(request("/employees/11/management-chain").map { it["id"].asInt() }).containsExactly(9, 5, 2, 1)
        assertThat(request("/employees/1/management-chain").isEmpty).isTrue()
        assertThat(request("/employees/23/assignments").isEmpty).isTrue()
        assertThat(request("/employees/13")["dottedManagers"][0]["comment"].asText()).contains("Личный кабинет клиента")
        assertThat(request("/employees/19/direct-reports?type=DOTTED").map { it["employee"]["id"].asInt() })
            .containsExactlyInAnyOrder(13, 15, 29)
        assertThat(request("/employees/12/assignments?kind=TASK").all { it["kind"].asText() == "TASK" }).isTrue()
    }

    @Test
    fun `пагинация фильтры поиск и пути до корня работают без учёта регистра`() {
        val first = request("/employees?size=7")
        assertThat(first["totalItems"].asInt()).isEqualTo(31)
        assertThat(first["totalPages"].asInt()).isEqualTo(5)
        assertThat(first["items"].size()).isEqualTo(7)
        assertThat(request("/employees?page=99")["items"].isEmpty).isTrue()
        assertThat(request("/employees?page=2147483647&size=100")["items"].isEmpty).isTrue()
        assertThat(request("/employees?departmentId=0")["totalItems"].asInt()).isZero()
        assertThat(request("/employees?departmentId=999")["totalItems"].asInt()).isZero()
        val sub = request("/departments/2/employees?includeSubdepartments=true&size=100")
        assertThat(sub["totalItems"].asInt()).isEqualTo(14)
        assertThat(sub["items"].map { it["department"]["code"].asText() })
            .contains("DEV", "DEV-BE", "DEV-FE", "DEV-QA", "DEV-OPS")
        assertThat(request("/employees?q=РАЗРАБОТЧИК")["totalItems"].asInt()).isEqualTo(6)
        assertThat(request("/employees?q=%25%25")["totalItems"].asInt()).isZero()
        val found = request("/search?q=Белова")["employees"][0]
        assertThat(found["pathIds"].map { it.asInt() }).containsExactly(1, 2, 6, 12)
        val support = request("/search?q=поддерж")
        assertThat(support["employees"].isEmpty).isFalse()
        assertThat(support["departments"][0]["department"]["id"].asInt()).isEqualTo(12)
        assertThat(support["departments"][0]["pathIds"].map { it.asInt() }).containsExactly(1, 4, 12)
        assertThat(request("/search?q=ов&limit=1")["employees"].size()).isLessThanOrEqualTo(1)
    }

    @Test
    fun `неверные параметры возвращают 400 problem json`() {
        val invalid = listOf(
            "/employees?q=а", "/employees?q=", "/employees?q=${"а".repeat(101)}",
            "/employees?size=0", "/employees?size=500", "/employees?page=-1", "/employees?page=text",
            "/employees?departmentId=oops", "/employees?includeSubdepartments=maybe",
            "/employees/tree?depth=-1", "/employees/tree?depth=21", "/employees/tree?rootId=foo",
            "/departments/tree?depth=21", "/departments/5/employees?size=101",
            "/departments/5/employees?includeSubdepartments=maybe", "/employees/foo",
            "/employees/1/direct-reports?type=INVALID", "/employees/1/assignments?kind=INVALID",
            "/search", "/search?q=а", "/search?q=ов&limit=0", "/search?q=ов&limit=51",
        )
        invalid.forEach { request ->
            val result = mvc.perform(get("/api/v1$request").header("X-API-Key", KEY)).andReturn().response
            assertThat(result.status).describedAs(request).isEqualTo(400)
            assertThat(result.contentType).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE)
            validateProblem(json.readTree(result.contentAsByteArray), 400)
        }
    }

    @Test
    fun `отсутствующие сущности и неверные ключи возвращают контрактные ошибки`() {
        listOf(
            "/departments/999", "/departments/tree?rootId=0", "/departments/999/employees",
            "/employees/tree?rootId=0", "/employees/999", "/employees/999/management-chain",
            "/employees/999/direct-reports", "/employees/999/assignments",
        ).forEach { validateProblem(request(it, 404), 404) }
        listOf(null, "wrong", "").forEach { key ->
            val query = get("/api/v1/employees")
            if (key != null) query.header("X-API-Key", key)
            val result = mvc.perform(query).andReturn().response
            assertThat(result.status).isEqualTo(401)
            assertThat(result.contentType).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE)
            validateProblem(json.readTree(result.contentAsByteArray), 401)
        }
        assertThat(mvc.perform(get("/actuator/health")).andReturn().response.status).isEqualTo(200)
    }

    private fun request(path: String, status: Int = 200): JsonNode {
        val result = mvc.perform(get("/api/v1$path").header("X-API-Key", KEY)).andReturn().response
        assertThat(result.status).describedAs("$path: ${result.contentAsString}").isEqualTo(status)
        return json.readTree(result.contentAsByteArray)
    }

    private fun validate(value: JsonNode, path: String, status: Int) {
        val schema = contract["paths"][path]["get"]["responses"][status.toString()]["content"]["application/json"]["schema"]
        val root = schema.deepCopy<ObjectNode>()
        root.set<JsonNode>("components", contract["components"])
        assertThat(schemas.getSchema(root).validate(value)).describedAs(path).isEmpty()
    }

    private fun validateProblem(value: JsonNode, status: Int) {
        val root = contract["components"]["schemas"]["Problem"]
        assertThat(schemas.getSchema(root).validate(value)).isEmpty()
        assertThat(value["status"].asInt()).isEqualTo(status)
        assertThat(value["title"].asText()).containsPattern("[А-Яа-я]")
    }

    private fun flatten(node: JsonNode): List<JsonNode> =
        listOf(node) + node["children"].flatMap(::flatten)

    companion object {
        private const val KEY = "integration-only-key"

        @Container
        @JvmField
        val postgres = PostgreSQLContainer("postgres:16")

        @JvmStatic
        @DynamicPropertySource
        fun datasource(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
}
