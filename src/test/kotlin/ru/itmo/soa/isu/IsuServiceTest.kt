package ru.itmo.soa.isu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import ru.itmo.soa.api.dto.CoordinatesDto
import ru.itmo.soa.api.dto.StudyGroupRequest
import ru.itmo.soa.api.dto.StudyGroupResponse
import ru.itmo.soa.domain.FormOfEducation
import java.util.Date

class IsuServiceTest {
    private val client = RecordingClient()
    private val service = IsuService(client)

    @Test
    fun `expel all deletes group through first service`() {
        service.expelAll(42)
        assertEquals(42, client.deletedId)
    }

    @Test
    fun `expel endpoint returns action result`() {
        val response = IsuController(service).expelAll(42)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("ALL_STUDENTS_EXPELLED", response.body?.code)
        assertEquals(42, response.body?.resourceId)
    }

    @Test
    fun `change form reads then updates through first service`() {
        val result = service.changeEducationForm(42, FormOfEducation.DISTANCE_EDUCATION)

        assertEquals(42, client.readId)
        assertEquals(42, client.updatedId)
        assertEquals(FormOfEducation.DISTANCE_EDUCATION, client.updatedRequest?.formOfEducation)
        assertEquals(FormOfEducation.DISTANCE_EDUCATION, result.formOfEducation)
    }

    private class RecordingClient : StudyGroupsClient {
        var deletedId: Int? = null
        var readId: Int? = null
        var updatedId: Int? = null
        var updatedRequest: StudyGroupRequest? = null

        override fun get(id: Int): StudyGroupResponse {
            readId = id
            return response(FormOfEducation.FULL_TIME_EDUCATION)
        }

        override fun update(id: Int, request: StudyGroupRequest): StudyGroupResponse {
            updatedId = id
            updatedRequest = request
            return response(requireNotNull(request.formOfEducation))
        }

        override fun delete(id: Int) {
            deletedId = id
        }

        private fun response(form: FormOfEducation) = StudyGroupResponse(
            id = 42,
            name = "P3110",
            coordinates = CoordinatesDto(1f, 2f),
            creationDate = Date(),
            studentsCount = 20,
            formOfEducation = form,
            semesterEnum = null,
            groupAdmin = null,
        )
    }
}
