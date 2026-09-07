package ru.itmo.soa.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import ru.itmo.soa.domain.StudyGroup

interface StudyGroupRepository :
    JpaRepository<StudyGroup, Int>,
    JpaSpecificationExecutor<StudyGroup>
