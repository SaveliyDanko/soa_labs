package ru.itmo.soa.service

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.itmo.soa.api.dto.PageResponse
import ru.itmo.soa.api.dto.StudyGroupRequest
import ru.itmo.soa.api.dto.StudyGroupResponse
import ru.itmo.soa.domain.StudyGroup
import ru.itmo.soa.persistence.StudyGroupRepository
import java.util.Locale

@Service
@Transactional(readOnly = true)
class StudyGroupService(
    private val repository: StudyGroupRepository,
    private val mapper: StudyGroupMapper,
) {
    @Transactional
    fun create(request: StudyGroupRequest): StudyGroupResponse =
        mapper.toResponse(repository.save(mapper.toEntity(request)))

    fun get(id: Int): StudyGroupResponse = mapper.toResponse(find(id))

    @Transactional
    fun update(id: Int, request: StudyGroupRequest): StudyGroupResponse {
        val group = find(id)
        mapper.update(group, request)
        return mapper.toResponse(repository.save(group))
    }

    @Transactional
    fun delete(id: Int) = repository.delete(find(id))

    fun list(page: Int, size: Int, sorts: List<String>?, filters: List<String>?): PageResponse<StudyGroupResponse> {
        val pageRequest = PageRequest.of(page, size, parseSort(sorts))
        val result = repository.findAll(StudyGroupSpecifications.fromFilters(filters), pageRequest)
            .map(mapper::toResponse)
        return PageResponse(result.content, result.number, result.size, result.totalElements, result.totalPages)
    }

    fun maxAdmin(): StudyGroupResponse {
        val hasAdmin = Specification<StudyGroup> { root, _, cb ->
            cb.isNotNull(root.get<Any>("groupAdmin").get<String>("name"))
        }
        return repository.findAll(
            hasAdmin,
            PageRequest.of(0, 1, Sort.by(Sort.Order.desc("groupAdmin.name"), Sort.Order.asc("id"))),
        ).firstOrNull()?.let(mapper::toResponse)
            ?: throw NotFoundException("NO_GROUP_WITH_ADMIN", "Нет групп с назначенным администратором")
    }

    fun countAdminGreaterThan(adminName: String): Long {
        val greater = Specification<StudyGroup> { root, _, cb ->
            cb.greaterThan(root.get<Any>("groupAdmin").get("name"), adminName)
        }
        return repository.count(greater)
    }

    fun nameContains(substring: String): List<StudyGroupResponse> {
        val contains = Specification<StudyGroup> { root, _, cb ->
            cb.like(cb.lower(root.get("name")), "%${substring.lowercase(Locale.ROOT)}%")
        }
        return repository.findAll(contains, Sort.by("id")).map(mapper::toResponse)
    }

    private fun find(id: Int): StudyGroup = repository.findById(id)
        .orElseThrow {
            NotFoundException(
                "STUDY_GROUP_NOT_FOUND",
                "Учебная группа с id=$id не найдена",
                mapOf("id" to id.toString()),
            )
        }

    private fun parseSort(values: List<String>?): Sort {
        if (values.isNullOrEmpty()) return Sort.by("id").ascending()
        return Sort.by(values.map { value ->
            val parts = value.split(',', limit = 3)
            val field = parts[0]
            if (field !in StudyGroupSpecifications.fields) {
                throw InvalidQueryException(
                    "INVALID_SORT_FIELD",
                    "Неизвестное поле сортировки: $field",
                    mapOf("field" to field),
                )
            }
            if (parts.size > 2 || (parts.size == 2 &&
                        !parts[1].equals("asc", true) && !parts[1].equals("desc", true))) {
                throw InvalidQueryException(
                    "INVALID_SORT_FORMAT",
                    "Сортировка должна иметь формат field,asc или field,desc",
                    mapOf("sort" to value),
                )
            }
            val direction = if (parts.size == 1) Sort.Direction.ASC else Sort.Direction.fromString(parts[1])
            Sort.Order(direction, field)
        })
    }
}
