package ru.itmo.soa.isu

import jakarta.validation.constraints.Positive
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ru.itmo.soa.api.dto.StudyGroupResponse
import ru.itmo.soa.domain.FormOfEducation

@Validated
@RestController
@RequestMapping("/isu")
class IsuController(private val service: IsuService) {
    @PostMapping("/group/{groupId}/expel-all")
    fun expelAll(@PathVariable @Positive groupId: Int): ResponseEntity<Void> {
        service.expelAll(groupId)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/group/{groupId}/change-edu-form/{newForm}")
    fun changeEducationForm(
        @PathVariable @Positive groupId: Int,
        @PathVariable newForm: FormOfEducation,
    ): StudyGroupResponse = service.changeEducationForm(groupId, newForm)
}
