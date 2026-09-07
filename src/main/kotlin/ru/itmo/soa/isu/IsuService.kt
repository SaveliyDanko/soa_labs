package ru.itmo.soa.isu

import org.springframework.stereotype.Service
import ru.itmo.soa.api.dto.StudyGroupResponse
import ru.itmo.soa.domain.FormOfEducation

@Service
class IsuService(private val client: StudyGroupsClient) {
    fun expelAll(groupId: Int) = client.delete(groupId)

    fun changeEducationForm(groupId: Int, newForm: FormOfEducation): StudyGroupResponse {
        val current = client.get(groupId)
        return client.update(groupId, current.toRequest(newForm))
    }
}
