package ru.itmo.soa.api.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import ru.itmo.soa.domain.Color
import ru.itmo.soa.domain.Country
import ru.itmo.soa.domain.FormOfEducation
import ru.itmo.soa.domain.Semester
import java.time.Instant
import java.time.ZonedDateTime
import java.util.Date

data class CoordinatesDto(
    @field:NotNull val x: Float?,
    @field:NotNull val y: Float?,
)

data class LocationDto(
    @field:NotNull val x: Long?,
    @field:NotNull val y: Double?,
    @field:NotNull val z: Double?,
)

data class PersonDto(
    @field:NotBlank val name: String,
    val birthday: ZonedDateTime? = null,
    val hairColor: Color? = null,
    @field:NotNull val nationality: Country?,
    @field:Valid val location: LocationDto? = null,
)

data class StudyGroupRequest(
    @field:NotBlank val name: String,
    @field:NotNull @field:Valid val coordinates: CoordinatesDto?,
    @field:NotNull @field:Positive val studentsCount: Long?,
    @field:NotNull val formOfEducation: FormOfEducation?,
    val semesterEnum: Semester? = null,
    @field:Valid val groupAdmin: PersonDto? = null,
)

data class StudyGroupResponse(
    val id: Int,
    val name: String,
    val coordinates: CoordinatesDto,
    val creationDate: Date,
    val studentsCount: Long,
    val formOfEducation: FormOfEducation,
    val semesterEnum: Semester?,
    val groupAdmin: PersonDto?,
) {
    fun toRequest(newForm: FormOfEducation) = StudyGroupRequest(
        name = name,
        coordinates = coordinates,
        studentsCount = studentsCount,
        formOfEducation = newForm,
        semesterEnum = semesterEnum,
        groupAdmin = groupAdmin,
    )
}

data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

data class ErrorResponse(
    val timestamp: Instant,
    val status: Int,
    val error: String,
    val message: String,
    val path: String,
    val violations: Map<String, String>,
)
