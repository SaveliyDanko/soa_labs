package ru.itmo.soa.api

import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import org.springframework.http.ResponseEntity
import org.springframework.util.MultiValueMap
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import ru.itmo.soa.api.dto.PageResponse
import ru.itmo.soa.api.dto.StudyGroupRequest
import ru.itmo.soa.api.dto.StudyGroupResponse
import ru.itmo.soa.service.StudyGroupService

@Validated
@RestController
@RequestMapping("/api/study-groups")
class StudyGroupController(private val service: StudyGroupService) {
    @PostMapping
    fun create(@Valid @RequestBody request: StudyGroupRequest): ResponseEntity<StudyGroupResponse> {
        val response = service.create(request)
        val location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}").buildAndExpand(response.id).toUri()
        return ResponseEntity.created(location).body(response)
    }

    @GetMapping("/{id}")
    fun get(@PathVariable @Positive id: Int): StudyGroupResponse = service.get(id)

    @PutMapping("/{id}")
    fun update(
        @PathVariable @Positive id: Int,
        @Valid @RequestBody request: StudyGroupRequest,
    ): StudyGroupResponse = service.update(id, request)

    @DeleteMapping("/{id}")
    fun delete(@PathVariable @Positive id: Int): ResponseEntity<Void> {
        service.delete(id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
        @RequestParam parameters: MultiValueMap<String, String>,
    ): PageResponse<StudyGroupResponse> =
        service.list(page, size, parameters["sort"], parameters["filter"])

    @GetMapping("/group-admin/max")
    fun maxAdmin(): StudyGroupResponse = service.maxAdmin()

    @GetMapping("/group-admin/count-greater")
    fun countAdminGreaterThan(@RequestParam @NotBlank adminName: String): Map<String, Long> =
        mapOf("count" to service.countAdminGreaterThan(adminName))

    @GetMapping("/name/contains")
    fun nameContains(@RequestParam @NotBlank substring: String): List<StudyGroupResponse> =
        service.nameContains(substring)
}
