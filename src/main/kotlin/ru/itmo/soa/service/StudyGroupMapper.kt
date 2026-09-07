package ru.itmo.soa.service

import org.springframework.stereotype.Component
import ru.itmo.soa.api.dto.CoordinatesDto
import ru.itmo.soa.api.dto.LocationDto
import ru.itmo.soa.api.dto.PersonDto
import ru.itmo.soa.api.dto.StudyGroupRequest
import ru.itmo.soa.api.dto.StudyGroupResponse
import ru.itmo.soa.domain.Coordinates
import ru.itmo.soa.domain.Location
import ru.itmo.soa.domain.Person
import ru.itmo.soa.domain.StudyGroup
import java.util.Date

@Component
class StudyGroupMapper {
    fun toEntity(request: StudyGroupRequest): StudyGroup {
        val coordinates = requireNotNull(request.coordinates)
        return StudyGroup(
            name = request.name,
            coordinates = Coordinates(requireNotNull(coordinates.x), requireNotNull(coordinates.y)),
            studentsCount = requireNotNull(request.studentsCount),
            formOfEducation = requireNotNull(request.formOfEducation),
            semesterEnum = request.semesterEnum,
            groupAdmin = request.groupAdmin?.toEntity(),
        )
    }

    fun update(target: StudyGroup, request: StudyGroupRequest) {
        target.updateFrom(toEntity(request))
    }

    fun toResponse(group: StudyGroup) = StudyGroupResponse(
        id = requireNotNull(group.id),
        name = group.name,
        coordinates = CoordinatesDto(group.coordinates.x, group.coordinates.y),
        creationDate = Date(requireNotNull(group.creationDate).time),
        studentsCount = group.studentsCount,
        formOfEducation = group.formOfEducation,
        semesterEnum = group.semesterEnum,
        groupAdmin = group.groupAdmin?.toDto(),
    )

    private fun PersonDto.toEntity(): Person {
        val locationEntity = location?.let {
            Location(requireNotNull(it.x), requireNotNull(it.y), requireNotNull(it.z))
        }
        return Person(name, birthday, hairColor, requireNotNull(nationality), locationEntity)
    }

    private fun Person.toDto(): PersonDto {
        val locationDto = location?.let {
            LocationDto(it.x, requireNotNull(it.y), requireNotNull(it.z))
        }
        return PersonDto(name, birthday, hairColor, requireNotNull(nationality), locationDto)
    }
}
