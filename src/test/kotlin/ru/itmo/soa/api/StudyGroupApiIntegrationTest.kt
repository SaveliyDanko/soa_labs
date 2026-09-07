package ru.itmo.soa.api

import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.itmo.soa.persistence.StudyGroupRepository

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StudyGroupApiIntegrationTest {
    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var repository: StudyGroupRepository

    @BeforeEach
    fun clean() = repository.deleteAll()

    @Test
    fun `supports CRUD and validation`() {
        val location = mvc.perform(
            post("/api/study-groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content(group("P3110", 25, "Anna")),
        )
            .andExpect(status().isCreated)
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.id").isNumber)
            .andExpect(jsonPath("$.creationDate").exists())
            .andReturn().response.getHeader("Location")!!

        mvc.perform(get(location))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("P3110"))

        mvc.perform(
            put(location)
                .contentType(MediaType.APPLICATION_JSON)
                .content(group("P3110 updated", 30, "Anna")),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.studentsCount").value(30))

        mvc.perform(
            post("/api/study-groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content(group("invalid", 0, "Admin")),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.violations.studentsCount").exists())

        mvc.perform(delete(location)).andExpect(status().isNoContent)
        mvc.perform(get(location)).andExpect(status().isNotFound)
    }

    @Test
    fun `filters sorts pages and runs special operations`() {
        create("Math A", 10, "Anna")
        create("Math B", 30, "Zoe")
        create("Physics", 20, "Mike")

        mvc.perform(
            get("/api/study-groups")
                .param("page", "0")
                .param("size", "1")
                .param("sort", "studentsCount,desc")
                .param("filter", "name:contains:math", "studentsCount:ge:10"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content", hasSize<Any>(1)))
            .andExpect(jsonPath("$.content[0].name").value("Math B"))
            .andExpect(jsonPath("$.totalElements").value(2))

        mvc.perform(get("/api/study-groups/group-admin/max"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.groupAdmin.name").value("Zoe"))

        mvc.perform(get("/api/study-groups/group-admin/count-greater").param("adminName", "Mike"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.count").value(1))

        mvc.perform(get("/api/study-groups/name/contains").param("substring", "math"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(2)))

        mvc.perform(get("/api/study-groups").param("filter", "unknown:eq:value"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `exposes OpenAPI and Swagger UI`() {
        mvc.perform(get("/openapi.yaml"))
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Type", containsString("application")))
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection)
    }

    private fun create(name: String, count: Long, admin: String) {
        mvc.perform(
            post("/api/study-groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content(group(name, count, admin)),
        ).andExpect(status().isCreated)
    }

    private fun group(name: String, count: Long, admin: String) = """
        {
          "name": "$name",
          "coordinates": {"x": 1.5, "y": -2.0},
          "studentsCount": $count,
          "formOfEducation": "FULL_TIME_EDUCATION",
          "semesterEnum": "THIRD",
          "groupAdmin": {
            "name": "$admin",
            "nationality": "USA",
            "location": {"x": 1, "y": 2.0, "z": 3.0}
          }
        }
    """.trimIndent()
}
