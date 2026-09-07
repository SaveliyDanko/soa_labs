package ru.itmo.soa.isu

import ru.itmo.soa.api.dto.StudyGroupRequest
import ru.itmo.soa.api.dto.StudyGroupResponse

interface StudyGroupsClient {
    fun get(id: Int): StudyGroupResponse
    fun update(id: Int, request: StudyGroupRequest): StudyGroupResponse
    fun delete(id: Int)
}
